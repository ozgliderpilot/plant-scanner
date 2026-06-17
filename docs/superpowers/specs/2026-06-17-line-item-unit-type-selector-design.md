# Line-item Unit-type Selector (Pots / Tubes / Misc) — Design

**Date:** 2026-06-17
**Status:** Approved, ready for implementation plan
**Related:** `docs/superpowers/specs/2026-06-15-access-google-sheets-plant-sync-design.md` (where the
stock counts originate), `CLAUDE.md` (core-first rule, export-header stability rule)

## Problem

On the line-item screen (step ② of the Sell flow), the volunteer keys a quantity against a fixed
"Pots" label. The nursery actually sells a plant accession in one of three unit kinds — **Pots**,
**Tubes**, or **Misc** — and which kinds are in stock varies per accession. The volunteer should pick
the unit kind from a dropdown, pre-set to the kind most likely in stock, and that choice must land in
the Sales sheet so reconciliation knows what was sold.

The per-accession stock counts already exist upstream: the `Plants` sheet is the raw 43-column Access
view and carries `PotsInNursery` / `TubesInNursery` / `MiscInNursery`. They are currently dropped at
`parsePlants` (only `{accession, name, group, light}` reach the app), so the first half of this work is
plumbing those three counts through to the device; the second half is the dropdown + capturing the
selection.

## Requirements

1. On the line-item screen, replace the fixed "Pots" label with a **dropdown: Pots / Tubes / Misc**.
2. **Default selection**, driven by the scanned plant's counts (exact rule):
   - **Pots**, unless `PotsInNursery == 0`,
   - then **Misc**, unless `MiscInNursery == 0`,
   - then **Tubes**, unless `TubesInNursery == 0`,
   - then **Pots** again (fallback).
3. The selected unit is written to the **Sales sheet** (new `unit` column).
4. All three options are always selectable (the rule only chooses the *default*).

### Decisions made during brainstorming

- **Unknown lines (not-found scan):** the dropdown still shows and defaults to **Pots**. An unknown
  line has no counts, i.e. all-zero, which the default rule already maps to Pots — uniform behaviour,
  Sales always carries a unit.
- **Quantity stepper label:** changes from "Pots" to a generic **"Quantity"**; the dropdown alone
  conveys the unit kind.
- **Sales column:** a new **`unit`** column placed immediately **after the quantity column**.
- **Quantity column renamed `pots` → `qty`.** With a unit dropdown, the count is a quantity of
  *pots/tubes/misc*, so "pots" is misleading. Renamed across the core domain model and the export
  header. The Room `line_items` column stays physically named `pots` (renamed only at the mapper) — see
  Migration.
- **Live Sales sheet already updated by the user** to include the `unit` column in the right place; no
  backend self-healing of the header is needed.

## Data flow

```
Plants sheet  (raw Access cols: PotsInNursery / TubesInNursery / MiscInNursery)
   └─ shared.js parsePlants  → emits potsInNursery / tubesInNursery / miscInNursery (number, Nz→0)
        └─ PlantDto → PlantEntity (Room) → core Plant            [counts drive the default selection]

LineItemScreen dropdown (Pots/Tubes/Misc) → SellViewModel.commitDraft(unit)
   └─ LineItem.unit → LineItemEntity.unit (Room) → Export "unit" column → Sales sheet  [selection captured]
```

## Core (`core/` — pure JVM, unit-tested; this is where the error-prone logic lives)

- **`SaleUnit` enum** `{ POTS, TUBES, MISC }`, each with a human label (`"Pots"`, `"Tubes"`, `"Misc"`)
  used both for the dropdown and as the exact string written to the Sales sheet.
- **Pure default-selection function** on `SaleUnit` (companion), e.g.
  `defaultFor(pots: Int, tubes: Int, misc: Int): SaleUnit`, implementing requirement #2 exactly:

  | pots | misc | tubes | → default |
  |------|------|-------|-----------|
  | > 0  | any  | any   | **POTS**  |
  | 0    | > 0  | any   | **MISC**  |
  | 0    | 0    | > 0   | **TUBES** |
  | 0    | 0    | 0     | **POTS** (fallback) |

- **`Plant`** gains `potsInNursery`, `tubesInNursery`, `miscInNursery: Int = 0`.
- **`LineItem`**: rename `pots` → `qty`; add `unit: SaleUnit = SaleUnit.POTS` (default keeps existing
  call sites/tests compiling and is a safe value for any legacy row).
- **`Money`**: `receiptTotalCents` reads `it.qty`; `lineTotalCents`'s first parameter renamed `qty`
  (cosmetic — called positionally). The math is unchanged.
- **`PlantBook.toLine` / `toUnknownLine`**: rename the `pots` parameter to `qty`, add a `unit`
  parameter, set it on the built `LineItem`.
- **`Export`**:
  - `HEADER` becomes `["receipt","date","accession","name","qty","unit","unit_price","discount_pct","line_total"]`
    (was `…,"pots","unit_price",…`). **`unit` sits right after `qty`.** Header order stays the
    backend's contract; the live sheet already matches.
  - `ExportRow`: rename `pots` → `qty`; add `unit: SaleUnit`.
  - `buildRows` carries `line.qty` and `line.unit`; `rowAsStrings` emits `qty.toString()` then
    `unit.label` in the new positions.

