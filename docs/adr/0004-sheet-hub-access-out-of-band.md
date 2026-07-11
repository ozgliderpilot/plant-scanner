# Google Sheet is the hub; Access is out of band

The app’s only cloud peer is Google Sheets via Apps Script. MS Access is the nursery’s upstream/downstream system of record for stock and reverse sync, but the Android app never talks to Access — stock on mobile is **record-only** (sales, culls, label requests); count corrections stay in Access.
