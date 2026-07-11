# Repot — update pot counts on an accession

**Date:** 2026-07-11
**Status:** Draft (from brainstorm #86 + answers 2026-07-11)
**Tracker:** GitHub issue #86
**Related:** Cull (#26–#28), Print label (#77), New accession (deferred)

## Goal

Let volunteers correct **Tubes / Pots / Misc.** counts for an accession on the phone
instead of paper, then push the update through Google Sheets into Access. The everyday
case is tubes becoming pots (~90%); the same screen also fixes stock discrepancies.

## Background

Physical events that change pot-type counts:

1. **Tray / cuttings → tubes** — out of scope here; covered by **New accession**.
2. **Tubes → pots** — classic repot (main case).
3. **Tubes and/or pots → hanging baskets** — hanging baskets are always **Misc.**
   Multi-accession (“multi-number”) baskets are rare: each barcode is its own accession;
   the volunteer runs Repot once per accession. No special merge UI.

Same-size pot upsizing does **not** change counts — stay out of the app.

Sell and Cull already move inventory *down*. Repot is the flow that **sets** Tubes /
Pots / Misc. to the values the volunteer sees on the bench (and optionally marks
ready for sale).

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

Rationale (locked with #86): plant list is stock-gated. Zero-stock accessions are
invisible on purpose. Inventing a repot for an unknown accession cannot update Access
safely. Same strictness as Print label (#77); Sell/Cull keep unknown for scan-loss
prevention only.

### 2. Update counts

Layout (top → bottom), large tap targets:

1. **Plant card** — name, accession, group when present (reuse `PlantCard`).
2. **Current stock line** — `PlantStock.summary` (`T n · P n · M n · St n` as applicable).
   **Stock plant (`St`) is display-only** — not editable on this screen.
3. **Three quantity steppers** — **Tubes**, **Pots**, **Misc.**
   - Prefill from the plant list (`tubesInNursery` / `potsInNursery` / `miscInNursery`).
   - Min **0** (unlike Sell/Cull qty min 1). No max.
   - Volunteer may change any combination (transfer *or* discrepancy fix).
4. **Ready for sale** — one checkbox (see defaults below).
5. **Notes** — optional, max 200 characters.
6. Primary button **Save repot**.

#### Validation (`core/`)

- Each of tubes / pots / misc ≥ 0.
- Save enabled only when **something changed** vs the values shown at open:
  any count differs **or** Ready for sale differs from its initial default.
- No unknown / not-found path on this screen.

#### Ready for sale — default rules (`core/`)

One tick in the UI. It maps to **one** Access for-sale flag for the accession’s
**sale unit** (which flag is exported as `ready_for_sale_unit`):

| Sale unit (from **new** counts) | Rule |
|---------------------------------|------|
| **Pots** if new pots > 0 | Default **checked**, except genus Camellia / Rhododendron / Hosta → **unchecked** |
| else **Tubes** if new tubes > 0 | Default **unchecked**, except group is Herb → **checked** |
| else **Misc.** if new misc > 0 | Default **checked** (hanging baskets) |
| else all zero | Default **unchecked**; unit falls back to **Pots** |

- Genus match: case-insensitive exact match on `Plant.genus` against
  `Camellia`, `Rhododendron`, `Hosta`.
- Herb match: case-insensitive — `Plant.group` equals `Herb` or `Herbs`, or contains
  `herb` (confirm exact Access `Plant Type` strings during Access apply work).
- Volunteer may override the tick freely before save.
- Recompute the default when steppers change (if the volunteer has not manually
  overridden yet). Once they toggle the box, keep their choice until they leave
  the screen.

### 3. Success

Mirror Cull success:

- Repot id + plant name (and optionally a one-line “T/P/M → T/P/M” summary).
- **Repot another** → back to scan.
- **Done** → leave the Repot graph.

No edit/delete after save (same as Cull).

### 4. History / Sync (view)

- **View repots** list on History/Sync (search by accession, name, group, notes;
  pending stripe; newest first).
- Pending repots join the combined sync-queue count and **Export now** / auto ticker
  (sales → culls → … → repots, or order locked at implementation).

---

## Data & sync contract

### Local record

| Field | Notes |
|-------|--------|
| `repot_id` | Shared `ReceiptNumbering` (`PP-<epoch>-<seq>`), same daily counter as sales/culls/labels |
| `date` / `createdAt` | Device confirm time |
| Accession + taxonomic snapshot | Same six identity fields as Cull (`name`, genus, species, cultivar, commonName, group) |
| `tubes_before`, `pots_before`, `misc_before` | Snapshot of list counts at save |
| `tubes`, `pots`, `misc` | New absolute counts |
| `ready_for_sale` | Boolean from checkbox |
| `ready_for_sale_unit` | `Pots` / `Tubes` / `Misc` — which Access `*ForSale` flag to set |
| `notes` | Optional |
| `status` | `PENDING` → `EXPORTED` only after HTTP success |

### Export header (proposed — lock with backend before implement)

```
repot_id, date, accession, name, genus, species, cultivar, common_name, group,
tubes_before, pots_before, misc_before,
tubes, pots, misc,
ready_for_sale, ready_for_sale_unit,
notes
```

Backend stamps sheet-only `sync_status` last (same pattern as Culls). Access apply
(later slice) writes absolute `*InNursery` and the matching `*ForSale` flag.

### Cloud sync

Same unified `syncCloud`: drain pending queues then import plants. Local counts on
the device update only after Access has applied the row and the next plant import
mirrors it — volunteers may briefly see old T/P/M until then (acceptable; same as
sales/culls today).

---

## Scope slices

| Slice | What ships |
|-------|------------|
| **App record** | Scan → Update counts → Success; Room + `core/` validation/defaults; View repots; pending in sync chip |
| **Export** | `RepotExport` / `RepotSync` in `core/`; Sheets **Repots** tab; `appendRepots` |
| **Access apply** | Sheets → Access absolute counts + for-sale flag; ledger on `repot_id` |

### Explicitly out of this feature

- New accession / tray pricking / cuttings from stock plants.
- Editing `StockInNursery`.
- Same pot-type size upsizing.
- Multi-barcode basket as one transaction.
- Expanding the Plants export to zero-stock accessions.
- “Repot as unknown.”
- Live rewrite of local plant-list counts on save.

---

## Decisions locked (from #86 answers)

| Topic | Decision |
|-------|----------|
| Hanging baskets | Always **Misc.** |
| Ready for sale | **In-app checkbox** with genus/group defaults above |
| Tray / stock-plant cuttings | **New accession**, not Repot |
| Pot-size upsizing | Out of app |
| Multi-number baskets | Rare; one Repot per accession barcode |
| What volunteer edits | **All three** Tubes / Pots / Misc. amounts (absolute), not only a 1:1 transfer |
| Not found | **Block** — contact database manager |
| Everyday case | Tubes → pots (~90%); screen still supports full correction |

---

## Open before implementation

1. Exact Access strings for Herb `Plant Type` / group (for default rule).
2. When all three new counts are zero: should Save be allowed (clear stock on accession)?
   Proposal: **yes** — discrepancy cleanup; Ready for sale default unchecked.
3. Export column list final sign-off with backend/Access apply.
4. Whether current `*ForSale` flags should be imported into the plant list later
   (nice-to-have; not required for MVP checkbox defaults).

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
│  Tubes     [ − ]  0  [ + ]      │
│  Pots      [ − ] 12  [ + ]      │
│  Misc.     [ − ]  0  [ + ]      │
│                                 │
│  ☑ Ready for sale               │
│                                 │
│  Notes                          │
│  ┌───────────────────────────┐  │
│  │                           │  │
│  └───────────────────────────┘  │
│                                 │
│  [        Save repot         ]  │
└─────────────────────────────────┘
```

(Example: volunteer moved 12 tubes into pots; Ready for sale stays checked for a Tree.)
