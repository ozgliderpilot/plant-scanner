# Connect a device to the backend

Do this once per device, after installing the app (`android.md` / Play) and deploying the backend
(`backend.md`).

> **Running the test app too?** The **"GF Nursery TEST"** install is a separate app with its own
> Settings — configure it independently here, pointing it at the **test** `/exec` URL + secret (see
> [backend.md → Standing up a test deployment](backend.md)). Its local data is fully separate from
> production.

## Prefer: magic link (one tap)

1. Ensure the Sheet has a **`Users`** tab with header row `device_prefix | name | secret`
   (the script can create the tab on first claim if missing). Optionally pre-fill rows with a
   prefix + volunteer name and leave **secret** blank.
2. Host `docs/` on **GitHub Pages** (Settings → Pages → Deploy from branch `main` / folder `/docs`)
   so `https://ozgliderpilot.github.io/plant-scanner/setup.html` is public. Gmail and most chat
   apps only make `https://` links tappable — plain `plantscanner://` is not.
3. Build one link per device prefix:

   ```bash
   node scripts/magic-link.js 07 'https://script.google.com/macros/s/…/exec' 'nursery-secret'
   ```

   The script prints two lines:

   1. **`https://…/setup.html?…`** — send this in email/SMS (volunteer taps → bridge page → app)
   2. **`plantscanner://setup?…`** — for QR codes or `adb` testing

4. Volunteer installs from Play, opens the **https** link on that phone, taps **Open app and finish
   setup**. The app saves settings, generates a **device secret**, and syncs. First successful sync
   claims that prefix on the Users tab — a second phone with the same link is rejected until an
   admin clears that row’s **secret** cell.

## Or: enter settings by hand

In the app: **History** tab → five taps on the version string → **Settings**. Fill in:

| Field | Value |
|-------|-------|
| **Device prefix (two digits)** | A number **unique to this device**, e.g. `07`. It namespaces receipt numbers (`07-1`, `07-2`, …) so two devices never collide in the Sheet. **Give each device a different prefix.** |
| **Google Sheets Web App URL** | The `/exec` URL from `backend.md` step 4. |
| **Access code (shared secret)** | The `SHARED_SECRET` value you set in `backend.md` step 3. |
| **Auto-export every (seconds)** | `60` (default = 1 minute). Minimum 10. Interval for background cloud sync. |

Tap **Save**. The app generates a device secret automatically; the first sync claims the Users row
the same way as a magic link.

## 2. Pull the plant list

Open the **Plants** tab and tap **↻**. You should see the plant count appear after a successful
cloud sync ("N plants cached" / list rows). This caches the list on the device so scanning works
fully offline. (History ↻ runs the same cloud sync.)

## 3. Test a sale end-to-end

1. **Actions → Sell plants**.
2. Scan a plant barcode (or **Type number instead** and enter an accession). The plant card should
   auto-fill. If it's not in the list, choose **Sell as unknown**.
3. Enter **Pots**, **Unit price**, optional **Discount %**. Check the live **Line total**.
4. **Add to receipt → Finish & save**. The confirmation shows the receipt # (`07-…`) and total.
5. Within ~1 minute (online), pending sales export as part of cloud sync. Open the Google Sheet —
   a new **`Sales`** tab has the row(s). Or tap **History ↻** / **Plants ↻** to sync immediately.

## How the two talk

```
 Android app  ──HTTPS POST {secret, devicePrefix, deviceSecret, action}──►  Apps Script (/exec)  ──►  Google Sheet
   (per device)         JSON over the wire            shared secret + Users claim          Plants / Sales / Users
        ▲                                                                                         │
        └──────────────  getPlants returns the plant list (cloud sync import)  ◄──────────────────┘
```

- **Selling works offline.** Receipts are saved on the device immediately.
- **Cloud sync** exports pending receipts/culls then imports the plant list every minute when online
  (and on History/Plants ↻). The ticker is silent; manual ↻ surfaces Done/Error. If offline, it
  retries next minute; nothing is lost.
- **No double counting:** each receipt # is sent once; the backend also skips any receipt # already in
  the Sheet.
- **One phone per prefix:** Users-tab `secret` is set on first claim; mismatched device secrets are
  rejected (`Unauthorized`). Clear the cell to allow a replacement phone.

## If something looks wrong

- Status chip stuck on **Offline·N** → device has no internet; sales are safe, they'll go when back online.
- History/Plants **↻** shows an error → check the URL (must end `/exec`) and access code match the backend.
- Sync says **Unauthorized** after a magic link → that prefix is already claimed by another phone, or
  this phone was reinstalled; clear **Users → secret** for that prefix (or assign a new prefix).
- Plants don't load → confirm the Sheet tab is named exactly `Plants` and has an `accession` header.
- Two devices show the same receipt numbers → give them **different two-digit prefixes** in Settings.
