Attribute VB_Name = "modPlantSync"
Option Compare Database
Option Explicit

' ============================================================================
'  modPlantSync  -  the nursery PC's two-way sync with the Google Apps Script
'  web app. Lives in the FRONT-END (GFRBG.mdb). One SyncPlants run does, in order:
'    1. Sales-in (reverse sync): pull "Pending" Sales rows out of the Sheet,
'       decrement the matching Batches stock in Access (or set a row aside as
'       "NoMatch" when it can't be applied), then mark each row on the Sheet.
'    1b. Culls-in (reverse sync): same handshake for "Pending" Culls rows, with
'       a stricter single-type deduction rule and a StockPlant skip path.
'    2. Plant push ("replacePlants"): full-mirror rewrite of the "Plants" sheet
'       from the in-stock plant view (qryPlantsExport).
'  Sales-in runs FIRST so the same run's push already reflects the freshly
'  decremented stock (a just-sold-out accession drops off the in-stock list now).
'
'  Wiring (see docs/deploy/access.md):
'    * On an always-open form (e.g. the switchboard):
'        - Timer Interval = 600000  (10 minutes)
'        - Form_Timer:        modPlantSync.SyncPlants
'    * "Sync now" button On Click:  modPlantSync.SyncPlants True, True
'
'  Design (mirrors the Android app):
'    * Re-entrancy guard (mPushing) so a slow POST can't overlap the next tick
'      -- the Access analog of the app's cloudMutex serializing cloud ops. Both
'      phases share the one guard so two runs can't deduct the same rows at once.
'    * Timer path swallows all errors (no popups); the manual button can show
'      them (showErrors:=True).
'    * Cheap change-detection (PLANT PUSH ONLY): hash the EXACT payload and skip
'      the POST when it matches the last SUCCESSFUL push (stored via SaveSetting).
'      The hash is updated ONLY on a confirmed ok response, so a failed push
'      retries on the next tick. Sales-in is NOT hash-gated -- it runs every tick.
'
'  Idempotency for sales-in -- the "never double-deduct" mechanism:
'    The decrement against the live Access DB is local and irreversible, so the
'    Sheet's sync_status is a human-visible MIRROR, not the safety net. The local
'    ledger tblAppliedSales(receipt, item_seq) is the authority on "already
'    applied": the decrement and the ledger insert happen in ONE DAO transaction,
'    so a Sheet-flip that fails after a successful deduct simply re-flips next run
'    (the ledger blocks a second decrement). The ledger is auto-created on first
'    run, mirroring how EnsureQueryDef_ auto-creates qryPlantsExport.
' ============================================================================

' ---- Configuration via Windows environment variables ----------------------
'  The web app URL and shared secret are read at RUN TIME from these Windows user environment
'  variables -- they are NOT stored in the database. If either is missing the sync is skipped, so a
'  copy of the database taken home (where the variables aren't set) will never push to Google Sheets.
'  Only the nursery PC has them. Set them once, from a Command Prompt on the nursery PC:
'      setx GFRBG_SYNC_URL    "https://script.google.com/macros/s/XXXX/exec"
'      setx GFRBG_SYNC_SECRET "your-shared-secret"
'  then fully close and reopen Access (Environ reads the environment captured when the process starts).
Private Const ENV_URL    As String = "GFRBG_SYNC_URL"
Private Const ENV_SECRET As String = "GFRBG_SYNC_SECRET"
Private Const QUERY_NAME As String = "qryPlantsExport"

' Local idempotency ledger for the sales-in reverse sync. Auto-created on first run (EnsureLedger_),
' PK (receipt, item_seq). A row here means "this sale has already been deducted from Access stock", so
' it is never deducted again even if the Sheet flip to "Synced" failed last run.
Private Const LEDGER_NAME As String = "tblAppliedSales"

' Local idempotency ledger for the culls-in reverse sync. Auto-created on first run,
' PK (cull_id). A row here means "this cull has already been processed in Access".
Private Const CULL_LEDGER_NAME As String = "tblAppliedCulls"

' The export query is (re)created automatically on first run if missing (see EnsureQueryDef_), so the
' nursery PC needs no manual query setup. This SELECT is the single source of truth for the export
' columns (Batches + Species joined on [Id No], in-stock only).
Private Const PLANTS_SQL As String = "SELECT b.[Ac Number], b.[Id No], b.[Source], b.[Material Type], b.[Date of Sowing/Cutting], b.[Date Potted], b.[LabelsLastPrinted], b.[NoLastPrinted], b.[NoPrinted], b.[PotsInNursery], b.[TubesInNursery], b.[PotsForSale], b.[TubesForSale], b.[MiscInNursery], b.[MiscForSale], b.[StockInNursery], b.[RBGSourced], b.[CBD], s.[Genus], s.[Species], s.[Cultivar], s.[Family], s.[Plant Type], s.[Common Name], s.[Origin], s.[Description], s.[Cultivation Notes], s.[Flowers In], s.[Sun/Shade], s.[Coast Tolerant], s.[Drought Tolerant], s.[Frost Tender], s.[Wet/Dry], s.[Soil Type], s.[Weed Potential], s.[Australian/Exotic], s.[WidthLower], s.[HeightLower], s.[WidthUpper], s.[HeightUpper], s.[Photo], s.[Flower Colour], s.[PotSuitable] FROM Batches AS b LEFT JOIN Species AS s ON b.[Id No] = s.[Id No] WHERE Nz(b.[PotsInNursery],0)+Nz(b.[TubesInNursery],0)+Nz(b.[MiscInNursery],0)+Nz(b.[StockInNursery],0)>0 ORDER BY b.[Ac Number];"

' ---- SaveSetting bucket for the last-pushed payload hash --------------------
Private Const REG_APP     As String = "GFRBG"
Private Const REG_SECTION As String = "PlantSync"
Private Const REG_KEY     As String = "LastHash"

' ---- HTTP timeouts (ms): resolve, connect, send, receive -------------------
Private Const T_RESOLVE As Long = 5000
Private Const T_CONNECT As Long = 5000
Private Const T_SEND    As Long = 10000
Private Const T_RECEIVE As Long = 20000

Private mPushing As Boolean   ' re-entrancy guard

