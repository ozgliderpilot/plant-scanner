# Repot — update stock counts on an accession

**Date:** 2026-07-11
**Status:** Decisions locked (chat + #86, 2026-07-11)
**Tracker:** GitHub issue #86
**Related:** Cull (#26–#28), Print label (#77), New accession (deferred)

## Goal

Let volunteers set **Tubes / Pots / Misc. / Stock plant** counts for an accession on
the phone instead of paper, then push the update through Google Sheets into Access.
The everyday case is tubes becoming pots (~90%); the same screen also fixes stock
discrepancies.

## Background

Physical events that change pot-type counts:

1. **Tray / cuttings → tubes** — out of scope here; covered by **New accession**.
2. **Tubes → pots** — classic repot (main case).
3. **Tubes and/or pots → hanging baskets** — hanging baskets are always **Misc.**
   Multi-accession (“multi-number”) baskets are rare: each barcode is its own accession;
   the volunteer runs Repot once per accession. No special merge UI.

Same-size pot upsizing does **not** change counts — stay out of the app.

A **stock plant** (`StockInNursery`) is a propagation source, **not for sale**. It has
no Access for-sale flag. Repot may still edit its count; for-sale ticks exist only for
Tubes / Pots / Misc.

Sell and Cull already move inventory *down*. Repot is the flow that **sets absolute
counts** to what the volunteer sees on the bench (and sets the three for-sale flags).

## Absolute counts (not a transfer form)

The screen shows **current** counts and the volunteer enters **new totals** for each
kind — not “move N from tubes to pots.”

Example: list says `T 12 · P 0` → volunteer sets Tubes **0**, Pots **12**.  
Correction example: listed `T 3 · P 10`, reality `2` and `8` → set **2** and **8**.

## What it should feel like

Same spine as Cull:

```
Actions → Repot → scan accession → update counts → success
         → save locally → cloud sync queue → Sheets → Access
```

Offline-first. Mobile is **record-only**; local `*InNursery` counts are **not** rewritten
as source of truth (they refresh on the next plant-list import after Access applies the
row).

### Actions entry

- Enable the existing **Repot** home tile (drop it from “Coming later”).
- Full-screen flow (bars hidden), like Sell / Cull / Print label.

---

## Screen-by-screen

### 1. Scan

Reuse the shared scanner + manual accession entry (same as Sell / Cull).

| Outcome | Behaviour |
|---------|-----------|
| Accession **found** in local plant list | Continue to Update counts |
| Accession **not found** | **Block.** Dialog: *Please contact the database manager.* Actions: **Retry** / **Done**. No “continue as unknown.” |

Rationale: plant list is stock-gated. Zero-stock accessions are invisible on purpose.
Inventing a repot for an unknown accession cannot update Access safely. Same strictness
as Print label (#77); Sell/Cull keep unknown for scan-loss prevention only.

### 2. Update counts

Layout (top → bottom), large tap targets:

1. **Plant card** — name, accession, group when present (reuse `PlantCard`).
2. **Current stock line** — `PlantStock.summary` (`T n · P n · M n · St n` as applicable).
3. **Four quantity steppers** — **Tubes**, **Pots**, **Misc.**, **Stock plant**
   - Prefill from the plant list (`tubesInNursery` / `potsInNursery` / `miscInNursery` /
     `stockInNursery`).
   - Min **0**. No max.
   - Volunteer may change any combination (everyday transfer *or* discrepancy fix).
4. **Three Ready for sale ticks** (Access parity — T/P/M only; **no** tick for Stock plant):
   - Ready for sale — **Tubes**
   - Ready for sale — **Pots**
   - Ready for sale — **Misc.**
5. **Notes** — optional, max 200 characters.
6. Primary button **Save repot**.

#### Validation (`core/`)

- Each of tubes / pots / misc / stock ≥ 0.
- Save enabled only when **something changed** vs values at open (any count or any of
  the three for-sale ticks).
- No unknown / not-found path on this screen.

#### All counts zero — confirm dialog

If Tubes + Pots + Misc. + Stock plant are all **0** at save:

> Number of all types of pots are zero. Is it correct?

- **Cancel** → stay on Update counts.
- **Confirm** → proceed to save.

#### Ready for sale — default rules (`core/`)

Three independent ticks. Defaults when the screen opens (and when steppers change, until
the volunteer manually overrides a given tick):

| Tick | Default |
|------|---------|
| **Pots** | **Checked** if new pots > 0, except genus Camellia / Rhododendron / Hosta → **unchecked**. If pots = 0 → **unchecked**. |
| **Tubes** | **Checked** if new tubes > 0 **and** group is Herb; otherwise **unchecked**. |
| **Misc.** | **Checked** if new misc > 0 (hanging baskets); otherwise **unchecked**. |

- Genus match: case-insensitive exact match on `Plant.genus` against
  `Camellia`, `Rhododendron`, `Hosta`.
- Herb match: case-insensitive — `Plant.group` equals `Herb` or `Herbs`, or contains
  `herb` (confirm exact Access `Plant Type` strings during Access apply work).
- Per-tick: once the volunteer toggles that tick, keep their choice for the rest of
  the screen visit; other ticks may still follow stepper-driven defaults.

### 3. Success

Mirror Cull success:

- Repot id + plant name (and optionally a one-line count summary).
- **Repot another** → back to scan.
- **Done** → leave the Repot graph.

No edit/delete after save (same as Cull).

### 4. History / Sync (view)

- **View repots** list on History/Sync (search by accession, name, group, notes;
  pending stripe; newest first).
- Pending repots join the combined sync-queue count and **Export now** / auto ticker
  (order with sales/culls locked at implementation).

---

## Data & sync contract

### Local record

| Field | Notes |
|-------|--------|
| `repot_id` | Shared `ReceiptNumbering` (`PP-<epoch>-<seq>`), same daily counter as sales/culls/labels |
| `date` / `createdAt` | Device confirm time |
| Accession + taxonomic snapshot | Same six identity fields as Cull (`name`, genus, species, cultivar, commonName, group) |
| `tubes_before`, `pots_before`, `misc_before`, `stock_before` | Snapshot of list counts at save |
| `tubes`, `pots`, `misc`, `stock` | New absolute counts |
| `tubes_for_sale`, `pots_for_sale`, `misc_for_sale` | Three booleans (no stock-for-sale) |
| `notes` | Optional |
| `status` | `PENDING` → `EXPORTED` only after HTTP success |

### Export header (proposed — lock with backend before implement)

```
repot_id, date, accession, name, genus, species, cultivar, common_name, group,
tubes_before, pots_before, misc_before, stock_before,
tubes, pots, misc, stock,
tubes_for_sale, pots_for_sale, misc_for_sale,
notes
```

Backend stamps sheet-only `sync_status` last (same pattern as Culls). Access apply
(later slice) writes absolute `*InNursery` counts and the three `*ForSale` flags.

### Cloud sync

Same unified `syncCloud`: drain pending queues then import plants. Local counts on
the device update only after Access has applied the row and the next plant import
mirrors it — volunteers may briefly see old counts until then (acceptable; same as
sales/culls today).

---

## Scope slices

| Slice | What ships |
|-------|------------|
| **App record** | Scan → Update counts → Success; Room + `core/` validation/defaults; View repots; pending in sync chip |
| **Export** | `RepotExport` / `RepotSync` in `core/`; Sheets **Repots** tab; `appendRepots` |
| **Access apply** | Sheets → Access absolute counts + three for-sale flags; ledger on `repot_id` |

### Explicitly out of this feature

- New accession / tray pricking / cuttings from stock plants (as a separate process).
- Same pot-type size upsizing.
- Multi-barcode basket as one transaction.
- Expanding the Plants export to zero-stock accessions.
- “Repot as unknown.”
- A Ready-for-sale tick for Stock plant (does not exist in Access).
- Live rewrite of local plant-list counts on save.

---

## Decisions locked

| Topic | Decision |
|-------|----------|
| Count model | **Absolute editors** (set new totals), not from→to transfer |
| Editable counts | **Tubes, Pots, Misc., Stock plant** |
| Ready for sale | **Three ticks** — Tubes / Pots / Misc. only (Access parity) |
| Stock plant | Propagation source, **not for sale**; editable count; **no** for-sale tick |
| Hanging baskets | Always **Misc.** |
| Tray / cuttings | **New accession**, not Repot |
| Pot-size upsizing | Out of app |
| Multi-number baskets | Rare; one Repot per accession barcode |
| Not found | **Block** — contact database manager |
| All counts zero | Save allowed after **confirm dialog** |
| Everyday case | Tubes → pots (~90%); screen still supports full correction |

---

## Open before implementation

1. Exact Access strings for Herb `Plant Type` / group (for Tubes for-sale default).
2. Export column list final sign-off with backend/Access apply.
3. Whether current `*ForSale` flags should be imported into the plant list later
   (nice-to-have for prefilling ticks; defaults above work without it).

## UX sketch

```
┌─────────────────────────────────┐
│  ← Repot                        │
│                                 │
│  ┌───────────────────────────┐  │
│  │ Acacia pycnantha          │  │
│  │ Accession: 31011          │  │
│  │ Group: Tree               │  │
│  └───────────────────────────┘  │
│  Current: T 12 · P 0            │
│                                 │
│  Tubes        [ − ]  0  [ + ]   │
│  Pots         [ − ] 12  [ + ]   │
│  Misc.        [ − ]  0  [ + ]   │
│  Stock plant  [ − ]  0  [ + ]   │
│                                 │
│  Ready for sale                 │
│  ☐ Tubes   ☑ Pots   ☐ Misc.     │
│                                 │
│  Notes                          │
│  ┌───────────────────────────┐  │
│  │                           │  │
│  └───────────────────────────┘  │
│                                 │
│  [        Save repot         ]  │
└─────────────────────────────────┘
```

(Example: volunteer moved 12 tubes into pots; Pots for sale checked for a Tree.)
