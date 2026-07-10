# Cull plants — record locally + export to Sheets

**Date:** 2026-07-01
**Status:** Approved (grilling sessions 2026-07-01)
**Tracker:** GitHub issue #26 (record) · #27 (export)
**Related:** #28 (Sheets → Access)

## Goal

Let volunteers record culled plants on the phone instead of a paper sheet: scan/type
accession → enter qty, pot type, reason, optional notes → save locally → view recent
culls on Sync → export pending rows to Google Sheets (export transport in #27).

## Background

The paper cull sheet captures plant name, accession, quantity, pot/tube, dead tick,
reason (when live), date, and a "database updated" tick. Like sales, there is not
enough volunteer time to enter culls into Access manually afterward.

Within the nursery, all discard situations (failed germination, tube-stage stall, death
after potting, unsold quality loss, pest/disease, end of life) are collectively called
**culling**.

The Sell flow (Scan → LineItem → Cart → Confirm) is the closest analogue. Cull is
simpler: one accession per save, no cart, no money fields.

## Scope

### In scope (#26)

- Actions tab **Cull plants** entry (second enabled button under Sell)
- Cull nav graph: Scan → Enter info → Success (`CullViewModel` scoped to graph)
- Local persistence: Room `culls` table, `MIGRATION_4_5` (additive, no destructive reset)
- `core/` models, validation, search, list ordering, retention GC, cull pot-type default
- Sync UI: rename Sales export → **Data export** (combined pending + single Export now);
  **Culled plants** card with **View culled plants** only
- View culled plants screen (search, pending stripe, retained records)
- Combined pending count on status chip and cull success screen
- Retention GC for **both** exported receipts and exported culls (~72h from `createdAt`)
- Export plumbing: manual + auto tick drain both queues (cull leg implemented in #27 delivery)

### Out of scope (#26 implementation)

- Access ingestion of cull rows (#28)
- Edit/delete saved culls or receipts after save
- Local `*InNursery` stock decrement

### In scope (#27 — ships in same delivery as taxonomic snapshot / sales header extension)

- `CullExport.HEADER` + row builder in `core/`
- `CullSync` pending / mark-exported in `core/`
- Extend `Plant`, `LineItem`, plant sync JSON with genus/species/cultivar/commonName
- Extend `Export.HEADER` (sales) with taxonomic + group columns
- Room migration: taxonomic columns on `line_items` and `culls`
- `SheetsClient.appendCulls`, `SyncRepository` cull leg (unstub)
- Backend `appendCulls` + `getOrCreateCullsSheet_`
- Auto-provision `sync_status` on Sales and Culls tabs (behaviour change for Sales)

## Decisions locked (grilling 2026-07-01)

| Topic | Decision |
|-------|----------|
| Pot-type default | **Tube → Pot → Misc** (new `CullUnit.defaultFor` in `core/`; **not** Sell's `SaleUnit.defaultFor`) |
| Unknown accession | Allowed — `name = "unknown"`, keep accession, `isUnknown = true` |
| Group | Show only when present (`PlantCard` behaviour) |
| Dead vs reason | **Dead** is one dropdown reason; no separate boolean |
| Status | `PENDING` → `EXPORTED`; **EXPORTED** = pushed to Google Sheets |
| Cull id | `ReceiptNumbering` (`PP-<epoch>-<seq>`), **shared daily counter** with sales |
| Stock | Record-only on mobile |
| Export timing | Offline-first; ~60s `AutoExportTicker` + manual Export now; never block success screen |
| Retention | ~72h from `createdAt` for exported rows; keep pending forever; GC on auto-export tick |
| Post-save | No edit/delete |
| Notes | Optional, max 200 chars |
| Search | Accession, name, group, reason label, notes |
| Reason labels | `Dead`, `Poor quality`, `Pest`, `Disease`, `Other` (UI + export) |
| Actions entry | **Cull plants** button; remove disabled **Record death** placeholder |
| Pending UI | Combined on chip, success screen, Data export tile |
| Sync layout | One Data export tile; Culled plants card is view-only |
| Export feedback | `Exported (3 sales, 1 cull)` style breakdown |
| Qty | Min 1, no max (mirror Sell) |

## Decisions locked (grilling #27, 2026-07-01)

| Topic | Decision |
|-------|----------|
| Dedupe key | **`cull_id` only** — not accession (same plant can be culled twice) |
| Culls export header | `cull_id, date, accession, name, genus, species, cultivar, common_name, group, qty, unit, reason, notes` |
| Sales export header | `receipt, date, item_seq, accession, name, genus, species, cultivar, common_name, group, qty, unit, unit_price, discount_pct, line_total` |
| Taxonomic fields | Stored separately on `Plant`; **snapshotted at save** on `LineItem` / `CullRecord` (not looked up at export) |
| `name` | Stored alongside parts — composed snapshot from sync (`Plant.name` or `"unknown"`), not derived locally |
| Unknown rows | `name = "unknown"`; genus, species, cultivar, common_name, group = `""` |
| Naming | Plant-sync JSON: `commonName` (camelCase); export headers: `common_name` (snake_case) |
| Partial export failure | **Independent queues** — sales stay `EXPORTED` if culls fail; retry culls on next tick |
| Backend handler | Separate **`appendCulls`** action (parallel to `appendSales`; reuse shared helpers) |
| Auto-export culls | Same ~60s `AutoExportTicker` as sales |
| Mark exported | HTTP `ok` → **all** pending culls → `EXPORTED`, even when backend reports skipped duplicates |
| Delivery scope | Sales header + `Plant` extension ship **in the same work** as cull export |
| Export status column | Sheet-only **`sync_status`**; backend stamps `Pending`; #28 flips to `Synced`; **not** in app payload |
| `lastSynced` | Update when **either** sales or culls export succeeds |
| Tab bootstrap | Auto-create **Culls** tab on first append; auto-add **`sync_status`** on first append (**same for Sales** — replaces manual provisioning) |
| `sync_status` position | **Last column** on new tabs (`appHeader + sync_status`) |
| Existing Sales tab | If `sync_status` already exists (any position), use it by name — **do not duplicate** |

## Design

### 1. Navigation

- Add `CULL_GRAPH` nested graph: `cull/scan`, `cull/info`, `cull/success`
- One `CullViewModel` scoped to graph back-stack entry (copy `sellViewModel()` pattern)
- Routes added to `Routes`; kept **out** of `TabRoutes` (full-screen, bars hidden)
- Add `Routes.CULLS = "culls"` for View culled plants (full-screen, like `Routes.PLANTS`)

### 2. Scan

- Reuse `ScannerView` / `BarcodeAnalyzer` verbatim
- Screen wrapper: `CullScanScreen` or parameterised scan ("Cull as unknown" / `CullViewModel`)
- Lookup: `PlantBook.findByScan` against cached book
- Not found: "Not in plant list" + **Cull as unknown** / **Retry**

### 3. Enter info

Reuse from Sell:

- `PlantCard` (name, accession, group when present)
- Qty stepper (min 1)
- `UnitDropdown` over `SaleUnit`

Cull-specific:

- Pot-type default via `CullUnit.defaultFor(tubes, pots, misc)` — priority **Tube → Pot → Misc**
- `CullReason` dropdown (default `DEAD`)
- Optional notes `OutlinedTextField` (max 200 chars)
- Primary **Record cull**

### 4. Success

Mirror `ConfirmScreen`:

- Cull id (`cullNo` from `ReceiptNumbering`) / plant name
- **Cull another** → scan
- **Done** → pop cull graph
- Pending sync count is intentionally omitted from this screen (not duplicated here)

### 5. `core/` module

| Piece | Responsibility |
|-------|----------------|
| `Plant` (extend) | Add `genus`, `species`, `cultivar`, `commonName`; `name` remains composed from sync |
| `LineItem` (extend) | Snapshot `name`, genus, species, cultivar, commonName, group at save |
| `CullReason` | Enum + stable `label` for UI/export |
| `CullStatus` | `PENDING`, `EXPORTED` |
| `CullRecord` | Full model + validation; snapshots same plant-identity fields as `LineItem` |
| `CullList` | `isPending`, `grouped` (pending-first, newest within group) |
| `CullSearch` | Filter across accession, name, group, reason, notes |
| `CullUnit.defaultFor` | Tube → Pot → Misc default |
| `CullExport` | `HEADER`, `CullExportRow`, `buildRows()`, `rowAsStrings()` |
| `CullSync` | `pending()`, `markExported()` over `CullStatus.PENDING` |
| `Retention` | `isEligibleForPurge(createdAt, status, now)` — exported and older than ~72h |
| `Sync` (extend) | `totalPendingCount(receipts, culls)` for combined chip/success |
| `Export` (extend) | Wider `HEADER` / `ExportRow` with taxonomic + group columns |

Reused unchanged: `PlantBook`, `ReceiptNumbering`, `SaleUnit` (labels for pot type column).

**Plant sync:** `parsePlants` / `PlantDto` emit genus, species, cultivar, `commonName` alongside
composed `name`. Sell and Cull save paths copy all six identity fields from the matched plant (or
unknown defaults above).

### 6. `app/` glue

- `CullViewModel`, `CullScanScreen`, `EnterInfoScreen`, `CullSuccessScreen`
- `CullListScreen` + `CullListViewModel` (mirror `PlantListScreen`)
- `CullEntity`, `CullDao`, `CullRepository`, mappers
- Save as `PENDING` with `cullNo` from `settings.nextReceiptSeq()` (shared counter)
- `HomeScreen`: enable **Cull plants**; drop **Record death** from disabled list
- `SyncScreen`: rename card to **Data export**; add Culled plants card; wire combined export
- `SyncRepository`: `exportAll()` (mutex) drains sales then culls; independent per-queue success;
  update `lastSynced` on either leg succeeding; GC on same tick
- `SheetsClient.appendCulls` + `AppendCullsRequest/Response` DTOs
- `AppContainer`: register `MIGRATION_4_5`

### 7. Room migration

DB version 4 → 5: additive `CREATE TABLE culls` in `MIGRATION_4_5` (includes taxonomic +
group columns on cull rows). A further migration (or combined 4→5 if not yet shipped) adds
genus, species, cultivar, commonName, group to `line_items` (default `""` for existing rows).
No `fallbackToDestructiveMigration`.

### 8. Sync & export

**Data export** card (renamed from Sales export):

- Combined pending count (receipts `SAVED` + culls `PENDING`)
- Auto interval label unchanged
- Single **Export now** → `SyncRepository.exportAll()` (mutex): push sales, then culls (stub), then retention GC

**Culled plants** card:

- **View culled plants** only

**AutoExportTicker:** same combined pass; silent on all outcomes.

Manual result: `Exported (3 sales, 1 cull)` when both types export; `Exported (0)` when empty.
Partial failure example: `Exported (3 sales, 0 culls) · Cull export failed`.

### 8b. Export to Google Sheets (#27)

**Culls worksheet contract**

- Tab name: **`Culls`**
- App payload header (stable): see decisions table above
- Dedupe: `filterNewRows` on **`cull_id`** column (every row independent — unlike sales' shared `receipt`)
- `date`: ISO local date from `createdAt` (same zone as sales)
- `unit`: `SaleUnit.label` (`Pot` / `Tube` / `Misc`)
- `reason`: `CullReason.label`
- `sync_status`: last column; auto-added on first append if missing; `stampPending_` writes `Pending`

**Sales worksheet (extended in same delivery)**

- App payload header widened with genus, species, cultivar, common_name, group after `name`
- Column order must match the live Sales tab (volunteer already added these columns manually)
- `sync_status`: auto-added on first append if missing (behaviour change — no longer manual-only)

**Backend (`appendCulls`)**

- Thin parallel handler mirroring `handleAppendSales_`
- Reuses `filterNewRows`, `forceTextColumn_` (on `cull_id` + `accession`), `stampPending_`, `recordSync_`
- Response shape: `{ ok, appended, skipped }` (same as sales)
- `getOrCreateCullsSheet_`: create tab + write app header when missing/empty; ensure `sync_status` at end

**Backend (`appendSales` update)**

- `getOrCreateSalesSheet_` + shared helper: ensure `sync_status` column exists (append at end if
  missing; if already present anywhere in row 1, use by name — do not duplicate)

**#28 handoff**

- `pendingCulls` / `markCullsSynced` key on single column **`cull_id`** (simpler than sales'
  `(receipt, item_seq)` composite)
- `notes` in worksheet payload (stock-plant rule: `Notes = 'Stock plant'`)
- `sync_status` `Pending` → `Synced` driven by Access reverse sync

### 9. Retention

- Pending rows: never purged
- Exported rows: purge when `now - createdAt > 72h` (constant in `core/Retention`; exact boundary on next tick is fine)
- Applies to receipts and culls
- DAO `DELETE` queries called from export tick path

### 10. View culled plants

- `ScreenHeader` + search box + `LazyColumn` of cull cards
- `CullList.grouped` ordering; pending cards get left accent stripe (mirror `ReceiptCard`)
- Empty states: no culls / no search matches

## Testing

**`core/` (JUnit):**

- `CullUnit.defaultFor` — Tube → Pot → Misc priority
- `CullReason` labels + default
- `CullRecord` validation (qty, notes length)
- `CullList` pending predicate + grouped ordering
- `CullSearch.filter` — each field, case-insensitive substring
- `Retention` — pending kept, exported purged after window
- `Sync.totalPendingCount` — combined count
- `CullExport.buildRows` / `rowAsStrings` — header order, unknown blanks, reason labels

**`backend/` (Node):**

- `appendCulls` dedupe by `cull_id`; `stampPending_` on new rows
- `sync_status` auto-provision on create / first append

**`app/`:** manual on emulator/device (scan, unknown path, save, list, export message, partial export).

## Files touched (expected)

- `core/` — new Cull* + Retention + Sync extension + tests
- `app/.../ui/cull/*` (new screens + ViewModel)
- `app/.../ui/culls/CullListScreen.kt` (new)
- `app/.../data/local/` — entity, dao, migration 4→5
- `app/.../data/repo/CullRepository.kt` (new)
- `app/.../ui/home/HomeScreen.kt`
- `app/.../ui/sync/SyncScreen.kt`, `SyncViewModel.kt`
- `app/.../data/repo/SyncRepository.kt`
- `app/.../sync/AutoExportTicker.kt` (if export entry point renamed)
- `app/.../ui/NurseryRoot.kt`, `Destinations.kt`
- `app/.../di/AppContainer.kt`, `NurseryViewModelFactory.kt`
- `core/Export.kt`, `core/Models.kt` (Plant, LineItem extensions)
- `app/.../data/remote/Dtos.kt`, `SheetsClient.kt`
- `backend/Code.gs`, `backend/shared.js`, `backend/test/logic.test.js`