' Public entry point.
'   force:=True       ignores the "unchanged hash" skip (force a push).
'   showErrors:=True  surfaces message boxes (use on the manual button, NOT the timer).
Public Sub SyncPlants(Optional ByVal force As Boolean = False, _
                      Optional ByVal showErrors As Boolean = False)
    ' Sync is enabled only on a machine that has BOTH environment variables set (the nursery PC).
    ' On any other copy (e.g. taken home) they are blank, so we do nothing and never push.
    Dim url As String, secret As String
    url = Environ$(ENV_URL)
    secret = Environ$(ENV_SECRET)
    If Len(url) = 0 Or Len(secret) = 0 Then
        If showErrors Then MsgBox "Sync is turned off on this PC." & vbCrLf & _
            "(" & ENV_URL & " / " & ENV_SECRET & " are not set, so this copy won't update Google Sheets.)", _
            vbInformation, "Plant sync"
        Exit Sub
    End If

    If mPushing Then Exit Sub          ' a previous push is still running
    mPushing = True
    On Error GoTo Done

    ' ---- Phase 1: sales-in (reverse sync), BEFORE the plant push -----------------------------------
    ' Pull "Pending" Sales rows from the Sheet and decrement Batches stock, then mark them "Synced".
    ' Runs every tick (not hash-gated). It is self-healing -- a network/parse hiccup just retries next
    ' run and the ledger blocks any double-deduct -- so we never let a sales-in failure abort the plant
    ' push: swallow its errors here, then restore the push's handler.
    On Error Resume Next
    ApplyPendingSales_ url, secret
    ApplyPendingCulls_ url, secret
    On Error GoTo Done

    ' ---- Phase 2: plant push (full-mirror replacePlants) -------------------------------------------
    Dim headerJson As String, rowsJson As String, payload As String
    Dim hash As String, lastHash As String
    Dim rowCount As Long

    rowCount = BuildPayloadParts(headerJson, rowsJson)   ' reads qryPlantsExport
    payload = "{""action"":""replacePlants""," & _
              """secret"":""" & JsonEscape(secret) & """," & _
              """header"":" & headerJson & "," & _
              """rows"":" & rowsJson & "}"

    hash = HashString(payload)
    lastHash = GetSetting(REG_APP, REG_SECTION, REG_KEY, "")

    If (Not force) And (hash = lastHash) Then
        ' Nothing changed since the last successful sync -> skip the cloud call.
        If showErrors Then MsgBox "Already up to date - nothing to sync.", vbInformation, "Plant sync"
        GoTo Done
    End If

    If PostJson(url, payload) Then
        SaveSetting REG_APP, REG_SECTION, REG_KEY, hash   ' persist on success only
        If showErrors Then MsgBox "Synced " & rowCount & " plants.", vbInformation, "Plant sync"
    Else
        If showErrors Then MsgBox "Sync failed - will retry on the next tick.", vbExclamation, "Plant sync"
    End If

Done:
    If showErrors And Err.Number <> 0 Then
        MsgBox "Sync error: " & Err.Description, vbExclamation, "Plant sync"
    End If
    mPushing = False
End Sub

' Safe DRY RUN: builds the payload from qryPlantsExport and prints diagnostics to the
' Immediate window. Makes NO network call and does NOT touch the Google Sheet. The secret
' is redacted in the printout. Run from the Immediate window:  modPlantSync.SyncSelfTest
Public Sub SyncSelfTest()
    Dim headerJson As String, rowsJson As String, payload As String
    Dim rc As Long
    rc = BuildPayloadParts(headerJson, rowsJson)
    payload = "{""action"":""replacePlants""," & _
              """secret"":""***""," & _
              """header"":" & headerJson & "," & _
              """rows"":" & rowsJson & "}"
    Debug.Print "--- SyncSelfTest ---"
    Debug.Print "config:     " & ENV_URL & "=" & IIf(Len(Environ$(ENV_URL)) > 0, "SET", "MISSING") & _
                ", " & ENV_SECRET & "=" & IIf(Len(Environ$(ENV_SECRET)) > 0, "SET", "MISSING")
    Debug.Print "rows:       " & rc
    Debug.Print "header:     " & headerJson
    Debug.Print "payloadLen: " & Len(payload)
    Debug.Print "hash:       " & HashString(payload)
    Debug.Print "firstRow:   " & Left$(rowsJson, 400)
End Sub

' Immediate-window self-test for the per-row deduction arithmetic (ComputeDeduction_) -- pots, tubes,
' misc, the misc->pots overflow corners, and an unrecognized unit (the NoMatch path, which moves no
' stock) -- mirroring the SyncSelfTest precedent. VBA has no JVM/JS
' harness, so this is where the arithmetic is checked; it is deliberately NOT mirrored as a tested copy
' in core/ or shared.js (a never-executed duplicate would only drift). Makes NO network call and touches
' NO data. Run from the Immediate window:  modPlantSync.DeductSelfTest
Public Sub DeductSelfTest()
    Debug.Print "--- DeductSelfTest ---"
    '            unit     qty    P   T   M  ->  wantP wantT wantM
    PrintDeduct_ "pots",    3,  10,  5,  4,        7,    5,    4   ' pure pots: only P drops
    PrintDeduct_ "pots",    9,   2,  5,  4,        0,    5,    4   ' pots oversell clamps at 0
    PrintDeduct_ "tubes",   3,  10,  5,  4,       10,    2,    4   ' pure tubes: only T drops
    PrintDeduct_ "tubes",   9,  10,  2,  4,       10,    0,    4   ' tubes oversell clamps at 0
    PrintDeduct_ "misc",    3,  10,  5,  4,       10,    5,    1   ' pure misc, no overflow: only M drops
    PrintDeduct_ "misc",    4,  10,  5,  4,       10,    5,    0   ' misc exactly to zero, no overflow
    PrintDeduct_ "misc",    7,  10,  5,  4,        7,    5,    0   ' misc overflow (3) partly drawn from pots
    PrintDeduct_ "misc",    9,   2,  5,  4,        0,    5,    0   ' misc overflow (5) exceeds pots -> pots 0
    PrintDeduct_ "",        5,  10,  5,  4,       10,    5,    4   ' blank unit -> NoMatch: nothing moves
    PrintDeduct_ "unknown", 5,  10,  5,  4,       10,    5,    4   ' unrecognized unit -> NoMatch: nothing moves
    PrintOrderIndependence_     ' two rows of one accession: result must not depend on row order (AC)
End Sub

' Run one case through ComputeDeduction_ and print pass/fail against the expected P/T/M. P/T/M arrive
' ByVal, so ComputeDeduction_ mutates these local copies, not the caller's.
Private Sub PrintDeduct_(ByVal unit As String, ByVal qty As Long, _
                         ByVal p As Long, ByVal t As Long, ByVal m As Long, _
                         ByVal wantP As Long, ByVal wantT As Long, ByVal wantM As Long)
    ComputeDeduction_ unit, qty, p, t, m
    Debug.Print "  " & unit & " qty " & qty & " -> P=" & p & " T=" & t & " M=" & m & _
                IIf(p = wantP And t = wantT And m = wantM, "  OK", _
                    "  FAIL (want P=" & wantP & " T=" & wantT & " M=" & wantM & ")")
End Sub

' Order-independence acceptance check: two rows of the same accession applied one at a time against the
' live counts must reach the same final counts regardless of row order (proved algebraically in the
' design; pinned here on a concrete misc-overflow case so it can't silently regress).
Private Sub PrintOrderIndependence_()
    Dim pa As Long, ta As Long, ma As Long
    Dim pb As Long, tb As Long, mb As Long
    pa = 4: ta = 0: ma = 6                      ' two misc rows, qty 3 then 5 ...
    ComputeDeduction_ "misc", 3, pa, ta, ma
    ComputeDeduction_ "misc", 5, pa, ta, ma
    pb = 4: tb = 0: mb = 6                       ' ... vs qty 5 then 3, same start
    ComputeDeduction_ "misc", 5, pb, tb, mb
    ComputeDeduction_ "misc", 3, pb, tb, mb
    Debug.Print "  order-independence -> (" & pa & "," & ta & "," & ma & ") vs (" & _
                pb & "," & tb & "," & mb & ")" & _
                IIf(pa = pb And ta = tb And ma = mb, "  OK", "  FAIL")
End Sub

