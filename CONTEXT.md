# Plant Scanner

Glossary for the volunteer plant-nursery scanning app. Captures domain terms whose meaning is
not obvious from the code. Implementation details live in `CLAUDE.md` and `docs/`, not here.

## Language

**Accession**:
A single batch of a plant in the nursery, identified by a unique number that is also encoded in
its Code 128 barcode. The accession *is* the barcode — there is no separate barcode field.
_Avoid_: SKU, item code, barcode (as a distinct concept)

**Stock plant**:
An accession the nursery holds as a mother/propagation source rather than (only) for sale.
Concretely: an accession whose Access `StockInNursery` count is greater than zero. Displayed with
the `St` indicator.
_Avoid_: parent plant, mother stock

**Pot type**:
The physical unit an accession is grown/sold in: **Tubes** (`T`), **Pots** (`P`), or **Misc.**
(`M`). Each accession carries a per-type count (`TubesInNursery`, `PotsInNursery`,
`MiscInNursery`). At sale this becomes the line item's sale unit.
_Avoid_: container, size, sale unit (in the catalogue context)

**Item seq**:
The 1-based position of a line item within its receipt. Together with the receipt number it forms
the primary key of a Sales-sheet row — `(receipt, item_seq)` — since one receipt produces one row
per line item and the receipt number alone repeats across them.
_Avoid_: line id, row id (it is not globally unique on its own)
