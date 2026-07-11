# Plant Scanner

Glossary for the volunteer plant-nursery scanning app. Captures domain terms whose meaning is
not obvious from the code. Implementation details live in `AGENTS.md` and `docs/`, not here.

## Language

**Accession**:
A single batch of a plant in the nursery, identified by a unique number that is also encoded in
its Code 128 barcode. The accession *is* the barcode — there is no separate barcode field. The plant
sheet has no price column; unit price is keyed at sale on the line item only.
_Avoid_: SKU, item code, barcode (as a distinct concept)

**Unknown scan**:
A barcode scan whose code is not in the local plant list. On sell and cull, never dropped —
recorded with `name = "unknown"` and the scanned code kept as the accession for later
reconciliation. Label print requests are different: a missing accession is rejected, not queued.
_Avoid_: invalid scan, rejected scan

**Stock plant**:
An accession the nursery holds as a mother/propagation source rather than (only) for sale.
Concretely: an accession whose Access `StockInNursery` count is greater than zero. Displayed with
the `St` indicator.
_Avoid_: parent plant, mother stock

**Pot type**:
The physical unit an accession is grown/sold in: **Tubes** (`T`), **Pots** (`P`), or **Misc.**
(`M`). Each accession carries a per-type count (`TubesInNursery`, `PotsInNursery`,
`MiscInNursery`). At sale or cull this becomes the line's unit.
_Avoid_: container, size, sale unit (in the catalogue context)

**Item seq**:
The 1-based position of a line item within its receipt. Together with the receipt number it forms
the primary key of a Sales-sheet row — `(receipt, item_seq)` — since one receipt produces one row
per line item and the receipt number alone repeats across them.
_Avoid_: line id, row id (it is not globally unique on its own)

**Receipt number**:
Per-device identifier for a saved sale, formatted `PP-<epochSeconds>-<seq>` where `PP` is the
two-digit device prefix from settings and `seq` resets daily. Culls and label print requests reuse
the same numbering scheme and daily counter. See `ReceiptNumbering.kt`.
_Avoid_: receipt id (ambiguous with local DB id), order number

**Label print request**:
A volunteer request to reprint labels for an existing accession (lost or worn labels). Identified by
a `queue_id` that uses the receipt-number scheme. Not for new accessions or repot/split flows.
_Avoid_: print job, label order

**Repot**:
A volunteer update of an accession’s Tubes / Pots / Misc. / Stock plant counts (absolute values),
commonly tubes becoming pots, plus three Ready for sale ticks (Tubes / Pots / Misc. only — stock
plants are never for sale). Recorded locally and exported like culls; Access applies stock.
Not-found accessions are blocked (contact database manager). Tray/cuttings belong under New
accession. Hanging baskets use Misc. See
`docs/superpowers/specs/2026-07-11-repot-stock-update-design.md`.
_Avoid_: transfer-only, inventory adjust (as a separate concept), restock

**Nursery stock total**:
For one accession, the sum of `PotsInNursery + TubesInNursery + MiscInNursery + StockInNursery`.
Used as the per-request cap on how many labels may be requested.
_Avoid_: available stock (ambiguous with for-sale flags), pot count (implies one pot type only)

**Sync queue**:
The local `status` column on a receipt, cull, or label print request. Only pending rows are
exported; status flips to exported only after a successful HTTP push. Receipts:
`OPEN` → `SAVED` → `EXPORTED`. Culls and label print requests: `PENDING` → `EXPORTED`.
_Avoid_: outbox table, sync flag (as a separate concept)

**Cloud sync**:
The single device↔Sheets round trip: export the sync queue (pending sales, then pending culls),
then import the plant list. History ↻, Plants ↻, and the background ticker all run this same
sequence via `SyncRepository.syncCloud`. Import still runs when export fails or the queue is empty;
both steps are skipped only when the device is not configured. See
`docs/superpowers/specs/2026-07-10-unified-cloud-sync-design.md` and `CloudSync`.
_Avoid_: export now, update plant list (as separate one-way actions), full sync (ambiguous)

**Export header**:
The ordered column list for a Sheet tab (`Export.HEADER` for Sales, `CullExport.HEADER` for Culls,
and the PrintQueue export header for label print requests). Stable order relied on by the Apps
Script backend — change only with coordinated `core/` and `backend/` updates.
_Avoid_: CSV schema, column mapping (implies flexibility the backend does not have)
