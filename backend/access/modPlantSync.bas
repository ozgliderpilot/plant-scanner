Attribute VB_Name = "modPlantSync"
Option Compare Database
Option Explicit

' ============================================================================
'  modPlantSync  -  the nursery PC's two-way sync with the Google Apps Script
'  web app. Lives in the FRONT-END (GFRBG.mdb). One SyncPlants run does, in order:
'    1. Sales-in (reverse sync): pull "Pending" Sales rows out of the Sheet and
'       decrement the matching Batches stock in Access, then mark them "Synced".
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

' Immediate-window self-test for the deduction arithmetic in THIS slice (pots clamp), mirroring the
' SyncSelfTest precedent -- VBA has no JVM/JS harness, so the arithmetic is checked here. The misc->pots
' overflow and tubes cases arrive with the dependent slices and will extend this sub then. Makes NO
' network call and touches NO data. Run from the Immediate window:  modPlantSync.DeductSelfTest
Public Sub DeductSelfTest()
    Debug.Print "--- DeductSelfTest (pots) ---"
    PrintClamp_ 10, 3, 7      ' normal decrement
    PrintClamp_ 5, 5, 0       ' exactly to zero
    PrintClamp_ 2, 9, 0       ' oversell clamps at zero
    PrintClamp_ 0, 1, 0       ' already empty stays zero
End Sub

Private Sub PrintClamp_(ByVal cur As Long, ByVal qty As Long, ByVal want As Long)
    Dim got As Long
    got = ClampSub_(cur, qty)
    Debug.Print "pots " & cur & " - " & qty & " = " & got & _
                IIf(got = want, "  OK", "  FAIL (want " & want & ")")
End Sub

' ============================================================================
'  Sales-in (reverse sync): Pending Sales rows -> Access stock decrement
' ============================================================================

' Pull every "Pending" Sales row from the Sheet and, for the rows THIS SLICE handles (unit = "pots"
' matching exactly one Batches row), decrement PotsInNursery and record the sale in the local ledger --
' the two in one DAO transaction so a crash can never leave one without the other. Already-ledgered rows
' are re-flipped on the Sheet only (never re-deducted). Finally mark the applied rows "Synced".
'
' SCOPE (slice 1): only unit = "pots" matching EXACTLY ONE batch is acted on. Every other row -- tubes,
' misc, no match, ambiguous (>1 batch), unknown unit -- is left "Pending" and untouched for later slices.
'
' Resilience: a failed POST or parse just Exits; nothing is half-applied and the next run retries. The
' caller (SyncPlants) swallows any error this raises so sales-in can never block the plant push.
Private Sub ApplyPendingSales_(ByVal url As String, ByVal secret As String)
    Dim db As DAO.Database
    Dim body As String, payload As String
    Dim sales As Collection, marks As Collection, row As Variant
    Dim i As Long
    Dim receipt As String, accession As String, unit As String, ledStatus As String
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

        ledStatus = LedgerStatus_(db, receipt, itemSeq)
        If Len(ledStatus) > 0 Then
            ' Already applied locally. Re-flip the Sheet only (a prior markSalesSynced may have failed);
            ' the ledger is the authority, so we NEVER decrement again.
            marks.Add MarkJson_(receipt, itemSeq, ledStatus)
        ElseIf unit = "pots" Then
            ' This slice: a pots row that resolves to EXACTLY ONE batch is deducted; 0 or >1 matches are
            ' left Pending & untouched (the NoMatch/ambiguous handling is a dependent slice).
            If CountBatchMatches_(db, accession) = 1 Then
                If ApplyPotsDeduction_(db, receipt, itemSeq, accession, qty) Then
                    marks.Add MarkJson_(receipt, itemSeq, "Synced")
                End If
                ' transaction failed -> not ledgered, left Pending, retried next run
            End If
        End If
        ' non-pots rows: left Pending & untouched (later slices)
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

' Decrement PotsInNursery for the single matching batch AND insert the ledger row, in ONE DAO
' transaction on Workspaces(0) (which covers the linked Jet back-end). Returns True only if both
' committed; on any error it rolls back and returns False, leaving the row un-applied for the next run.
' Caller has already verified unit = "pots" and exactly one matching batch.
Private Function ApplyPotsDeduction_(ByRef db As DAO.Database, _
                                     ByVal receipt As String, ByVal itemSeq As Long, _
                                     ByVal accession As String, ByVal qty As Long) As Boolean
    Dim ws As DAO.Workspace
    Dim rs As DAO.Recordset
    Dim inTrans As Boolean

    On Error GoTo Fail
    Set ws = DBEngine.Workspaces(0)
    ws.BeginTrans
    inTrans = True

    Set rs = OpenBatchByAccession_(db, accession, True)    ' updatable dynaset
    If rs.EOF Then Err.Raise vbObjectError, , "batch vanished"   ' guard: caller checked count=1
    rs.Edit
    rs![PotsInNursery] = ClampSub_(Nz(rs![PotsInNursery], 0), qty)
    rs.Update
    rs.Close
    Set rs = Nothing

    LedgerInsert_ db, receipt, itemSeq, "Synced"

    ws.CommitTrans
    inTrans = False
    ApplyPotsDeduction_ = True
    Exit Function
Fail:
    If Not rs Is Nothing Then
        On Error Resume Next
        rs.Close
        On Error GoTo 0
    End If
    If inTrans Then ws.Rollback
    ApplyPotsDeduction_ = False
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

' Count Batches rows whose [Ac Number] equals `accession` (>1 is treated as ambiguous upstream). The
' compare is exact: [Ac Number] holds numeric-only values, so no trim/case folding is needed -- and an
' exact compare on the bare column can use an index instead of scanning every row.
Private Function CountBatchMatches_(ByRef db As DAO.Database, ByVal accession As String) As Long
    Dim rs As DAO.Recordset
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

' Open the Batches rows matching `accession` by an exact compare on [Ac Number]. That column holds
' numeric-only values, so no trim/case folding is needed; the bare-column compare can use an index. The
' accession arriving from selectPendingSales is already trimmed. A parameterised query avoids any
' quoting/escaping of the accession. SELECT Batches.* from a single table keeps the dynaset updatable.
Private Function OpenBatchByAccession_(ByRef db As DAO.Database, ByVal accession As String, _
                                       ByVal updatable As Boolean) As DAO.Recordset
    Dim qd As DAO.QueryDef
    Set qd = db.CreateQueryDef("", _
        "PARAMETERS pAcc Text ( 255 ); " & _
        "SELECT Batches.* FROM Batches " & _
        "WHERE Batches.[Ac Number]=[pAcc];")
    qd.Parameters("pAcc").Value = accession
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

' Insert one ledger row. Called only inside ApplyPotsDeduction_'s transaction, so it must raise (not
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
Private Function MarkJson_(ByVal receipt As String, ByVal itemSeq As Long, _
                           ByVal status As String) As String
    MarkJson_ = "{""receipt"":""" & JsonEscape(receipt) & """," & _
                """item_seq"":" & itemSeq & "," & _
                """status"":""" & JsonEscape(status) & """}"
End Function

' Parse the pendingSales response into a Collection of Variant arrays Array(receipt, item_seq,
' accession, qty, unit). We control the response shape (Code.gs handlePendingSales_ -> selectPendingSales
' in shared.js), so each element is a FLAT JSON object {"receipt":..,..,"unit":..} with no nested braces,
' and the identifiers never contain '{' '}' or ']'. So: slice out the "sales":[ ... ] array text, then
' match each {...} object and pull its fields by name (order-independent). Anything else -> empty.
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
            JsonStr_(obj, "unit"))
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
