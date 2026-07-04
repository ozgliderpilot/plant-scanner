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

## Standing up a test deployment (isolated from production)

To exercise the **test** app flavor (`com.nursery.scanner.test`, label "Nursery TEST" — see
[android.md](android.md)) without ever touching live data, give it its **own** Sheet + Apps Script
deployment. Cloud isolation is purely operational — there is **nothing to change in code**:

1. **Repeat steps 1–5 above** to create a *second* spreadsheet (e.g. **Nursery Sales — TEST**) with
   its own `Plants` tab, its own pasted `Code.gs`/`shared.js`, its **own** `SHARED_SECRET` (use a
   *different* secret from production), and its own Web App deployment. You get a **separate `/exec`
   URL**.
2. In the **test** app install only, open **Sync → Settings** and enter that test `/exec` URL + test
   secret (and a distinct device prefix if you like). Leave the **production** install pointed at the
   production URL/secret.
3. Sales exported from the test app now land **only** in the test Sheet; the production Sheet is
   untouched. Confirm by making a test sale and watching only the test Sheet's `Sales` tab grow.

Because the endpoint URL + secret are **per-device runtime config** (entered in Settings, never baked
into the build), one APK flavor can point at either backend — the test flavor exists only to keep the
*local* DB separate and make the install unmistakable on the device.

## Automated deploy (clasp)

Code in `backend/` can be pushed with [clasp](https://github.com/google/clasp) instead of
copy-paste. **Prod** deploys on merge to `main` (GitHub Actions). **Test** deploys automatically
on pull requests that change `backend/Code.gs` or `backend/shared.js`, and can still be run
locally with one command.

### One-time setup (per environment)

Do this once for **each** Sheet/script (test + prod):

1. Complete steps 1–4 above (Sheet, paste or push code, `SHARED_SECRET`, **first** Web App
   deployment).
2. Note the **Script ID** (Project Settings → IDs) and the **Deployment ID** of the Web App
   (Deploy → Manage deployments → copy the ID column, *not* the `/exec` URL).

Local config (gitignored — copy the `.example` files in `backend/`):

```bash
cd backend
npm install
cp .clasp.test.json.example .clasp.test.json   # set test scriptId
cp .clasp.prod.json.example .clasp.prod.json   # set prod scriptId (CI uses secrets instead)
cp gas-deploy.json.example gas-deploy.json     # set both deploymentIds
npx clasp login
```

GitHub repo secrets (Settings → Secrets → Actions):

| Secret | Value |
|--------|--------|
| `CLASPRC_JSON` | Full contents of `~/.clasprc.json` after `clasp login` |
| `GAS_TEST_SCRIPT_ID` | Test Apps Script project ID |
| `GAS_TEST_DEPLOYMENT_ID` | Test Web App deployment ID |
| `GAS_PROD_SCRIPT_ID` | Prod Apps Script project ID |
| `GAS_PROD_DEPLOYMENT_ID` | Prod Web App deployment ID |

Refresh `CLASPRC_JSON` if the OAuth token expires.

### Deploy test (CI)

Open or update a PR to `main` that touches `backend/Code.gs` or `backend/shared.js` → workflow
**Deploy GAS (test)** runs automatically (logic tests, `clasp push`, redeploy test Web App). New
commits on the same PR re-deploy test; only the latest run completes if several are queued.

> Fork PRs do not receive repository secrets, so test deploy is skipped for those (CI tests still
> run). Use a branch in this repo for backend changes you want deployed to test.

### Deploy test (CLI)

From `backend/`:

```bash
npm run deploy:test
```

Runs `backend/test/logic.test.js`, `clasp push`, then redeploys the **test** Web App (same `/exec`
URL, new code version). Requires `clasp login` on your machine.

### Deploy prod (CI)

Merge to `main` with changes under `backend/` → workflow **Deploy GAS (prod)** runs automatically.
Manual prod deploy (emergency): `npm run deploy:prod` with local `.clasp.prod.json` configured.

> `clasp push` alone only updates project HEAD. The deploy step (`clasp deploy -i <deploymentId>`)
> is what publishes to the live `/exec` URL — same as **Manage deployments → New version** in the
> editor.

## What the endpoint does

- `POST {secret, action:"getPlants"}` → `{ok, plants:[{accession,name,group,light}], count, updatedAt}`
- `POST {secret, action:"appendSales", header:[...], rows:[[...]]}` → `{ok, appended, skipped}`
  - Appends to `Sales`; **skips any row whose receipt # already exists** (no double counting), so a
    re-sent batch is harmless.

> **2026-06-17:** `shared.js`/`shared.gs` `parsePlants` now also returns `potsInNursery` /
> `tubesInNursery` / `miscInNursery`. Redeploy the web app, then on each device tap **"Update plant
> list"** once so the cache repulls with the stock counts (they drive the Pots/Tubes/Misc default on
> the line-item screen). The `Sales` sheet now has a `unit` column after `qty`.
