Attribute VB_Name = "modPlantSync"
Option Compare Database
Option Explicit

' ============================================================================
'  modPlantSync  -  pushes the in-stock plant view (qryPlantsExport) to the
'  Google Apps Script web app as action "replacePlants" (a full-mirror rewrite
'  of the "Plants" sheet). Lives in the FRONT-END (GFRBG.mdb).
'
'  Wiring (see docs/deploy/access.md):
'    * On an always-open form (e.g. the switchboard):
'        - Timer Interval = 600000  (10 minutes)
'        - Form_Timer:        modPlantSync.SyncPlants
'    * "Sync now" button On Click:  modPlantSync.SyncPlants True, True
'
'  Design (mirrors the Android app):
'    * Re-entrancy guard (mPushing) so a slow POST can't overlap the next tick
'      -- the Access analog of the app's cloudMutex serializing cloud ops.
'    * Timer path swallows all errors (no popups); the manual button can show
'      them (showErrors:=True).
'    * Cheap change-detection: hash the EXACT payload and skip the POST when it
'      matches the last SUCCESSFUL push (stored via SaveSetting). The hash is
'      updated ONLY on a confirmed ok response, so a failed push retries on the
'      next tick -- the analog of the app flipping rows to EXPORTED on success.
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

' POST the payload. Returns True only on HTTP 200 with an {"ok":true} body.
' Apps Script 302-redirects /exec to googleusercontent.com; ServerXMLHTTP 6.0
' follows that automatically and returns the final JSON.
Private Function PostJson(ByVal url As String, ByVal payload As String) As Boolean
    Dim http As Object
    On Error GoTo Fail
    Set http = CreateObject("MSXML2.ServerXMLHTTP.6.0")
    http.setTimeouts T_RESOLVE, T_CONNECT, T_SEND, T_RECEIVE
    http.Open "POST", url, False
    http.setRequestHeader "Content-Type", "application/json;charset=UTF-8"
    http.send payload
    If http.Status = 200 Then
        ' Success only when the body explicitly says ok:true AND does not say ok:false, so an error
        ' body that merely echoes the substring "ok":true (e.g. in a message) can't read as success.
        Dim body As String
        body = http.responseText
        PostJson = (InStr(1, body, """ok"":true", vbTextCompare) > 0) And _
                   (InStr(1, body, """ok"":false", vbTextCompare) = 0)
    End If
    Exit Function
Fail:
    PostJson = False
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
