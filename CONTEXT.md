# Plant Scanner

Glossary for the volunteer plant-nursery scanning app. Captures domain terms whose meaning is
not obvious from the code. Implementation details live in `AGENTS.md` and `docs/adr/`, not here.

## Language

**Accession**:
A single batch of a plant in the nursery, identified by a unique number that is also encoded in
its Code 128 barcode. The accession *is* the barcode — there is no separate barcode field. The plant
sheet has no price column; unit price is keyed at sale on the line item only.
_Avoid_: SKU, item code, barcode (as a distinct concept)

**Unknown scan**:
A barcode scan whose code is not in the local plant list. On sell and cull, never dropped —
recorded with `name = "unknown"` and the scanned code kept as the accession for later
reconciliation. Label print requests and repots are different: a missing accession is rejected, not
queued.
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

**Cull**:
A record that plants were discarded from nursery stock (death, disease, surplus, and similar).
_Avoid_: death record, discard, write-off (as the product name)

**Repot**:
A record that sets new absolute nursery counts for one accession (Tubes / Pots / Misc. / Stock plant)
and three Ready-for-sale ticks (T/P/M only). Everyday case is tubes → pots; same flow also corrects
stock discrepancies. Identified by a `repot_id` that uses the receipt-number scheme. Offline-first:
saved locally (`PENDING`), then exported to Sheets. Not for new accessions or tray/cuttings splits.
_Avoid_: transfer, move stock, inventory adjustment (as the product name)

**Item seq**:
The 1-based position of a line item within its receipt. Together with the receipt number it forms
the primary key of a Sales-sheet row — `(receipt, item_seq)` — since one receipt produces one row
per line item and the receipt number alone repeats across them.
_Avoid_: line id, row id (it is not globally unique on its own)

**Device prefix**:
The two-digit per-device code from settings that namespaces local IDs in the shared Sheet (the `PP`
in `PP-<epochSeconds>-<seq>`). Keeps multiple devices from colliding without a central allocator.
_Avoid_: device id, store id

**Receipt number**:
Per-device identifier for a saved sale, formatted `PP-<epochSeconds>-<seq>` where `PP` is the
device prefix and `seq` resets daily. Culls, label print requests, and repots reuse the same
numbering scheme and daily counter (`cull_id` / `queue_id` / `repot_id`). See ADR-0009.
_Avoid_: receipt id (ambiguous with local DB id), order number

**Payment method**:
How the customer paid (cash, card, etc.), recorded on the receipt for nursery records. The app
does not process payments.
_Avoid_: tender, checkout, payment processing

**Label print request**:
A volunteer request to reprint labels for an existing accession (lost or worn labels). Identified by
a `queue_id` that uses the receipt-number scheme. Saved locally first (offline-first), then exported
to the Sheets `PrintQueue` tab and applied into Access `PrintQueue` for NiceLabel. Not for new
accessions or repot/split flows. Unlike sales/culls, a not-found accession is **blocked**
(message: "Please contact database administrator") — never recorded as unknown.
_Avoid_: print job, label order

**Nursery stock total**:
For one accession, the sum of `PotsInNursery + TubesInNursery + MiscInNursery + StockInNursery`.
Used as the per-request cap on how many labels may be requested. Stock corrections stay in Access —
the app does not edit counts.
_Avoid_: available stock (ambiguous with for-sale flags), pot count (implies one pot type only)

**Taxonomic snapshot**:
Genus, species, cultivar, and common name copied onto a line item or cull at save time. Export uses
these frozen fields — it does not re-look up the live plant list. See ADR-0013.
_Avoid_: plant name (ambiguous with composed display name), live lookup

**Sync queue**:
The local `status` column on a receipt, cull, label print request, or repot. Only pending rows are
exported; status flips to exported only after a successful HTTP push. Receipts:
`OPEN` → `SAVED` → `EXPORTED`. Culls, label print requests, and repots: `PENDING` → `EXPORTED`.
See ADR-0006.
_Avoid_: outbox table, sync flag (as a separate concept)

**Cloud sync**:
The single device↔Sheets round trip: export the sync queue, then import the plant list. See
ADR-0007 and ADR-0008.
_Avoid_: export now, update plant list (as separate one-way actions), full sync (ambiguous)

**Full mirror**:
Rewriting the entire plant list in one go (Sheet `Plants` tab from Access, or the device cache from
Sheets) rather than upserting rows. Only accessions with stock > 0 are included (stock-gated).
See ADR-0005.
_Avoid_: incremental sync, upsert, plant delta

**Plant-list fingerprint**:
Opaque string identifying the current plant list as returned by `getPlants` (parsed plant objects).
The server computes and caches it; the device only stores and echoes it on conditional import so an
unchanged list skips download and local replace. See ADR-0016.
_Avoid_: consistent hashing, etag, LastHash (Access push change-detection — different path)

**Export header**:
The ordered column list for a Sheet tab (`Export.HEADER` for Sales, `CullExport.HEADER` for Culls,
`LabelPrintExport.HEADER` for PrintQueue, `RepotExport.HEADER` for Repots). Stable order relied on
by the Apps Script backend — change only with coordinated `core/` and `backend/` updates. See
ADR-0008.
_Avoid_: CSV schema, column mapping (implies flexibility the backend does not have)

**Reverse sync**:
Access applying Pending Sheet rows into the nursery database and marking them Synced. Distinct
from the device's cloud sync. See ADR-0014.
_Avoid_: export, cloud sync (those are the device↔Sheet round trip)
