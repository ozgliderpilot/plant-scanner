Attribute VB_Name = "modFixFamilyTypos"
Option Compare Database
Option Explicit

Public Sub FixFamilyTypos()
    Dim db As DAO.Database
    Dim sql As String
    Dim total As Long
    Dim i As Long

    Set db = CurrentDb
    total = 0
    i = 0
    On Error GoTo Fail

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Crasssulaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Crassualaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Crassulacea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Crassulaeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Cracculaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Crassualceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Crassulaceae' WHERE Family = 'Crassulacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Lamiaceae' WHERE Family = 'Laminaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Lamiaceae' WHERE Family = 'Laminiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asteraceae' WHERE Family = 'Asteracae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asteraceae' WHERE Family = 'Asteraceaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asteraceae' WHERE Family = 'Ateraceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asteraceae' WHERE Family = 'Asteracaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Ericaceae' WHERE Family = 'Ericaeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Ericaceae' WHERE Family = 'Erisaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Iridaceae' WHERE Family = 'Iradaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Iridaceae' WHERE Family = 'Irisaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Iridaceae' WHERE Family = 'Iridicaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Theaceae' WHERE Family = 'Theraceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Theaceae' WHERE Family = 'Theacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Proteaceae' WHERE Family = 'Protaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Proteaceae' WHERE Family = 'Proteacea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Myrtaceae' WHERE Family = 'Myteraceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Myrtaceae' WHERE Family = 'Myrlaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Myrtaceae' WHERE Family = 'Mytaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Ranunculaceae' WHERE Family = 'Ranuculaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Ranunculaceae' WHERE Family = 'Ranuncalaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Ranunculaceae' WHERE Family = 'Rununculaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asphodelaceae' WHERE Family = 'Aloaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asphodelaceae' WHERE Family = 'Aloeaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asphodelaceae' WHERE Family = 'Asphdelaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asphodelaceae' WHERE Family = 'Aspodelaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asphodelaceae' WHERE Family = 'Astrodelaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asphodelaceae' WHERE Family = 'Asphodeloideae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Rutaceae' WHERE Family = 'Ruteaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Rutaceae' WHERE Family = 'Rutacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Amaryllidaceae' WHERE Family = 'Amaryillidaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Amaryllidaceae' WHERE Family = 'Amarylidaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Myroporaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Scophulariaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Scrophulariaeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Scrophulariceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Scropulariaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Schropulariaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Sciphulariaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Scrophulariaceae' WHERE Family = 'Scrophuliaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Rosaceae' WHERE Family = 'Roseacea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Liliaceae' WHERE Family = 'Lililaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Hydrangeaceae' WHERE Family = 'Hydrangaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Malvaceae' WHERE Family = 'Malvacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Bromeliaceae' WHERE Family = 'Bromelliaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Bromeliaceae' WHERE Family = 'Bromiliaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Bromeliaceae' WHERE Family = 'Bromilaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Fabaceae' WHERE Family = 'Fabiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caprifoliaceae' WHERE Family = 'Caprifliaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caprifoliaceae' WHERE Family = 'Caprifoliacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Geraniaceae' WHERE Family = 'Geranaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Geraniaceae' WHERE Family = 'Geraniceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Geraniaceae' WHERE Family = 'Gerianaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Geraniaceae' WHERE Family = 'Genaraceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Campanulaceae' WHERE Family = 'Campanuliaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Agavaceae' WHERE Family = 'Agavacee'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caryophyllaceae' WHERE Family = 'Carophyllaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caryophyllaceae' WHERE Family = 'Caryophllaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caryophyllaceae' WHERE Family = 'Caryophyllaeceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caryophyllaceae' WHERE Family = 'Caryopyllaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Onagraceae' WHERE Family = 'Onograceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Araceae' WHERE Family = 'Aracaeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Araceae' WHERE Family = 'Aracaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Araceae' WHERE Family = 'Araceacea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Saxifragaceae' WHERE Family = 'Saxifragacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Solanaceae' WHERE Family = 'Solenaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Solanaceae' WHERE Family = 'Solinaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Solanaceae' WHERE Family = 'Solonaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Cactaceae' WHERE Family = 'Cactceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Apocynaceae' WHERE Family = 'Apocyanceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Rubiaceae' WHERE Family = 'Rubicaeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Cupressaceae' WHERE Family = 'Taxoniaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Cupressaceae' WHERE Family = 'Cuppressaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Cupressaceae' WHERE Family = 'Cupressaceaee'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Verbenaceae' WHERE Family = 'Verbanaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Verbenaceae' WHERE Family = 'Verbenacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Bignoniaceae' WHERE Family = 'Bignoneaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Boraginaceae' WHERE Family = 'Boriginaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Sterculiaceae' WHERE Family = 'Stirculiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Papaveraceae' WHERE Family = 'Papaveracee'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Papaveraceae' WHERE Family = 'Papavariceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Podocarpaceae' WHERE Family = 'Podarpaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Compositae' WHERE Family = 'Compositeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Compositae' WHERE Family = 'Compositiae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Fagaceae' WHERE Family = 'Faceaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Gesneriaceae' WHERE Family = 'Generiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Gesneriaceae' WHERE Family = 'Gensneriaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Leguminosae' WHERE Family = 'Leguminoseae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Fagaceae' WHERE Family = 'Fagacaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Leguminosae' WHERE Family = 'Lugiminosae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Melastomataceae' WHERE Family = 'Melastomaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Papilionaceae' WHERE Family = 'Papionaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Plumbaginaceae' WHERE Family = 'Plumbaginceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Caesalpiniaceae' WHERE Family = 'Caesalpinaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Aizoaceae' WHERE Family = 'Azioaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Aizoaceae' WHERE Family = 'Aizoaoceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asclepiadaceae' WHERE Family = 'Asclepiadiceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Commelinaceae' WHERE Family = 'Commelianaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Gentianaceae' WHERE Family = 'Gentianiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Umbelliferae' WHERE Family = 'Umbilliferae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Elaeocarpaceae' WHERE Family = 'Eleaocarpaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Styracaceae' WHERE Family = 'Styraceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Flacourtiaceae' WHERE Family = 'Flacouritiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Aspleniaceae' WHERE Family = 'Aspleniacae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Fumariaceae' WHERE Family = 'Fumeriaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Cruciferae' WHERE Family = 'Crucifereae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Labiatae' WHERE Family = 'Labiateae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Casuarinaceae' WHERE Family = 'Casurinaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Mimosaceae' WHERE Family = 'Mimosacea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Hypoxidaceae' WHERE Family = 'Hypoxicadeae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Haloragaceae' WHERE Family = 'Halorigidaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Betulaceae' WHERE Family = 'Corylaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Dipsacaceae' WHERE Family = 'Diipsaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Asteliaceae' WHERE Family = 'Astelaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Chloanthaceae' WHERE Family = 'Chloanathaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Dryopteridaceae' WHERE Family = 'Dryopteridiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Malpighiaceae' WHERE Family = 'Malighiaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Cobaeaceae' WHERE Family = 'Cobaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Davidiaceae' WHERE Family = 'Davidaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Eupomatiaceae' WHERE Family = 'Eupomateaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Guttiferae' WHERE Family = 'Guttiferaea'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Pentaphylacaceae' WHERE Family = 'Pentaphlacaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    sql = "UPDATE Species SET Family = 'Saururaceae' WHERE Family = 'Sauruaceae'"
    db.Execute sql, dbFailOnError
    total = total + db.RecordsAffected
    i = i + 1

    MsgBox "Done. Statements: " & i & "; rows updated: " & total, vbInformation
    Exit Sub

Fail:
    MsgBox "Failed on statement " & (i + 1) & vbCrLf & Err.Description & vbCrLf & sql, vbCritical
End Sub

