# Connect a device to the backend

Do this once per device, after installing the app (`android.md`) and deploying the backend
(`backend.md`).

> **Running the test app too?** The **"Nursery TEST"** install is a separate app with its own
> Settings — configure it independently here, pointing it at the **test** `/exec` URL + secret (see
> [backend.md → Standing up a test deployment](backend.md)). Its local data is fully separate from
> production.

## 1. Enter the settings

In the app: **Sync** tab → **Settings**. Fill in:

| Field | Value |
|-------|-------|
| **Device prefix (two digits)** | A number **unique to this device**, e.g. `07`. It namespaces receipt numbers (`07-1`, `07-2`, …) so two devices never collide in the Sheet. **Give each device a different prefix.** |
| **Google Sheets Web App URL** | The `/exec` URL from `backend.md` step 4. |
| **Access code (shared secret)** | The `SHARED_SECRET` value you set in `backend.md` step 3. |
| **Auto-export every (seconds)** | `60` (default = 1 minute). Minimum 10. |

Tap **Save**.

## 2. Pull the plant list

Still on the **Sync** tab → **Update plant list**. You should see the plant count appear
("N plants cached"). This caches the list on the device so scanning works fully offline.

## 3. Test a sale end-to-end

1. **Actions → Sell plants**.
2. Scan a plant barcode (or **Type number instead** and enter an accession). The plant card should
   auto-fill. If it's not in the list, choose **Sell as unknown**.
3. Enter **Pots**, **Unit price**, optional **Discount %**. Check the live **Line total**.
4. **Add to receipt → Finish & save**. The confirmation shows the receipt # (`07-…`) and total.
5. Within ~1 minute (online), the top **status chip** flips from `Pending 1` to `Synced just now`.
   Open the Google Sheet — a new **`Sales`** tab has the row(s). Or tap **Sync → Export now** to push
   immediately.

## How the two talk

```
 Android app  ──HTTPS POST {secret, action}──►  Apps Script Web App (/exec)  ──►  Google Sheet
   (per device)         JSON over the wire            shared-secret auth          Plants / Sales tabs
        ▲                                                                              │
        └───────────────  getPlants returns the plant list (manual pull)  ◄───────────┘
```

- **Selling works offline.** Receipts are saved on the device immediately.
- **Auto-export** pushes pending receipts every minute when online — silently. The status chip is the
  only feedback. If offline, it just retries next minute; nothing is lost.
- **No double counting:** each receipt # is sent once; the backend also skips any receipt # already in
  the Sheet.

## If something looks wrong

- Status chip stuck on **Offline·N** → device has no internet; sales are safe, they'll go when back online.
- **Export now** shows an error → check the URL (must end `/exec`) and access code match the backend.
- Plants don't load → confirm the Sheet tab is named exactly `Plants` and has an `accession` header.
- Two devices show the same receipt numbers → give them **different two-digit prefixes** in Settings.
