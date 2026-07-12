# Access → Google Sheets plant sync (nursery PC setup)

Pushes the in-stock plant view from the nursery's Access front-end (`GFRBG.mdb`) to the Apps Script
web app as the `replacePlants` action — a full-mirror rewrite of the `Plants` tab. Runs as an in-app
**Form Timer** (Option A): only while the database is open, which is exactly when the nursery is
operating and stock changes. No Windows Task Scheduler, no second copy of the database, no
always-on requirement.

The VBA lives in [`backend/access/modPlantSync.bas`](../../backend/access/modPlantSync.bas) — push
routine, payload builder, change-detection, HTTP POST. It also embeds the export query (`PLANTS_SQL`):
Batches ⋈ Species on `Id No`, all 43 columns, filtered to `Pots+Tubes+Misc+Stock > 0`, one row per
accession — and auto-creates it (`qryPlantsExport`) on first run.

## One-time setup

> If the front-end opens straight into the volunteer menu with no navigation pane or ribbon, hold
> **Shift** while opening `GFRBG.mdb` to bypass the startup lock-down — that gives you the navigation
> pane and full menus needed for the steps below.

1. **The query is created automatically.** `modPlantSync` builds `qryPlantsExport` on its first run if
   it's missing (from its embedded `PLANTS_SQL`), so there's no manual query step. It also **rewrites
   the query's SQL from `PLANTS_SQL` on every run** — `PLANTS_SQL` is the single source of truth for
   the export columns, so don't hand-edit `qryPlantsExport`; any manual change is overwritten on the
   next sync.

2. **Import the module.** VBA editor (Alt+F11) → File → Import File… → select
   `backend/access/modPlantSync.bas`.

3. **Set the URL + secret as Windows environment variables (nursery PC only).** The module reads them
   at run time from user environment variables — they are *not* stored in the database — so any copy
   taken home (without the variables) silently skips syncing. From a Command Prompt on the nursery PC:

   ```
   setx GFRBG_SYNC_URL    "https://script.google.com/macros/s/XXXX/exec"
   setx GFRBG_SYNC_SECRET "your-shared-secret"
   ```

   `GFRBG_SYNC_SECRET` must match the GAS Script Property `SHARED_SECRET`. After running `setx`, fully
   close and reopen Access so it picks up the new values (`Environ` reads the environment captured at
   launch). If either variable is missing, `SyncPlants` does nothing.

4. **Wire the timer.** Open the always-open form (the switchboard / main menu) in Design view →
   Property Sheet → **Event** tab:
   - Set **Timer Interval** = `600000` (10 minutes; tune to taste).
   - On **On Timer**, choose `[Event Procedure]` and put:
     ```vba
     Private Sub Form_Timer()
         modPlantSync.SyncPlants            ' silent; errors swallowed
     End Sub
     ```

5. **Add a "Sync now" button** (optional but handy). Place a button on the form → On Click event:
   ```vba
   Private Sub btnSyncNow_Click()
       modPlantSync.SyncPlants True, True   ' force = True, showErrors = True
   End Sub
   ```

6. **Trusted Location.** File → Options → Trust Center → Trust Center Settings → Trusted Locations →
   add the folder holding `GFRBG.mdb`, so VBA runs each launch without the macro-security prompt.

## How it behaves

- **Sync runs only where both environment variables are set** (the nursery PC). On any other copy
  `SyncPlants` exits immediately and never contacts Google — that's the home-copy safeguard. Use
  `SyncSelfTest` in the Immediate window to check: its `config:` line reports each variable SET or
  MISSING (it never prints the secret value).
- **Every tick** it runs `qryPlantsExport`, builds the exact JSON payload, and hashes it. If the hash
  matches the last **successful** push (stored via `SaveSetting` under `GFRBG\PlantSync\LastHash`), it
  **skips the POST** — so an idle nursery makes no cloud calls. Any change to the exported data (stock
  counts, a plant selling out of the filter, or an edited Species column) changes the hash and
  triggers a push.
- The hash is saved **only on a confirmed `ok` response**, so a failed push automatically retries on
  the next tick (nothing lost).
- A **re-entrancy guard** prevents a slow POST from overlapping the next tick.
- The first run (no stored hash) always pushes.
- Each successful action is timestamped on the **`SyncStatus`** tab of the Sheet (rolling log of
  the last 100 sync events, newest first), so you can see recent plant pushes from Access and
  pulls / pushes with devices.

## Notes / gotchas

- The push uses `MSXML2.ServerXMLHTTP.6.0` with short timeouts; it follows the Apps Script
  302-redirect automatically.
- `SyncPlants` from the timer never shows UI. Use the button (`showErrors:=True`) for manual checks.
- The `Plants` sheet ends up with the raw Access column names; the backend's `getPlants`/`parsePlants`
  reads them (accession ← `Ac Number`, group ← `Plant Type`, light ← `Sun/Shade`, name composed from
  `Genus`/`Species`/`Cultivar`), so the Android app is unaffected.