' Safe DRY RUN of the sales-in (reverse sync) selection, mirroring SyncSelfTest's dry-run/redacted-secret
' style. POSTs pendingSales and, for each returned row, prints how the NEXT REAL run would route it --
' already-ledgered (re-flip only), would-deduct (one batch match), or NoMatch (unrecognized/blank unit,
' no batch, or an ambiguous >1 match) -- WITHOUT decrementing any stock or flipping any Sheet cell. It is
' the diagnostic that surfaced the [Ac Number] type-mismatch: it walks the exact decision tree
' ApplyPendingSales_ uses, so its routing matches what the real run will do. Side effects are limited to
' the pendingSales read (which stamps the SyncStatus tab, as any read does) and auto-creating the empty
' ledger if missing (harmless; the real run does the same). Run from the Immediate window:
'   modPlantSync.SalesInSelfTest
Public Sub SalesInSelfTest()
    Dim url As String, secret As String, body As String
    Dim sales As Collection, row As Variant, db As DAO.Database
    Dim i As Long, receipt As String, accession As String, unit As String, plantName As String, ledStatus As String
    Dim itemSeq As Long, qty As Long, matches As Long

    url = Environ$(ENV_URL)
    secret = Environ$(ENV_SECRET)
    Debug.Print "--- SalesInSelfTest (dry run: no stock change, no Sheet flip) ---"
    Debug.Print "config:  " & ENV_URL & "=" & IIf(Len(url) > 0, "SET", "MISSING") & _
                ", " & ENV_SECRET & "=" & IIf(Len(secret) > 0, "SET", "MISSING")
    If Len(url) = 0 Or Len(secret) = 0 Then Exit Sub

    If Not PostJsonResponse(url, _
        "{""action"":""pendingSales"",""secret"":""" & JsonEscape(secret) & """}", body) Then
        Debug.Print "pending: POST FAILED (no ok:true response)"
        Exit Sub
    End If

    Set sales = ParsePendingSales_(body)
    Debug.Print "pending: " & sales.Count & " row(s)"
    If sales.Count = 0 Then Exit Sub

    Set db = CurrentDb
    EnsureLedger_ db
    For i = 1 To sales.Count
        row = sales.Item(i)
        receipt = CStr(row(0))
        itemSeq = row(1)
        accession = CStr(row(2))
        qty = row(3)
        unit = LCase$(Trim$(CStr(row(4))))
        plantName = CStr(row(5))

        ledStatus = LedgerStatus_(db, receipt, itemSeq)
        If Len(ledStatus) > 0 Then
            Debug.Print "  " & receipt & "#" & itemSeq & " acc=" & accession & " " & unit & " x" & qty & _
                        " -> already ledgered '" & ledStatus & "'" & _
                        IIf(ledStatus = "Synced" And NeedsPlantEnrichment_(plantName), _
                            " (would re-flip + enrich)", " (would re-flip only)")
        ElseIf Not IsSellUnit_(unit) Then
            Debug.Print "  " & receipt & "#" & itemSeq & " acc=" & accession & " unit=[" & unit & "]" & _
                        " -> NoMatch (unrecognized/blank unit)"
        Else
            matches = CountBatchMatches_(db, accession)
            Select Case matches
                Case 1
                    Debug.Print "  " & receipt & "#" & itemSeq & " acc=" & accession & " " & unit & " x" & qty & _
                                " name=[" & plantName & "]" & _
                                IIf(NeedsPlantEnrichment_(plantName), " -> would deduct + enrich", _
                                    " -> would deduct (status only)")
                Case 0
                    Debug.Print "  " & receipt & "#" & itemSeq & " acc=" & accession & _
                                " -> NoMatch (no batch / non-numeric accession)"
                Case Else
                    Debug.Print "  " & receipt & "#" & itemSeq & " acc=" & accession & _
                                " -> NoMatch (ambiguous: " & matches & " batches)"
            End Select
        End If
    Next i
End Sub

' Immediate-window self-test for cull deduction arithmetic (ComputeCullDeduction_) — single-type clamp,
' no misc->pots overflow. Run: modPlantSync.CullDeductSelfTest
Public Sub CullDeductSelfTest()
    Debug.Print "--- CullDeductSelfTest ---"
    PrintCullDeduct_ "pots", 1, 0, 4, 0, 0, 4, 0   ' issue example: don't touch tubes
    PrintCullDeduct_ "tubes", 3, 10, 5, 4, 10, 2, 4
    PrintCullDeduct_ "misc", 7, 10, 5, 4, 10, 5, 0  ' no overflow to pots
    PrintCullDeduct_ "pots", 9, 2, 5, 4, 0, 5, 4
End Sub

Private Sub PrintCullDeduct_(ByVal unit As String, ByVal qty As Long, _
                             ByVal p As Long, ByVal t As Long, ByVal m As Long, _
                             ByVal wantP As Long, ByVal wantT As Long, ByVal wantM As Long)
    ComputeCullDeduction_ unit, qty, p, t, m
    Debug.Print "  " & unit & " qty " & qty & " -> P=" & p & " T=" & t & " M=" & m & _
                IIf(p = wantP And t = wantT And m = wantM, "  OK", _
                    "  FAIL (want P=" & wantP & " T=" & wantT & " M=" & wantM & ")")
End Sub

' Safe DRY RUN of the culls-in selection. POSTs pendingCulls and prints routing without changing stock
' or flipping Sheet cells. Run: modPlantSync.CullsInSelfTest
Public Sub CullsInSelfTest()
    Dim url As String, secret As String, body As String
    Dim culls As Collection, row As Variant, db As DAO.Database
    Dim i As Long, cullId As String, accession As String, unit As String, notes As String
    Dim plantName As String, ledStatus As String
    Dim qty As Long, matches As Long

    url = Environ$(ENV_URL)
    secret = Environ$(ENV_SECRET)
    Debug.Print "--- CullsInSelfTest (dry run: no stock change, no Sheet flip) ---"
    Debug.Print "config:  " & ENV_URL & "=" & IIf(Len(url) > 0, "SET", "MISSING") & _
                ", " & ENV_SECRET & "=" & IIf(Len(secret) > 0, "SET", "MISSING")
    If Len(url) = 0 Or Len(secret) = 0 Then Exit Sub

    If Not PostJsonResponse(url, _
        "{""action"":""pendingCulls"",""secret"":""" & JsonEscape(secret) & """}", body) Then
        Debug.Print "pending: POST FAILED (no ok:true response)"
        Exit Sub
    End If

    Set culls = ParsePendingCulls_(body)
    Debug.Print "pending: " & culls.Count & " row(s)"
    If culls.Count = 0 Then Exit Sub

    Set db = CurrentDb
    EnsureCullLedger_ db
    For i = 1 To culls.Count
        row = culls.Item(i)
        cullId = CStr(row(0))
        accession = CStr(row(1))
        qty = row(2)
        unit = LCase$(Trim$(CStr(row(3))))
        notes = CStr(row(4))
        plantName = CStr(row(5))

        ledStatus = CullLedgerStatus_(db, cullId)
        If Len(ledStatus) > 0 Then
            Debug.Print "  " & cullId & " acc=" & accession & " -> already ledgered '" & ledStatus & "'" & _
                        IIf(ledStatus = "Synced" And NeedsPlantEnrichment_(plantName), _
                            " (would re-flip + enrich)", " (would re-flip only)")
        ElseIf IsStockPlantCull_(notes, unit) Then
            Debug.Print "  " & cullId & " acc=" & accession & " notes=[" & notes & "] -> StockPlant (skip)"
        ElseIf Not IsSellUnit_(unit) Then
            Debug.Print "  " & cullId & " acc=" & accession & " unit=[" & unit & "] -> NoMatch"
        Else
            matches = CountBatchMatches_(db, accession)
            Select Case matches
                Case 1
                    Debug.Print "  " & cullId & " acc=" & accession & " " & unit & " x" & qty & _
                                " name=[" & plantName & "]" & _
                                IIf(NeedsPlantEnrichment_(plantName), " -> would deduct + enrich", _
                                    " -> would deduct (status only)")
                Case 0
                    Debug.Print "  " & cullId & " acc=" & accession & " -> NoMatch (no batch)"
                Case Else
                    Debug.Print "  " & cullId & " acc=" & accession & " -> NoMatch (ambiguous: " & matches & ")"
            End Select
        End If
    Next i
End Sub

' ============================================================================
'  Sales-in (reverse sync): Pending Sales rows -> Access stock decrement
' ============================================================================

' Pull every "Pending" Sales row from the Sheet and route each one to a terminal state, recording the
' outcome in the local ledger so it is never reconsidered:
'   * a pots/tubes/misc sale matching EXACTLY ONE Batches row -> decrement the matching stock count(s)
'     and ledger "Synced", the two in ONE DAO transaction so a crash can't leave one without the other;
'   * a row that can't be acted on -- no batch match, an ambiguous (>1) match, or an unrecognized/blank
'     unit -> ledger "NoMatch", moving NO stock (never guessed; surfaced for human reconciliation).
' Already-ledgered rows are re-flipped on the Sheet only (never re-applied). Finally mark every pulled
' row on the Sheet with the status the ledger now holds ("Synced" or "NoMatch").
'
' StockInNursery and the *ForSale flags are never touched by any path -- only PotsInNursery/
' TubesInNursery/MiscInNursery move, and only on the deduct path.
'
' Resilience: a failed POST or parse just Exits; nothing is half-applied and the next run retries. A row
' whose ledger write fails is simply left Pending and retried next run (one bad row never aborts the
' batch). The caller (SyncPlants) swallows any error this raises so sales-in can never block the push.
Private Sub ApplyPendingSales_(ByVal url As String, ByVal secret As String)
    Dim db As DAO.Database
    Dim body As String, payload As String
    Dim sales As Collection, marks As Collection, row As Variant
    Dim i As Long
    Dim receipt As String, accession As String, unit As String, plantName As String, ledStatus As String
    Dim itemSeq As Long, qty As Long

    Set db = CurrentDb
    EnsureLedger_ db                       ' auto-create tblAppliedSales on first run

    payload = "{""action"":""pendingSales"",""secret"":""" & JsonEscape(secret) & """}"
    If Not PostJsonResponse(url, payload, body) Then Exit Sub   ' transient failure -> retry next run
    Set sales = ParsePendingSales_(body)
    If sales.Count = 0 Then Exit Sub

    Set marks = New Collection
    For i = 1 To sales.Count
        row = sales.Item(i)
        receipt = CStr(row(0))
        itemSeq = row(1)
        accession = CStr(row(2))
        qty = row(3)
        unit = LCase$(Trim$(CStr(row(4))))
        plantName = CStr(row(5))

        ledStatus = LedgerStatus_(db, receipt, itemSeq)
        If Len(ledStatus) > 0 Then
            ' Already applied locally (Synced or NoMatch). Re-flip the Sheet only (a prior
            ' markSalesSynced may have failed); the ledger is the authority, so we NEVER re-apply.
            ' If the prior mark failed after a Synced deduct, also retry enrichment when the Sheet
            ' row still needs it — otherwise sync_status recovers but name stays "unknown" forever.
            If ledStatus = "Synced" And NeedsPlantEnrichment_(plantName) Then
                marks.Add MarkJson_(receipt, itemSeq, ledStatus, LookupSpeciesFields_(db, accession))
            Else
                marks.Add MarkJson_(receipt, itemSeq, ledStatus)
            End If
        ElseIf Not IsSellUnit_(unit) Then
            ' Unrecognized or blank unit -> NoMatch, no stock moved (never guessed).
            If ApplyNoMatch_(db, receipt, itemSeq) Then
                marks.Add MarkJson_(receipt, itemSeq, "NoMatch")
            End If
        ElseIf CountBatchMatches_(db, accession) = 1 Then
            ' A recognized unit resolving to EXACTLY ONE batch is deducted and ledgered "Synced".
            ' Species lookup only when the Sheet row still needs enrichment. Skip when name is
            ' already resolved (saves a Batches⋈Species query and keeps the mark payload small).
            ' Blank/missing name (older pendingSales without the field) still looks up — safe under
            ' staggered deploy; Apps Script refuses to overwrite non-unknown rows anyway.
            If ApplyDeduction_(db, receipt, itemSeq, accession, unit, qty) Then
                If NeedsPlantEnrichment_(plantName) Then
                    marks.Add MarkJson_(receipt, itemSeq, "Synced", LookupSpeciesFields_(db, accession))
                Else
                    marks.Add MarkJson_(receipt, itemSeq, "Synced")
                End If
            End If
            ' transaction failed -> not ledgered, left Pending, retried next run
        Else
            ' No batch match (unknown/typo/deleted batch) or an ambiguous (>1) match -> NoMatch, no
            ' stock moved; ambiguity is surfaced for a human, not silently resolved.
            If ApplyNoMatch_(db, receipt, itemSeq) Then
                marks.Add MarkJson_(receipt, itemSeq, "NoMatch")
            End If
        End If
    Next i

    If marks.Count > 0 Then
        Dim arr() As String
        ReDim arr(0 To marks.Count - 1)
        For i = 1 To marks.Count
            arr(i - 1) = marks.Item(i)
        Next i
        payload = "{""action"":""markSalesSynced""," & _
                  """secret"":""" & JsonEscape(secret) & """," & _
                  """keys"":[" & Join(arr, ",") & "]}"
        ' A failed flip is tolerated: the ledger already blocks re-deduction, so next run just re-flips.
        PostJson url, payload
    End If
End Sub

' ============================================================================
'  Culls-in (reverse sync): Pending Culls rows -> Access stock decrement
' ============================================================================

' Pull every "Pending" Culls row from the Sheet and route each one to a terminal state.
' Same resilience/idempotency pattern as ApplyPendingSales_, but:
'   * ledger keyed on cull_id (not receipt/item_seq)
'   * ComputeCullDeduction_ (single-type clamp, NO misc->pots overflow)
'   * Notes = "Stock plant" -> ledger "StockPlant", no stock moved (#28)
Private Sub ApplyPendingCulls_(ByVal url As String, ByVal secret As String)
    Dim db As DAO.Database
    Dim body As String, payload As String
    Dim culls As Collection, marks As Collection, row As Variant
    Dim i As Long
    Dim cullId As String, accession As String, unit As String, notes As String
    Dim plantName As String, ledStatus As String
    Dim qty As Long

    Set db = CurrentDb
    EnsureCullLedger_ db

    payload = "{""action"":""pendingCulls"",""secret"":""" & JsonEscape(secret) & """}"
    If Not PostJsonResponse(url, payload, body) Then Exit Sub
    Set culls = ParsePendingCulls_(body)
    If culls.Count = 0 Then Exit Sub

    Set marks = New Collection
    For i = 1 To culls.Count
        row = culls.Item(i)
        cullId = CStr(row(0))
        accession = CStr(row(1))
        qty = row(2)
        unit = LCase$(Trim$(CStr(row(3))))
        notes = CStr(row(4))
        plantName = CStr(row(5))

        ledStatus = CullLedgerStatus_(db, cullId)
        If Len(ledStatus) > 0 Then
            ' Same re-flip enrichment as sales-in: recover plant fields if a prior markCullsSynced
            ' failed after a Synced deduct left the Sheet name still unknown/blank.
            If ledStatus = "Synced" And NeedsPlantEnrichment_(plantName) Then
                marks.Add CullMarkJson_(cullId, ledStatus, LookupSpeciesFields_(db, accession))
            Else
                marks.Add CullMarkJson_(cullId, ledStatus)
            End If
        ElseIf IsStockPlantCull_(notes, unit) Then
            If ApplyCullStockPlant_(db, cullId) Then
                marks.Add CullMarkJson_(cullId, "StockPlant")
            End If
        ElseIf Not IsSellUnit_(unit) Then
            If ApplyCullNoMatch_(db, cullId) Then
                marks.Add CullMarkJson_(cullId, "NoMatch")
            End If
        ElseIf CountBatchMatches_(db, accession) = 1 Then
            ' Species lookup only when the Sheet row still needs enrichment (same gate as sales-in).
            If ApplyCullDeduction_(db, cullId, accession, unit, qty) Then
                If NeedsPlantEnrichment_(plantName) Then
                    marks.Add CullMarkJson_(cullId, "Synced", LookupSpeciesFields_(db, accession))
                Else
                    marks.Add CullMarkJson_(cullId, "Synced")
                End If
            End If
        Else
            If ApplyCullNoMatch_(db, cullId) Then
                marks.Add CullMarkJson_(cullId, "NoMatch")
            End If
        End If
    Next i

    If marks.Count > 0 Then
        Dim arr() As String
        ReDim arr(0 To marks.Count - 1)
        For i = 1 To marks.Count
            arr(i - 1) = marks.Item(i)
        Next i
        payload = "{""action"":""markCullsSynced""," & _
                  """secret"":""" & JsonEscape(secret) & """," & _
                  """keys"":[" & Join(arr, ",") & "]}"
        PostJson url, payload
    End If
End Sub

' Decrement ONLY the cull unit's stock count for the single matching batch AND insert the cull ledger
' row, in ONE DAO transaction. Uses ComputeCullDeduction_ (no misc->pots overflow). StockInNursery and
' the *ForSale flags are never touched.
Private Function ApplyCullDeduction_(ByRef db As DAO.Database, _
                                     ByVal cullId As String, ByVal accession As String, _
                                     ByVal unit As String, ByVal qty As Long) As Boolean
    Dim ws As DAO.Workspace
    Dim rs As DAO.Recordset
    Dim inTrans As Boolean
    Dim p As Long, t As Long, m As Long

    On Error GoTo Fail
    Set ws = DBEngine.Workspaces(0)
    ws.BeginTrans
    inTrans = True

    Set rs = OpenBatchByAccession_(db, accession, True)
    If rs.EOF Then Err.Raise vbObjectError, , "batch vanished"

    p = Nz(rs![PotsInNursery], 0)
    t = Nz(rs![TubesInNursery], 0)
    m = Nz(rs![MiscInNursery], 0)
    ComputeCullDeduction_ unit, qty, p, t, m

    rs.Edit
    rs![PotsInNursery] = p
    rs![TubesInNursery] = t
    rs![MiscInNursery] = m
    rs.Update
    rs.Close
    Set rs = Nothing

    CullLedgerInsert_ db, cullId, "Synced"

    ws.CommitTrans
    inTrans = False
    ApplyCullDeduction_ = True
    Exit Function
Fail:
    If Not rs Is Nothing Then
        On Error Resume Next
        rs.Close
        On Error GoTo 0
    End If
    If inTrans Then ws.Rollback
    ApplyCullDeduction_ = False
End Function

' Record an unactionable cull row in the ledger as "NoMatch" — no stock moved.
Private Function ApplyCullNoMatch_(ByRef db As DAO.Database, ByVal cullId As String) As Boolean
    On Error GoTo Fail
    CullLedgerInsert_ db, cullId, "NoMatch"
    ApplyCullNoMatch_ = True
    Exit Function
Fail:
    ApplyCullNoMatch_ = False
End Function

' Record a stock-plant cull (Notes = "Stock plant") — no stock moved, StockInNursery untouched.
Private Function ApplyCullStockPlant_(ByRef db As DAO.Database, ByVal cullId As String) As Boolean
    On Error GoTo Fail
    CullLedgerInsert_ db, cullId, "StockPlant"
    ApplyCullStockPlant_ = True
    Exit Function
Fail:
    ApplyCullStockPlant_ = False
End Function

' Pure cull deduction (#28): subtract qty ONLY from the named container, clamped at zero.
' Unlike ComputeDeduction_, misc oversell does NOT draw from pots.
Private Sub ComputeCullDeduction_(ByVal unit As String, ByVal qty As Long, _
                                  ByRef p As Long, ByRef t As Long, ByRef m As Long)
    Select Case unit
        Case "pots", "pot"
            p = ClampSub_(p, qty)
        Case "tubes", "tube"
            t = ClampSub_(t, qty)
        Case "misc"
            m = ClampSub_(m, qty)
    End Select
End Sub

' True when notes + unit identify a stock-plant cull per #28 (Pot + Notes = "Stock plant").
Private Function IsStockPlantCull_(ByVal notes As String, ByVal unit As String) As Boolean
    IsStockPlantCull_ = (LCase$(Trim$(notes)) = "stock plant") And _
                          (LCase$(Trim$(unit)) = "pots" Or LCase$(Trim$(unit)) = "pot")
End Function

Private Sub EnsureCullLedger_(ByRef db As DAO.Database)
    Dim tdf As DAO.TableDef
    On Error Resume Next
    Set tdf = db.TableDefs(CULL_LEDGER_NAME)
    On Error GoTo 0
    If Not (tdf Is Nothing) Then Exit Sub
    db.Execute _
        "CREATE TABLE " & CULL_LEDGER_NAME & " (" & _
        "[cull_id] TEXT(255) NOT NULL, " & _
        "[status] TEXT(50), " & _
        "[applied_at] DATETIME, " & _
        "CONSTRAINT PrimaryKey PRIMARY KEY ([cull_id]));", dbFailOnError
End Sub

Private Function CullLedgerStatus_(ByRef db As DAO.Database, ByVal cullId As String) As String
    Dim qd As DAO.QueryDef, rs As DAO.Recordset
    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pId Text ( 255 ); " & _
        "SELECT [status] FROM " & CULL_LEDGER_NAME & " WHERE [cull_id]=[pId];")
    qd.Parameters("pId").Value = cullId
    Set rs = qd.OpenRecordset(dbOpenSnapshot)
    If rs.EOF Then
        CullLedgerStatus_ = ""
    Else
        CullLedgerStatus_ = Nz(rs!Status, "Synced")
    End If
    rs.Close
    Set rs = Nothing
End Function

Private Sub CullLedgerInsert_(ByRef db As DAO.Database, ByVal cullId As String, ByVal status As String)
    Dim qd As DAO.QueryDef
    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pId Text ( 255 ), pStatus Text ( 50 ), pAt DateTime; " & _
        "INSERT INTO " & CULL_LEDGER_NAME & " ([cull_id], [status], [applied_at]) " & _
        "VALUES ([pId], [pStatus], [pAt]);")
    qd.Parameters("pId").Value = cullId
    qd.Parameters("pStatus").Value = status
    qd.Parameters("pAt").Value = Now()
    qd.Execute dbFailOnError
End Sub

Private Function CullMarkJson_(ByVal cullId As String, ByVal status As String, _
                               Optional ByVal enrich As Variant) As String
    CullMarkJson_ = "{""cull_id"":""" & JsonEscape(cullId) & """," & _
                    """status"":""" & JsonEscape(status) & """" & _
                    PlantEnrichJsonSuffix_(enrich) & "}"
End Function

Private Function ParsePendingCulls_(ByVal body As String) As Collection
    Dim result As New Collection
    Dim p As Long, q As Long, inner As String
    Dim re As Object, matches As Object, obj As String, i As Long

    p = InStr(1, body, """culls"":[")
    If p = 0 Then GoTo Done
    p = p + Len("""culls"":[")
    q = InStr(p, body, "]")
    If q = 0 Then GoTo Done
    inner = Mid$(body, p, q - p)

    Set re = CreateObject("VBScript.RegExp")
    re.Global = True
    re.Pattern = "\{[^{}]*\}"
    Set matches = re.Execute(inner)
    For i = 0 To matches.Count - 1
        obj = matches.Item(i).Value
        result.Add Array( _
            JsonStr_(obj, "cull_id"), _
            JsonStr_(obj, "accession"), _
            CLng(Val(JsonNum_(obj, "qty"))), _
            JsonStr_(obj, "unit"), _
            JsonStr_(obj, "notes"), _
            JsonStr_(obj, "name"))
    Next i
Done:
    Set ParsePendingCulls_ = result
End Function

' Decrement the sale unit's stock count(s) for the single matching batch AND insert the ledger row, in
' ONE DAO transaction on Workspaces(0) (which covers the linked Jet back-end). Returns True only if both
' committed; on any error it rolls back and returns False, leaving the row un-applied for the next run.
' Caller has already verified the unit is a sell unit and that exactly one batch matches. The arithmetic
' (including the misc->pots overflow) lives in ComputeDeduction_; only PotsInNursery/TubesInNursery/
' MiscInNursery are written -- StockInNursery and the *ForSale flags are never touched.
Private Function ApplyDeduction_(ByRef db As DAO.Database, _
                                 ByVal receipt As String, ByVal itemSeq As Long, _
                                 ByVal accession As String, ByVal unit As String, _
                                 ByVal qty As Long) As Boolean
    Dim ws As DAO.Workspace
    Dim rs As DAO.Recordset
    Dim inTrans As Boolean
    Dim p As Long, t As Long, m As Long

    On Error GoTo Fail
    Set ws = DBEngine.Workspaces(0)
    ws.BeginTrans
    inTrans = True

    Set rs = OpenBatchByAccession_(db, accession, True)    ' updatable dynaset
    If rs.EOF Then Err.Raise vbObjectError, , "batch vanished"   ' guard: caller checked count=1

    p = Nz(rs![PotsInNursery], 0)
    t = Nz(rs![TubesInNursery], 0)
    m = Nz(rs![MiscInNursery], 0)
    ComputeDeduction_ unit, qty, p, t, m       ' applies the unit's clamp(s) + the misc->pots overflow

    rs.Edit
    rs![PotsInNursery] = p
    rs![TubesInNursery] = t
    rs![MiscInNursery] = m
    rs.Update
    rs.Close
    Set rs = Nothing

    LedgerInsert_ db, receipt, itemSeq, "Synced"

    ws.CommitTrans
    inTrans = False
    ApplyDeduction_ = True
    Exit Function
Fail:
    If Not rs Is Nothing Then
        On Error Resume Next
        rs.Close
        On Error GoTo 0
    End If
    If inTrans Then ws.Rollback
    ApplyDeduction_ = False
End Function

' Record an unactionable row -- no batch match, an ambiguous (>1) match, or an unrecognized/blank unit --
' in the ledger as "NoMatch" so it is never reconsidered, moving NO stock. A single INSERT is atomic, so
' (unlike ApplyDeduction_) it needs no surrounding transaction; nothing here writes Batches at all, so
' StockInNursery and the *ForSale flags are untouched by construction. Returns True only if the ledger
' insert committed; on any error it returns False, leaving the row Pending for the next run so one bad
' row never aborts the batch.
Private Function ApplyNoMatch_(ByRef db As DAO.Database, _
                               ByVal receipt As String, ByVal itemSeq As Long) As Boolean
    On Error GoTo Fail
    LedgerInsert_ db, receipt, itemSeq, "NoMatch"
    ApplyNoMatch_ = True
    Exit Function
Fail:
    ApplyNoMatch_ = False
End Function

' The pure per-row deduction arithmetic from the design (no DB, no I/O), mutating P/T/M byref so the SAME
' code backs both the live recordset update (ApplyDeduction_) and DeductSelfTest. For current counts
' P, T, M and a sale of `qty` `unit`s:
'     pots : P := max(0, P - qty)
'     tubes: T := max(0, T - qty)
'     misc : short := max(0, qty - M);  M := max(0, M - qty);  P := max(0, P - short)
' Misc->pots overflow ONLY: tubes never overflow, pots never overflow, and a misc shortfall that pots
' can't absorb is silently dropped (never negative, no cascade to tubes). An unknown unit moves nothing.
' Row-by-row against live counts equals aggregating per accession (max(0, x-a-b) = max(0, max(0, x-a)-b)
' for non-negative a, b), so applying a day's rows one at a time is order-independent.
Private Sub ComputeDeduction_(ByVal unit As String, ByVal qty As Long, _
                              ByRef p As Long, ByRef t As Long, ByRef m As Long)
    Dim shortfall As Long
    Select Case unit
        Case "pots"
            p = ClampSub_(p, qty)
        Case "tubes"
            t = ClampSub_(t, qty)
        Case "misc"
            shortfall = ClampSub_(qty, m)      ' max(0, qty - M): misc sold beyond MiscInNursery
            m = ClampSub_(m, qty)              ' max(0, M - qty)
            p = ClampSub_(p, shortfall)        ' max(0, P - short): overflow draws down pots, clamped at 0
    End Select
End Sub

' True for the three sale-unit labels the app writes. Anything else (unknown/blank) is routed to NoMatch
' by ApplyPendingSales_ -- never guessed -- so ambiguous data can't silently move stock.
Private Function IsSellUnit_(ByVal unit As String) As Boolean
    IsSellUnit_ = (unit = "pots" Or unit = "tubes" Or unit = "misc")
End Function

' max(0, cur - qty): decrement clamped at zero. A negative qty (shouldn't happen) is treated as 0.
Private Function ClampSub_(ByVal cur As Long, ByVal qty As Long) As Long
    If qty < 0 Then qty = 0
    If cur - qty < 0 Then
        ClampSub_ = 0
    Else
        ClampSub_ = cur - qty
    End If
End Function

' Count Batches rows whose [Ac Number] equals `accession` (>1 is treated as ambiguous upstream).
' [Ac Number] is a NUMBER field, so a non-numeric accession (e.g. a "sell as unknown" line, or a typo)
' can't equal any row: report 0 here so the row is routed to NoMatch -- never querying with a value that
' would raise a type mismatch. A numeric accession is compared numerically against the indexed column
' (see OpenBatchByAccession_); the compare is exact, so no trim/case folding is needed.
Private Function CountBatchMatches_(ByRef db As DAO.Database, ByVal accession As String) As Long
    Dim rs As DAO.Recordset
    If Not IsNumeric(accession) Then
        CountBatchMatches_ = 0
        Exit Function
    End If
    Set rs = OpenBatchByAccession_(db, accession, False)   ' snapshot
    If rs.EOF Then
        CountBatchMatches_ = 0
    Else
        rs.MoveLast
        CountBatchMatches_ = rs.RecordCount
    End If
    rs.Close
    Set rs = Nothing
End Function

' Open the Batches rows matching `accession` by an exact compare on [Ac Number]. That column is a NUMBER
' field, so the accession is bound as a Double parameter and compared numerically -- binding it as Text
' (as an earlier version did) raises "Data type mismatch in criteria expression" (error 3464) against a
' numeric column. The numeric equality can still use the column's index. Callers pass only numeric
' accessions (CountBatchMatches_ routes a non-numeric one to NoMatch first), so CDbl is safe here. A
' parameterised query avoids any quoting/escaping; SELECT Batches.* from a single table keeps the
' dynaset updatable.
Private Function OpenBatchByAccession_(ByRef db As DAO.Database, ByVal accession As String, _
                                       ByVal updatable As Boolean) As DAO.Recordset
    Dim qd As DAO.QueryDef
    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pAcc Double; " & _
        "SELECT Batches.* FROM Batches " & _
        "WHERE Batches.[Ac Number]=[pAcc];")
    qd.Parameters("pAcc").Value = CDbl(accession)
    If updatable Then
        Set OpenBatchByAccession_ = qd.OpenRecordset(dbOpenDynaset)
    Else
        Set OpenBatchByAccession_ = qd.OpenRecordset(dbOpenSnapshot)
    End If
End Function

' Auto-create the ledger table on first run if missing -- mirrors EnsureQueryDef_, so the nursery PC
' needs no manual table setup. PK (receipt, item_seq) makes a double-insert of the same sale impossible.
Private Sub EnsureLedger_(ByRef db As DAO.Database)
    Dim tdf As DAO.TableDef
    On Error Resume Next
    Set tdf = db.TableDefs(LEDGER_NAME)
    On Error GoTo 0
    If Not (tdf Is Nothing) Then Exit Sub
    db.Execute _
        "CREATE TABLE " & LEDGER_NAME & " (" & _
        "[receipt] TEXT(255) NOT NULL, " & _
        "[item_seq] LONG NOT NULL, " & _
        "[status] TEXT(50), " & _
        "[applied_at] DATETIME, " & _
        "CONSTRAINT PrimaryKey PRIMARY KEY ([receipt], [item_seq]));", dbFailOnError
End Sub

' The ledger status recorded for (receipt, item_seq), or "" if the row is not in the ledger (i.e. not
' yet applied). Used to skip re-deduction and to re-flip the Sheet to the status already applied.
Private Function LedgerStatus_(ByRef db As DAO.Database, ByVal receipt As String, _
                               ByVal itemSeq As Long) As String
    Dim qd As DAO.QueryDef, rs As DAO.Recordset
    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pR Text ( 255 ), pS Long; " & _
        "SELECT [status] FROM " & LEDGER_NAME & " WHERE [receipt]=[pR] AND [item_seq]=[pS];")
    qd.Parameters("pR").Value = receipt
    qd.Parameters("pS").Value = itemSeq
    Set rs = qd.OpenRecordset(dbOpenSnapshot)
    If rs.EOF Then
        LedgerStatus_ = ""
    Else
        LedgerStatus_ = Nz(rs!Status, "Synced")
    End If
    rs.Close
    Set rs = Nothing