### Core tests
- New `SaleUnitTest`: the full default-selection truth table above, including all-zero → POTS and the
  "pots present wins regardless" cases.
- `ExportTest`: `HEADER` shape/order, `unit` present after `qty`, `rowAsStrings` positions and the unit
  label round-trip.
- `PlantBookTest`: `toLine` / `toUnknownLine` carry `qty` and `unit`.

## App (`app/` — thin glue, no unit tests runnable here)

- **`PlantDto`** (`data/remote/Dtos.kt`): add `potsInNursery`, `tubesInNursery`, `miscInNursery: Int = 0`.
- **`PlantEntity`**: add the three `Int` columns (default 0).
- **`LineItemEntity`**: add `unit: String` (default `"POTS"`). The quantity column **stays
  `pots`** physically; do not rename it (see Migration).
- **`Mappers.kt`** — the naming seam:
  - `PlantEntity ↔ Plant` carry the three counts.
  - `LineItemEntity.toCore()` → `LineItem(qty = pots, unit = SaleUnit.valueOf(unit), …)`.
  - `LineItem.toEntity()` → `LineItemEntity(pots = qty, unit = unit.name, …)`.
- **`LineDraft`** (`SellViewModel.kt`): add the three counts + `unit: SaleUnit`.
  `fromPlant` sets `unit = SaleUnit.defaultFor(p.potsInNursery, p.tubesInNursery, p.miscInNursery)`;
  `unknown` sets counts 0 and `unit = SaleUnit.POTS`.
- **`SellViewModel`**: `commitDraft` gains a `unit: SaleUnit` parameter and sets it on the `LineItem`;
  `beginEdit` re-enriches counts from the book and preserves the saved line's `unit`.
- **`LineItemScreen`**: add a Material3 `ExposedDropdownMenuBox` (Pots/Tubes/Misc) above the stepper;
  the stepper label becomes **"Quantity"** with generic +/- content descriptions; the local `pots`
  state is renamed `qty`; `commitDraft` is called with the selected unit.
- **`CartScreen` / `ReceiptDetailScreen`**: update `line.pots` → `line.qty`. (Optional: show the unit
  next to the quantity, e.g. "3 Tubes × $4.00" — nice-to-have, decide in the plan.)

### Migration (Room `version = 1` → `2`)

Purely **additive**, so no data loss (pending/SAVED receipts and the plant cache survive):

```sql
ALTER TABLE plants     ADD COLUMN potsInNursery  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plants     ADD COLUMN tubesInNursery INTEGER NOT NULL DEFAULT 0;
ALTER TABLE plants     ADD COLUMN miscInNursery  INTEGER NOT NULL DEFAULT 0;
ALTER TABLE line_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'POTS';
```

- Bump `@Database(version = 2)` in `NurseryDatabase.kt`; define `MIGRATION_1_2` and wire it via
  `.addMigrations(MIGRATION_1_2)` on the `Room.databaseBuilder` in `di/AppContainer.kt`.
- **No SQLite `RENAME COLUMN`**: minSdk is 26 and the bundled SQLite on API 26–29 predates 3.25, so
  the `pots → qty` rename is done in Kotlin/at the mapper, **not** in the DB. The physical column stays
  `pots`; only the Room entity property and the mapper translate it.

## Backend

- **`shared.js` `parsePlants`**: also resolve `potsInNursery` / `tubesInNursery` / `miscInNursery`
  (raw Access header names, matched case-insensitively as `potsinnursery` etc.), coerced to numbers
  with blank/missing → 0, and include them on each returned plant object. `accession/name/group/light`
  behaviour is unchanged.
- **`backend/test/logic.test.js`**: cases for the three counts — present numbers parsed, blank/missing
  → 0, raw-header matching.
- **`Code.gs`**: no functional change. `appendSales` resolves columns by header name, so the new `unit`
  column is handled automatically. (Optional: update the stale "pots" word in the `forceTextColumn_`
  comment to "qty".) `getPlants` just returns whatever `parsePlants` emits, so the new counts flow
  through with no handler change.
- **Redeploy** `shared.gs` (the `shared.js` mirror) to the web app so the device starts receiving the
  counts.

## Deployment notes

- Redeploy the Apps Script web app after the `shared.gs` change (counts won't reach devices otherwise).
- The live `Sales` sheet header is **already** updated by the user to include `unit` after `qty`; new
  app builds writing the new `HEADER` will align with it.
- After installing the new app build, the volunteer taps **"Update plant list"** once so the cache is
  repulled with the counts populated (existing cached rows default the counts to 0, which would make
  every default fall back to Pots until the repull).

## Out of scope / YAGNI

- The `PotsForSale` / `TubesForSale` / `MiscForSale` flags (the rule keys only on the `*InNursery`
  counts, per the requirement).
- Disabling/hiding dropdown options whose count is 0 (all three stay selectable by decision #4).
- Plant List screen / `PlantSearch` surfacing the counts (not needed for this feature).
- Backend self-healing of an out-of-date Sales header (the user already fixed the live sheet).
```
