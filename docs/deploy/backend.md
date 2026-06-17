# Deploy the backend (Google Sheets + Apps Script)

The "server" is a Google Apps Script Web App bound to one Google Sheet. No Google Cloud project,
no OAuth — the app authenticates with a shared secret. ~10 minutes.

## 1. Create the Sheet

1. Go to <https://sheets.google.com> and create a new spreadsheet. Name it e.g. **Nursery Sales**.
2. Rename the first tab to **`Plants`** (exact, case-sensitive) and put this header in row 1:

   | accession | name | group | light |
   |-----------|------|-------|-------|

   Fill rows below with your plant list. The **accession** is the value the label's Code 128 barcode
   encodes (there is no separate barcode column). Only **accession** and **name** are required;
   `group`, `light` may be blank. Columns can be reordered — they are matched by header name.
3. You do **not** need to create a `Sales` tab — the script creates it (with headers) on first export.

## 2. Add the script

1. In the Sheet: **Extensions → Apps Script**. An editor opens.
2. Delete the sample `Code.gs` contents and paste the contents of **`backend/Code.gs`**.
3. Add a new script file (the **+** next to "Files" → Script), name it **`shared`**, and paste the
   contents of **`backend/shared.js`** into it. (GAS merges all script files, so `Code.gs` can call
   the shared functions.)
4. (Optional but recommended) Click the gear **Project Settings → "Show appsscript.json"**, then open
   `appsscript.json` and replace it with **`backend/appsscript.json`** (sets timezone + web-app access).

## 3. Set the shared secret

1. **Project Settings (gear) → Script Properties → Add script property**.
2. Property: **`SHARED_SECRET`**, Value: a strong passphrase (e.g. a 20+ char random string).
   Keep this — you'll type it into each device once.

## 4. Deploy as a Web App

1. **Deploy → New deployment**.
2. Select type (gear) → **Web app**.
3. Description: `nursery`; **Execute as: Me**; **Who has access: Anyone**.
   ("Anyone" = anonymous; required because the app calls it without a Google login. The shared
   secret is what protects it.)
4. **Deploy** → authorize when prompted (review the scopes, allow).
5. Copy the **Web app URL** — it ends in `/exec`. This is the **endpoint URL** for the app.

> Re-deploying for code changes: **Deploy → Manage deployments → (edit) → New version**. The
> `/exec` URL stays the same.

## 5. Smoke-test (optional)

From any machine:

```bash
curl -L -X POST "<YOUR_EXEC_URL>" \
  -H "Content-Type: application/json" \
  -d '{"secret":"<YOUR_SECRET>","action":"getPlants"}'
```

Expect `{"ok":true,"plants":[...],"count":N,...}`. A wrong secret returns `{"ok":false,"error":"Unauthorized"}`.

## Alternative: clasp (command line)

```bash
npm install -g @google/clasp
clasp login
# In an empty dir bound to your script's ID:
clasp clone <SCRIPT_ID>
# copy backend/Code.gs and backend/shared.js in, then:
clasp push
```

You still set `SHARED_SECRET` and deploy the Web App from the editor (steps 3–4).

## What the endpoint does

- `POST {secret, action:"getPlants"}` → `{ok, plants:[{accession,name,group,light}], count, updatedAt}`
- `POST {secret, action:"appendSales", header:[...], rows:[[...]]}` → `{ok, appended, skipped}`
  - Appends to `Sales`; **skips any row whose receipt # already exists** (no double counting), so a
    re-sent batch is harmless.

> **2026-06-17:** `shared.js`/`shared.gs` `parsePlants` now also returns `potsInNursery` /
> `tubesInNursery` / `miscInNursery`. Redeploy the web app, then on each device tap **"Update plant
> list"** once so the cache repulls with the stock counts (they drive the Pots/Tubes/Misc default on
> the line-item screen). The `Sales` sheet now has a `unit` column after `qty`.