End Function

' Insert one ledger row. Called only inside ApplyDeduction_'s transaction, so it must raise (not
' swallow) on error -- dbFailOnError -- letting the caller roll the whole row back together.
Private Sub LedgerInsert_(ByRef db As DAO.Database, ByVal receipt As String, _
                          ByVal itemSeq As Long, ByVal status As String)
    Dim qd As DAO.QueryDef
    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pR Text ( 255 ), pS Long, pStatus Text ( 50 ), pAt DateTime; " & _
        "INSERT INTO " & LEDGER_NAME & " ([receipt], [item_seq], [status], [applied_at]) " & _
        "VALUES ([pR], [pS], [pStatus], [pAt]);")
    qd.Parameters("pR").Value = receipt
    qd.Parameters("pS").Value = itemSeq
    qd.Parameters("pStatus").Value = status
    qd.Parameters("pAt").Value = Now()
    qd.Execute dbFailOnError
End Sub

' Build one {"receipt":..,"item_seq":..,"status":..} object for the markSalesSynced keys array.
' Optional enrich is a Variant array from LookupSpeciesFields_: (name, genus, species, cultivar,
' common_name, group). Omitted/Empty -> status-only payload (backward compatible).
Private Function MarkJson_(ByVal receipt As String, ByVal itemSeq As Long, _
                           ByVal status As String, Optional ByVal enrich As Variant) As String
    MarkJson_ = "{""receipt"":""" & JsonEscape(receipt) & """," & _
                """item_seq"":" & itemSeq & "," & _
                """status"":""" & JsonEscape(status) & """" & _
                PlantEnrichJsonSuffix_(enrich) & "}"
End Function

' Compose a display name from Species columns — same rules as parsePlants/composePlantName in shared.js.
Private Function ComposePlantName_(ByVal genus As String, ByVal species As String, _
                                   ByVal cultivar As String, ByVal commonName As String) As String
    Dim base As String, cv As String
    base = Trim$(genus)
    If Len(Trim$(species)) > 0 Then
        If Len(base) > 0 Then base = base & " "
        base = base & Trim$(species)
    End If
    cv = Trim$(cultivar)
    If Len(cv) > 0 Then base = Trim$(base & " '" & cv & "'")
    If Len(base) = 0 Then base = Trim$(commonName)
    ComposePlantName_ = base
End Function

' True when Access should look up Species for this Sheet row: name is the unknown sentinel, or
' blank/missing (older pending* payloads without "name" — still enrich so staggered deploy is safe).
Private Function NeedsPlantEnrichment_(ByVal plantName As String) As Boolean
    Dim n As String
    n = LCase$(Trim$(plantName))
    NeedsPlantEnrichment_ = (Len(n) = 0 Or n = "unknown")
End Function

' Look up plant metadata from Batches joined to Species for a unique numeric accession.
' Returns Empty when absent; otherwise Array(name, genus, species, cultivar, common_name, group).
Private Function LookupSpeciesFields_(ByRef db As DAO.Database, ByVal accession As String) As Variant
    Dim qd As DAO.QueryDef, rs As DAO.Recordset
    Dim genus As String, species As String, cultivar As String
    Dim commonName As String, plantType As String

    If Not IsNumeric(accession) Then
        LookupSpeciesFields_ = Empty
        Exit Function
    End If

    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pAcc Double; " & _
        "SELECT s.[Genus], s.[Species], s.[Cultivar], s.[Common Name], s.[Plant Type] " & _
        "FROM Batches AS b INNER JOIN Species AS s ON b.[Id No] = s.[Id No] " & _
        "WHERE b.[Ac Number]=[pAcc];")
    qd.Parameters("pAcc").Value = CDbl(accession)
    Set rs = qd.OpenRecordset(dbOpenSnapshot)
    If rs.EOF Then
        LookupSpeciesFields_ = Empty
    Else
        genus = Nz(rs![Genus], "")
        species = Nz(rs![Species], "")
        cultivar = Nz(rs![Cultivar], "")
        commonName = Nz(rs![Common Name], "")
        plantType = Nz(rs![Plant Type], "")
        LookupSpeciesFields_ = Array( _
            ComposePlantName_(genus, species, cultivar, commonName), _
            genus, species, cultivar, commonName, plantType)
    End If
    rs.Close
    Set rs = Nothing
End Function

' Append optional plant-field JSON properties from LookupSpeciesFields_ output.
Private Function PlantEnrichJsonSuffix_(Optional ByVal enrich As Variant) As String
    Dim s As String
    If IsMissing(enrich) Or IsEmpty(enrich) Then
        PlantEnrichJsonSuffix_ = ""
        Exit Function
    End If
    s = ""
    If Len(CStr(enrich(0))) > 0 Then s = s & ",""name"":""" & JsonEscape(CStr(enrich(0))) & """"
    If Len(CStr(enrich(1))) > 0 Then s = s & ",""genus"":""" & JsonEscape(CStr(enrich(1))) & """"
    If Len(CStr(enrich(2))) > 0 Then s = s & ",""species"":""" & JsonEscape(CStr(enrich(2))) & """"
    If Len(CStr(enrich(3))) > 0 Then s = s & ",""cultivar"":""" & JsonEscape(CStr(enrich(3))) & """"
    If Len(CStr(enrich(4))) > 0 Then s = s & ",""common_name"":""" & JsonEscape(CStr(enrich(4))) & """"
    If Len(CStr(enrich(5))) > 0 Then s = s & ",""group"":""" & JsonEscape(CStr(enrich(5))) & """"
    PlantEnrichJsonSuffix_ = s
End Function

' Parse the pendingSales response into a Collection of Variant arrays Array(receipt, item_seq,
' accession, qty, unit, name). We control the response shape (Code.gs handlePendingSales_ -> selectPendingSales
' in shared.js), so each element is a FLAT JSON object {"receipt":..,..,"name":..} with no nested braces,
' and the identifiers never contain '{' '}' or ']'. So: slice out the "sales":[ ... ] array text, then
' match each {...} object and pull its fields by name (order-independent). Anything else -> empty.
' Missing "name" (older Apps Script) yields "" — NeedsPlantEnrichment_ still looks up in that case
' so Access can be deployed before Apps Script without losing unknown-row enrichment.
Private Function ParsePendingSales_(ByVal body As String) As Collection
    Dim result As New Collection
    Dim p As Long, q As Long, inner As String
    Dim re As Object, matches As Object, obj As String, i As Long

    p = InStr(1, body, """sales"":[")
    If p = 0 Then GoTo Done
    p = p + Len("""sales"":[")
    q = InStr(p, body, "]")
    If q = 0 Then GoTo Done
    inner = Mid$(body, p, q - p)

    Set re = CreateObject("VBScript.RegExp")
    re.Global = True
    re.Pattern = "\{[^{}]*\}"
    Set matches = re.Execute(inner)
    For i = 0 To matches.Count - 1
        obj = matches.Item(i).Value
        result.Add Array( _
            JsonStr_(obj, "receipt"), _
            CLng(Val(JsonNum_(obj, "item_seq"))), _
            JsonStr_(obj, "accession"), _
            CLng(Val(JsonNum_(obj, "qty"))), _
            JsonStr_(obj, "unit"), _
            JsonStr_(obj, "name"))
    Next i
Done:
    Set ParsePendingSales_ = result
End Function

' Extract a JSON string field "key":"value" from one flat object, unescaping the value. "" if absent.
Private Function JsonStr_(ByVal obj As String, ByVal key As String) As String
    Dim re As Object, m As Object, q As String
    q = Chr$(34)
    Set re = CreateObject("VBScript.RegExp")
    re.Pattern = q & key & q & "\s*:\s*" & q & "((?:[^" & q & "\\]|\\.)*)" & q
    Set m = re.Execute(obj)
    If m.Count = 0 Then
        JsonStr_ = ""
    Else
        JsonStr_ = JsonUnescape_(m.Item(0).SubMatches(0))
    End If
End Function

' Extract a JSON number field "key":value from one flat object, as its raw digits. "0" if absent.
Private Function JsonNum_(ByVal obj As String, ByVal key As String) As String
    Dim re As Object, m As Object, q As String
    q = Chr$(34)
    Set re = CreateObject("VBScript.RegExp")
    re.Pattern = q & key & q & "\s*:\s*(-?[0-9]+(\.[0-9]+)?)"
    Set m = re.Execute(obj)
    If m.Count = 0 Then
        JsonNum_ = "0"
    Else
        JsonNum_ = m.Item(0).SubMatches(0)
    End If
End Function

' Reverse the JSON string escapes that JsonEscape (and JSON.stringify) can emit: \" \\ \/ \n \r \t \uXXXX.
Private Function JsonUnescape_(ByVal s As String) As String
    Dim out As String, i As Long, c As String, nxt As String
    i = 1
    Do While i <= Len(s)
        c = Mid$(s, i, 1)
        If c = "\" And i < Len(s) Then
            nxt = Mid$(s, i + 1, 1)
            Select Case nxt
                Case """": out = out & """": i = i + 2
                Case "\": out = out & "\": i = i + 2
                Case "/": out = out & "/": i = i + 2
                Case "n": out = out & vbLf: i = i + 2
                Case "r": out = out & vbCr: i = i + 2
                Case "t": out = out & vbTab: i = i + 2
                Case "u"
                    If i + 5 <= Len(s) Then
                        out = out & ChrW$(CLng("&H" & Mid$(s, i + 2, 4)))
                        i = i + 6
                    Else
                        out = out & c: i = i + 1
                    End If
                Case Else
                    out = out & nxt: i = i + 2
            End Select
        Else
            out = out & c
            i = i + 1
        End If
    Loop
    JsonUnescape_ = out
End Function

' Create the export query if missing, and keep its SQL in sync with PLANTS_SQL on every run. PLANTS_SQL
' is the single source of truth for the export columns, so a redeploy that changes the columns must
' refresh the stored query -- otherwise the nursery PC keeps exporting the OLD column set and the
' Sheet mirror silently diverges. (Setting .SQL each run is cheap and overwrites any manual edit to
' the saved query, by design.) Lets the nursery PC deploy with no manual query step.
Private Sub EnsureQueryDef_()
    Dim db As DAO.Database, qd As DAO.QueryDef
    Set db = CurrentDb
    On Error Resume Next
    Set qd = db.QueryDefs(QUERY_NAME)
    On Error GoTo 0
    If qd Is Nothing Then
        db.CreateQueryDef QUERY_NAME, PLANTS_SQL
    Else
        qd.SQL = PLANTS_SQL
    End If
    Set qd = Nothing
    Set db = Nothing
End Sub

' Reads QUERY_NAME and fills the JSON for the header array (byref) and the rows
' array-of-arrays (byref). Returns the row count. Snapshot recordset = read-only.
Private Function BuildPayloadParts(ByRef headerJson As String, _
                                   ByRef rowsJson As String) As Long
    Dim db As DAO.Database, rs As DAO.Recordset
    Dim i As Long, n As Long, rc As Long, k As Long
    Dim cols() As String, cells() As String, rows() As String

    EnsureQueryDef_                        ' auto-create the query on first run
    Set db = CurrentDb
    Set rs = db.OpenRecordset(QUERY_NAME, dbOpenSnapshot)

    n = rs.Fields.Count
    ReDim cols(0 To n - 1)
    ReDim cells(0 To n - 1)
    For i = 0 To n - 1
        cols(i) = """" & JsonEscape(rs.Fields(i).Name) & """"   ' raw Access column names
    Next i
    headerJson = "[" & Join(cols, ",") & "]"

    If Not rs.EOF Then
        rs.MoveLast                 ' make RecordCount accurate for a snapshot
        rs.MoveFirst
    End If
    rc = rs.RecordCount

    If rc > 0 Then
        ReDim rows(0 To rc - 1)
        k = 0
        Do While Not rs.EOF
            For i = 0 To n - 1
                cells(i) = JsonValue(rs.Fields(i))
            Next i
            rows(k) = "[" & Join(cells, ",") & "]"
            k = k + 1
            rs.MoveNext
        Loop
        rowsJson = "[" & Join(rows, ",") & "]"
    Else
        rowsJson = "[]"
    End If

    rs.Close
    Set rs = Nothing
    Set db = Nothing
    BuildPayloadParts = rc
End Function

' Format one DAO field as a JSON value based on its type.
Private Function JsonValue(ByVal fld As DAO.Field) As String
    Dim v As Variant
    v = fld.Value
    If IsNull(v) Then
        JsonValue = "null"
        Exit Function
    End If
    Select Case fld.Type
        Case dbBoolean
            JsonValue = IIf(v, "true", "false")
        Case dbByte, dbInteger, dbLong, dbSingle, dbDouble, dbCurrency, dbDecimal, dbBigInt
            ' Force a dot decimal separator regardless of Windows regional settings.
            JsonValue = Replace(CStr(v), ",", ".")
        Case dbDate
            JsonValue = """" & Format$(v, "yyyy-mm-dd hh:nn:ss") & """"
        Case Else
            JsonValue = """" & JsonEscape(CStr(v)) & """"
    End Select
End Function

' Minimal JSON string escaping.
Private Function JsonEscape(ByVal s As String) As String
    Dim i As Long, code As Long, c As String, out As String
    s = Replace(s, "\", "\\")
    s = Replace(s, """", "\""")
    s = Replace(s, vbCrLf, "\n")
    s = Replace(s, vbCr, "\n")
    s = Replace(s, vbLf, "\n")
    s = Replace(s, vbTab, "\t")
    ' Any OTHER control character (U+0000..U+001F: backspace, form-feed, vertical tab, NUL, ...) is
    ' illegal raw inside a JSON string -- JSON.parse on the server rejects it and the WHOLE push
    ' fails. These slip in via text pasted from Word/PDF, so escape them as \u00XX. (We mask AscW to
    ' an unsigned 16-bit code unit because AscW is signed: chars >= U+8000 return negative and would
    ' otherwise be mistaken for control chars.)
    For i = 1 To Len(s)
        c = Mid$(s, i, 1)
        code = AscW(c) And &HFFFF&
        If code < 32 Then
            out = out & "\u" & Right$("000" & Hex$(code), 4)
        Else
            out = out & c
        End If
    Next i
    JsonEscape = out
End Function

' POST the payload. Returns True only on HTTP 200 with an {"ok":true} body. Thin wrapper around
' PostJsonResponse for callers (the plant push, markSalesSynced) that only need success/failure.
Private Function PostJson(ByVal url As String, ByVal payload As String) As Boolean
    Dim body As String
    PostJson = PostJsonResponse(url, payload, body)
End Function

' POST the payload, returning the response body byref (so sales-in can parse the pendingSales JSON).
' Returns True only on HTTP 200 with an {"ok":true} body. Apps Script 302-redirects /exec to
' googleusercontent.com; ServerXMLHTTP 6.0 follows that automatically and returns the final JSON.
Private Function PostJsonResponse(ByVal url As String, ByVal payload As String, _
                                  ByRef respBody As String) As Boolean
    Dim http As Object
    respBody = ""
    On Error GoTo Fail
    Set http = CreateObject("MSXML2.ServerXMLHTTP.6.0")
    http.setTimeouts T_RESOLVE, T_CONNECT, T_SEND, T_RECEIVE
    http.Open "POST", url, False
    http.setRequestHeader "Content-Type", "application/json;charset=UTF-8"
    http.send payload
    If http.Status = 200 Then
        ' Success only when the body explicitly says ok:true AND does not say ok:false, so an error
        ' body that merely echoes the substring "ok":true (e.g. in a message) can't read as success.
        respBody = http.responseText
        PostJsonResponse = (InStr(1, respBody, """ok"":true", vbTextCompare) > 0) And _
                           (InStr(1, respBody, """ok"":false", vbTextCompare) = 0)
    End If
    Exit Function
Fail:
    PostJsonResponse = False
End Function

' Deterministic 31-bit polynomial hash of the payload, returned as hex.
' Intermediate math is done in Double (exact for integers < 2^53) so the
' multiply-then-mod never overflows a 32-bit Long. Collision risk is negligible
' for change-detection; worst case is one skipped sync, corrected on the next
' real change. Runs every ~10 min, so the per-character loop cost is irrelevant.
Private Function HashString(ByVal s As String) As String
    Const P As Double = 2147483647#    ' 2^31 - 1 (Mersenne prime)
    Const M As Double = 131#
    Dim h As Double, t As Double, i As Long, n As Long
    n = Len(s)
    For i = 1 To n
        t = h * M + AscW(Mid$(s, i, 1))
        h = t - Int(t / P) * P         ' h = t mod P, stays < 2^31, exact in Double
    Next i
    HashString = Hex$(CLng(h))
End Function
