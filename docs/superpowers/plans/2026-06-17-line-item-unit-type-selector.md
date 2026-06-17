# Line-item Unit-type Selector (Pots / Tubes / Misc) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the volunteer pick the sale unit (Pots / Tubes / Misc) on the line-item screen, pre-set from the scanned plant's stock counts, and capture that choice in the Sales sheet.

**Architecture:** Plumb the per-accession stock counts (`PotsInNursery` / `TubesInNursery` / `MiscInNursery`), which already exist in the raw `Plants` sheet but are dropped at `parsePlants`, through backend → app → core `Plant`. Add a pure `SaleUnit` type + default-selection function in `core/` (the project's home for error-prone, unit-tested logic), surface a dropdown on the line-item screen, rename the quantity field `pots` → `qty`, and add a `unit` column to the export. Room gets an additive v1→v2 migration; the `pots`→`qty` rename happens at the mapper (minSdk 26 lacks SQLite `RENAME COLUMN`).

**Tech Stack:** Kotlin/JVM (`core/`, JUnit5 via `gradle test`), Google Apps Script + Node test runner (`backend/`), Android Compose Material3 + Room (`app/`, verified via `:app:assembleDebug`).

**Design:** `docs/superpowers/specs/2026-06-17-line-item-unit-type-selector-design.md`

---

## File map

**Create**
- `core/src/main/kotlin/com/nursery/core/SaleUnit.kt` — the unit enum + pure default-selection rule
- `core/src/test/kotlin/com/nursery/core/SaleUnitTest.kt` — default-selection truth table

**Modify — core**
- `core/src/main/kotlin/com/nursery/core/Models.kt` — `Plant` gains counts; `LineItem` `pots`→`qty` + `unit`
- `core/src/main/kotlin/com/nursery/core/Money.kt` — `receiptTotalCents` reads `qty`
- `core/src/main/kotlin/com/nursery/core/PlantBook.kt` — `toLine`/`toUnknownLine` `qty` + `unit`
- `core/src/main/kotlin/com/nursery/core/Export.kt` — `ExportRow` `qty`+`unit`; `HEADER`; row builders
- `core/src/test/kotlin/com/nursery/core/{MoneyTest,PlantBookTest,ExportTest}.kt`

**Modify — backend**
- `backend/shared.js` — `parsePlants` emits the three counts
- `backend/test/logic.test.js` — count assertions + fix the full-object `deepStrictEqual`s

**Modify — app**
- `app/src/main/java/com/nursery/scanner/data/remote/Dtos.kt` — `PlantDto` counts
- `app/src/main/java/com/nursery/scanner/data/remote/SheetsClient.kt` — map counts into `Plant`
- `app/src/main/java/com/nursery/scanner/data/local/entity/PlantEntity.kt` — count columns
- `app/src/main/java/com/nursery/scanner/data/local/entity/LineItemEntity.kt` — `unit` column
- `app/src/main/java/com/nursery/scanner/data/local/Mappers.kt` — `pots`↔`qty` seam + counts + unit
- `app/src/main/java/com/nursery/scanner/data/local/NurseryDatabase.kt` — version 2 + `MIGRATION_1_2`
- `app/src/main/java/com/nursery/scanner/di/AppContainer.kt` — wire the migration
- `app/src/main/java/com/nursery/scanner/ui/sell/SellViewModel.kt` — `LineDraft` + `commitDraft`/`beginEdit`
- `app/src/main/java/com/nursery/scanner/ui/sell/LineItemScreen.kt` — dropdown + "Quantity" stepper
- `app/src/main/java/com/nursery/scanner/ui/sell/CartScreen.kt` — `qty` + unit display
- `app/src/main/java/com/nursery/scanner/ui/receipts/ReceiptDetailScreen.kt` — `qty` + unit display

**Modify — docs**
- `CLAUDE.md` — Room version/migration gotcha + export-header note
- `docs/deploy/backend.md` — redeploy `shared.gs` note

> **Build/test commands** (from `CLAUDE.md`):
> - core: `cd core && gradle test`
> - backend: `node --test backend/test/logic.test.js`
> - app (compile-only verification — no emulator here): `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`

---

## Task 1: Core — `SaleUnit` enum + default-selection rule

**Files:**
- Create: `core/src/main/kotlin/com/nursery/core/SaleUnit.kt`
- Test: `core/src/test/kotlin/com/nursery/core/SaleUnitTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/test/kotlin/com/nursery/core/SaleUnitTest.kt`:

```kotlin
package com.nursery.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SaleUnitTest {

    @Test fun `pots in stock always wins`() {
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = 5, tubes = 5, misc = 5))
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = 1, tubes = 0, misc = 0))
    }

    @Test fun `no pots falls back to misc before tubes`() {
        assertEquals(SaleUnit.MISC, SaleUnit.defaultFor(pots = 0, tubes = 5, misc = 3))
        assertEquals(SaleUnit.MISC, SaleUnit.defaultFor(pots = 0, tubes = 0, misc = 2))
    }

    @Test fun `no pots or misc falls back to tubes`() {
        assertEquals(SaleUnit.TUBES, SaleUnit.defaultFor(pots = 0, tubes = 4, misc = 0))
    }

    @Test fun `all zero falls back to pots`() {
        assertEquals(SaleUnit.POTS, SaleUnit.defaultFor(pots = 0, tubes = 0, misc = 0))
    }

    @Test fun `labels are the sheet and dropdown strings`() {
        assertEquals("Pots", SaleUnit.POTS.label)
        assertEquals("Tubes", SaleUnit.TUBES.label)
        assertEquals("Misc", SaleUnit.MISC.label)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd core && gradle test --tests "com.nursery.core.SaleUnitTest"`
Expected: FAIL — compilation error, `SaleUnit` is unresolved.

- [ ] **Step 3: Write the implementation**

Create `core/src/main/kotlin/com/nursery/core/SaleUnit.kt`:

```kotlin
package com.nursery.core

/**
 * The kind of unit a plant accession is sold in. [label] is shown in the line-item dropdown and is
 * the exact string written to the Sales sheet's "unit" column.
 */
enum class SaleUnit(val label: String) {
    POTS("Pots"),
    TUBES("Tubes"),
    MISC("Misc");

    companion object {
        /**
         * The unit pre-selected for a freshly scanned plant, from its in-nursery stock counts:
         * Pots, unless none in pots; then Misc, unless none; then Tubes, unless none; else Pots.
         * A not-found ("unknown") scan has all-zero counts and therefore defaults to Pots.
         */
        fun defaultFor(pots: Int, tubes: Int, misc: Int): SaleUnit = when {
            pots > 0 -> POTS
            misc > 0 -> MISC
            tubes > 0 -> TUBES
            else -> POTS
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd core && gradle test --tests "com.nursery.core.SaleUnitTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/nursery/core/SaleUnit.kt core/src/test/kotlin/com/nursery/core/SaleUnitTest.kt
git commit -m "Add SaleUnit enum and stock-driven default-selection rule"
```

---

## Task 2: Core — `qty` rename + `unit` on lines, counts on `Plant`, export column

This is one atomic change: renaming `LineItem.pots` → `qty` breaks `Money`, `PlantBook`, `Export` and their tests simultaneously, so they move together and the suite is green only at the end.

**Files:**
- Modify: `core/src/main/kotlin/com/nursery/core/Models.kt`
- Modify: `core/src/main/kotlin/com/nursery/core/Money.kt`
- Modify: `core/src/main/kotlin/com/nursery/core/PlantBook.kt`
- Modify: `core/src/main/kotlin/com/nursery/core/Export.kt`
- Test: `core/src/test/kotlin/com/nursery/core/{MoneyTest,PlantBookTest,ExportTest}.kt`

- [ ] **Step 1: Update the tests to the new API (they will fail to compile first)**

In `core/src/test/kotlin/com/nursery/core/MoneyTest.kt`, replace the two `LineItem(...)` constructions in `receipt total sums lines` (the `pots =` named args become `qty =`):

```kotlin
    @Test fun `receipt total sums lines`() {
        val lines = listOf(
            LineItem(accession = "A", name = "x", qty = 2, unitPriceCents = 500, discountPct = 0),  // 1000
            LineItem(accession = "B", name = "y", qty = 1, unitPriceCents = 1000, discountPct = 10), // 900
        )
        assertEquals(1900, Money.receiptTotalCents(lines))
    }
```

(Leave every `Money.lineTotalCents(pots = ...)` call unchanged — that parameter keeps its name.)

In `core/src/test/kotlin/com/nursery/core/PlantBookTest.kt`, replace the `toLine` and `toUnknownLine` tests:

```kotlin
    @Test fun `toLine carries accession, name, qty and unit`() {
        val plant = book.findByScan("2021-0345")!!
        val line = book.toLine(plant, qty = 2, unitPriceCents = 1500, discountPct = 10, unit = SaleUnit.TUBES)
        assertEquals("2021-0345", line.accession)
        assertEquals("Banksia", line.name)
        assertEquals(2, line.qty)
        assertEquals(1500, line.unitPriceCents)
        assertEquals(10, line.discountPct)
        assertEquals(SaleUnit.TUBES, line.unit)
        assertFalse(PlantBook.isUnknown(line))
    }

    @Test fun `unknown line keeps the scanned code as accession and is named unknown`() {
        val line = PlantBook.toUnknownLine(code = "9999999999999", qty = 1, unitPriceCents = 800, discountPct = 0, unit = SaleUnit.MISC)
        assertEquals("9999999999999", line.accession)
        assertEquals(PlantBook.UNKNOWN_NAME, line.name)
        assertEquals(1, line.qty)
        assertEquals(SaleUnit.MISC, line.unit)
        assertTrue(PlantBook.isUnknown(line))
    }
```

In `core/src/test/kotlin/com/nursery/core/ExportTest.kt`, replace the `receipt` fixture and the `row strings follow header order` test (rename `pots`→`qty`, set an explicit unit on the first line, expect the `unit` cell after `qty`):

```kotlin
    private val receipt = Receipt(
        localId = 1,
        receiptNo = "07-241",
        createdAtEpochMs = createdAt,
        status = ReceiptStatus.SAVED,
        lines = listOf(
            LineItem(accession = "2021-0345", name = "Banksia", qty = 2, unitPriceCents = 1000, discountPct = 10, unit = SaleUnit.TUBES), // 1800
            LineItem(accession = "9999999999999", name = "unknown", qty = 1, unitPriceCents = 500, discountPct = 0), // 500, unit defaults POTS
        ),
    )
```

```kotlin
    @Test fun `row strings follow header order`() {
        val rows = Export.buildRows(listOf(receipt), ZoneOffset.UTC)
        assertEquals(
            listOf("07-241", "2026-06-09", "2021-0345", "Banksia", "2", "Tubes", "10.00", "10", "18.00"),
            Export.rowAsStrings(rows[0]),
        )
        // unknown line: the scanned code lives in the accession column, name = "unknown", unit defaults to Pots
        assertEquals(
            listOf("07-241", "2026-06-09", "9999999999999", "unknown", "1", "Pots", "5.00", "0", "5.00"),
            Export.rowAsStrings(rows[1]),
        )
    }
```

(`one row per line item...` and `header and row have the same width` need no edits — they assert totals/date and a size match.)

- [ ] **Step 2: Run the suite to verify it fails**

Run: `cd core && gradle test`
Expected: FAIL — compilation errors (`qty`/`unit` unresolved on `LineItem`, `toLine` signature mismatch).

- [ ] **Step 3: Update `Models.kt`**

Replace the `Plant` and `LineItem` data classes in `core/src/main/kotlin/com/nursery/core/Models.kt`:

```kotlin
data class Plant(
    val accession: String,
    val name: String,
    val group: String?,
    val light: String?,
    val potsInNursery: Int = 0,
    val tubesInNursery: Int = 0,
    val miscInNursery: Int = 0,
)
```

```kotlin
/**
 * One line on a receipt. [accession] is the scanned/typed value (== the barcode). When it matched a
 * plant, [name] is that plant's name; for a not-found "sell as unknown" line (decision #7) the same
 * scanned [accession] is kept and [name] is "unknown". [qty] is the count of [unit]s (Pots/Tubes/Misc).
 * Unit price is always keyed at sale (decision #6); discount is a percentage 0..100 (decision #5).
 */
data class LineItem(
    val accession: String,
    val name: String,
    val qty: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val unit: SaleUnit = SaleUnit.POTS,
)
```

- [ ] **Step 4: Update `Money.kt`**

In `core/src/main/kotlin/com/nursery/core/Money.kt`, change `receiptTotalCents` to read `qty`:

```kotlin
    fun receiptTotalCents(lines: List<LineItem>): Long =
        lines.sumOf { lineTotalCents(it.qty, it.unitPriceCents, it.discountPct) }
```

(`lineTotalCents`'s `pots` parameter is unchanged — it is just a count.)

- [ ] **Step 5: Update `PlantBook.kt`**

In `core/src/main/kotlin/com/nursery/core/PlantBook.kt`, replace `toLine` and `toUnknownLine`:

```kotlin
    /** Build a line for a found plant. */
    fun toLine(plant: Plant, qty: Int, unitPriceCents: Long, discountPct: Int, unit: SaleUnit = SaleUnit.POTS): LineItem =
        LineItem(
            accession = plant.accession,
            name = plant.name,
            qty = qty,
            unitPriceCents = unitPriceCents,
            discountPct = discountPct,
            unit = unit,
        )
```

```kotlin
        /** Build a "sell as unknown" line: keep the scanned code as the accession, name = "unknown". */
        fun toUnknownLine(code: String, qty: Int, unitPriceCents: Long, discountPct: Int, unit: SaleUnit = SaleUnit.POTS): LineItem =
            LineItem(
                accession = code,
                name = UNKNOWN_NAME,
                qty = qty,
                unitPriceCents = unitPriceCents,
                discountPct = discountPct,
                unit = unit,
            )
```

- [ ] **Step 6: Update `Export.kt`**

In `core/src/main/kotlin/com/nursery/core/Export.kt`, replace `ExportRow`, `HEADER`, `buildRows` and `rowAsStrings`:

```kotlin
/** One spreadsheet row = one line item of one receipt. Includes the transaction date (per spec). */
data class ExportRow(
    val receiptNo: String,
    val isoDate: String,
    val accession: String,
    val name: String,
    val qty: Int,
    val unit: SaleUnit,
    val unitPriceCents: Long,
    val discountPct: Int,
    val lineTotalCents: Long,
)
```

```kotlin
    /** Column order written to the Sheet — keep stable; the Apps Script relies on it. */
    val HEADER: List<String> = listOf(
        "receipt", "date", "accession", "name",
        "qty", "unit", "unit_price", "discount_pct", "line_total",
    )

    fun buildRows(receipts: List<Receipt>, zone: ZoneId): List<ExportRow> =
        receipts.flatMap { receipt ->
            val date = Instant.ofEpochMilli(receipt.createdAtEpochMs).atZone(zone).toLocalDate().toString()
            receipt.lines.map { line ->
                ExportRow(
                    receiptNo = receipt.receiptNo,
                    isoDate = date,
                    accession = line.accession,
                    name = line.name,
                    qty = line.qty,
                    unit = line.unit,
                    unitPriceCents = line.unitPriceCents,
                    discountPct = line.discountPct,
                    lineTotalCents = Money.lineTotalCents(line.qty, line.unitPriceCents, line.discountPct),
                )
            }
        }

    /** Row as plain strings in [HEADER] order; prices as plain decimals so Sheets treats them as numbers. */
    fun rowAsStrings(row: ExportRow): List<String> = listOf(
        row.receiptNo,
        row.isoDate,
        row.accession,
        row.name,
        row.qty.toString(),
        row.unit.label,
        Money.formatPlain(row.unitPriceCents),
        row.discountPct.toString(),
        Money.formatPlain(row.lineTotalCents),
    )
```

- [ ] **Step 7: Run the full core suite to verify it passes**

Run: `cd core && gradle test`
Expected: PASS (all classes — `MoneyTest`, `PlantBookTest`, `ExportTest`, `SaleUnitTest`, plus the untouched `SyncTest`/`DeviceConfigTest`/`PlantSearchTest`/`ReceiptNumberingTest`).

- [ ] **Step 8: Commit**

```bash
git add core/src/main/kotlin/com/nursery/core/Models.kt core/src/main/kotlin/com/nursery/core/Money.kt core/src/main/kotlin/com/nursery/core/PlantBook.kt core/src/main/kotlin/com/nursery/core/Export.kt core/src/test/kotlin/com/nursery/core
git commit -m "Core: rename line qty, add SaleUnit to lines/export, add stock counts to Plant"
```

---

## Task 3: Backend — `parsePlants` carries the stock counts

**Files:**
- Modify: `backend/shared.js`
- Test: `backend/test/logic.test.js`

- [ ] **Step 1: Update existing full-object assertions + add count tests (will fail first)**

In `backend/test/logic.test.js`, the three `deepStrictEqual` checks on a whole plant object must include the new keys (those test headers have no count columns, so all default to 0).

In `parsePlants maps by header name and skips blank accessions`:

```js
  assert.deepStrictEqual(plants[0], {
    accession: '2021-0345', name: 'Banksia', group: 'Proteaceae', light: 'Full sun',
    potsInNursery: 0, tubesInNursery: 0, miscInNursery: 0,
  });
```

In `parsePlants reads the raw Batches+Species view and composes name`:

```js
  assert.deepStrictEqual(plants[0], {
    accession: '31011', name: 'Acacia pycnantha', group: 'Tree', light: 'Full sun',
    potsInNursery: 0, tubesInNursery: 0, miscInNursery: 0,
  });
```

In `parsePlants name falls back to Common Name, and legacy headers still work` (the legacy `deepStrictEqual`):

```js
  const legacy = parsePlants([['accession', 'name', 'group', 'light'], ['2021-1', 'Wattle', 'Tree', 'Sun']]);
  assert.deepStrictEqual(legacy[0], {
    accession: '2021-1', name: 'Wattle', group: 'Tree', light: 'Sun',
    potsInNursery: 0, tubesInNursery: 0, miscInNursery: 0,
  });
```

Add two new tests at the end of the file (before the final line is fine):

```js
test('parsePlants reads the per-accession stock counts (Nz -> 0)', () => {
  const values = [
    ['Ac Number', 'Genus', 'Species', 'PotsInNursery', 'TubesInNursery', 'MiscInNursery'],
    ['31011', 'Acacia', 'pycnantha', 5, 2, 0],
    ['8250', 'Banksia', 'integrifolia', '', '', ''], // blanks -> 0
  ];
  const plants = parsePlants(values);
  assert.strictEqual(plants[0].potsInNursery, 5);
  assert.strictEqual(plants[0].tubesInNursery, 2);
  assert.strictEqual(plants[0].miscInNursery, 0);
  assert.strictEqual(plants[1].potsInNursery, 0);
  assert.strictEqual(plants[1].tubesInNursery, 0);
  assert.strictEqual(plants[1].miscInNursery, 0);
});

test('parsePlants defaults stock counts to 0 when the columns are absent', () => {
  const plants = parsePlants([['Ac Number', 'Genus'], ['9', 'Grevillea']]);
  assert.strictEqual(plants[0].potsInNursery, 0);
  assert.strictEqual(plants[0].tubesInNursery, 0);
  assert.strictEqual(plants[0].miscInNursery, 0);
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `node --test backend/test/logic.test.js`
Expected: FAIL — `deepStrictEqual` mismatches (missing count keys) and the two new tests fail.

- [ ] **Step 3: Implement the counts in `parsePlants`**

In `backend/shared.js`, inside `parsePlants`, add the count column indices after the existing `iLight` line:

```js
  var iLight = col('light', 'sun/shade');
  var iPots = col('potsinnursery');
  var iTubes = col('tubesinnursery');
  var iMisc = col('miscinnursery');
```

Add a numeric reader next to the existing `get`/`nameOf` helpers:

```js
  function num(row, idx) {
    if (idx < 0) return 0;
    var v = row[idx];
    if (v === '' || v === null || v === undefined) return 0;
    var n = Number(v);
    return isNaN(n) ? 0 : n;
  }
```

And extend the pushed object:

```js
    out.push({
      accession: accession,
      name: nameOf(row),
      group: iGroup >= 0 ? emptyToNull(row[iGroup]) : null,
      light: iLight >= 0 ? emptyToNull(row[iLight]) : null,
      potsInNursery: num(row, iPots),
      tubesInNursery: num(row, iTubes),
      miscInNursery: num(row, iMisc)
    });
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `node --test backend/test/logic.test.js`
Expected: PASS (all tests).

- [ ] **Step 5: Commit**

```bash
git add backend/shared.js backend/test/logic.test.js
git commit -m "Backend: parsePlants emits per-accession stock counts for the app"
```

---

## Task 4: App — plumb counts, unit dropdown, qty rename, Room migration

The `core` `pots`→`qty` rename breaks every `app` reference at once, and `app` has no JVM unit tests, so all app edits land together and are verified by a single `:app:assembleDebug`. **Do this task after Tasks 1–2 are committed.**

**Files:** (all under `app/src/main/java/com/nursery/scanner/`)
- Modify: `data/remote/Dtos.kt`, `data/remote/SheetsClient.kt`
- Modify: `data/local/entity/PlantEntity.kt`, `data/local/entity/LineItemEntity.kt`
- Modify: `data/local/Mappers.kt`, `data/local/NurseryDatabase.kt`, `di/AppContainer.kt`
- Modify: `ui/sell/SellViewModel.kt`, `ui/sell/LineItemScreen.kt`, `ui/sell/CartScreen.kt`
- Modify: `ui/receipts/ReceiptDetailScreen.kt`

- [ ] **Step 1: `PlantDto` gains the counts**

In `data/remote/Dtos.kt`, replace `PlantDto`:

```kotlin
@Serializable
data class PlantDto(
    val accession: String,
    val name: String,
    val group: String? = null,
    val light: String? = null,
    val potsInNursery: Int = 0,
    val tubesInNursery: Int = 0,
    val miscInNursery: Int = 0,
)
```

- [ ] **Step 2: Map the counts in `SheetsClient.fetchPlants`**

In `data/remote/SheetsClient.kt`, replace the `resp.plants.map { ... }` body:

```kotlin
            resp.plants.map {
                Plant(
                    accession = it.accession,
                    name = it.name,
                    group = it.group,
                    light = it.light,
                    potsInNursery = it.potsInNursery,
                    tubesInNursery = it.tubesInNursery,
                    miscInNursery = it.miscInNursery,
                )
            }
```

- [ ] **Step 3: `PlantEntity` gains the count columns**

In `data/local/entity/PlantEntity.kt`, replace the data class:

```kotlin
@Entity(tableName = "plants")
data class PlantEntity(
    // accession == the Code 128 barcode value; there is no separate barcode field.
    @PrimaryKey val accession: String,
    val name: String,
    // "group" is reserved in SQLite; store under a safe column name.
    @ColumnInfo(name = "plant_group") val group: String?,
    val light: String?,
    val potsInNursery: Int = 0,
    val tubesInNursery: Int = 0,
    val miscInNursery: Int = 0,
)
```

- [ ] **Step 4: `LineItemEntity` gains the `unit` column**

In `data/local/entity/LineItemEntity.kt`, add `unit` to the data class (keep the physical column `pots` — the rename happens at the mapper):

```kotlin
data class LineItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val accession: String,
    val name: String,
    val pots: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val unit: String = "POTS",
)
```

- [ ] **Step 5: Mappers — the `pots`↔`qty` seam + counts + unit**

In `data/local/Mappers.kt`, add the import and replace the Plant + LineItem mappers:

```kotlin
import com.nursery.core.SaleUnit
```

```kotlin
fun PlantEntity.toCore(): Plant =
    Plant(
        accession = accession, name = name, group = group, light = light,
        potsInNursery = potsInNursery, tubesInNursery = tubesInNursery, miscInNursery = miscInNursery,
    )

fun Plant.toEntity(): PlantEntity =
    PlantEntity(
        accession = accession, name = name, group = group, light = light,
        potsInNursery = potsInNursery, tubesInNursery = tubesInNursery, miscInNursery = miscInNursery,
    )
```

```kotlin
fun LineItemEntity.toCore(): LineItem =
    LineItem(
        accession = accession,
        name = name,
        qty = pots,
        unitPriceCents = unitPriceCents,
        discountPct = discountPct,
        unit = runCatching { SaleUnit.valueOf(unit) }.getOrDefault(SaleUnit.POTS),
    )

fun LineItem.toEntity(receiptId: Long): LineItemEntity =
    LineItemEntity(
        receiptId = receiptId,
        accession = accession,
        name = name,
        pots = qty,
        unitPriceCents = unitPriceCents,
        discountPct = discountPct,
        unit = unit.name,
    )
```

(`runCatching` guards against an unexpected stored string — a defaulted/legacy row reads back as `POTS`.)

- [ ] **Step 6: Bump the DB version and define the migration**

In `data/local/NurseryDatabase.kt`, set `version = 2` and add the migration. New file body:

```kotlin
package com.nursery.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nursery.scanner.data.local.dao.PlantDao
import com.nursery.scanner.data.local.dao.ReceiptDao
import com.nursery.scanner.data.local.entity.LineItemEntity
import com.nursery.scanner.data.local.entity.PlantEntity
import com.nursery.scanner.data.local.entity.ReceiptEntity

@Database(
    entities = [PlantEntity::class, ReceiptEntity::class, LineItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NurseryDatabase : RoomDatabase() {
    abstract fun plantDao(): PlantDao
    abstract fun receiptDao(): ReceiptDao

    companion object {
        const val NAME = "nursery.db"

        /** v1 -> v2: add per-accession stock counts to plants and the sale-unit to line items (additive). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN potsInNursery INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plants ADD COLUMN tubesInNursery INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE plants ADD COLUMN miscInNursery INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE line_items ADD COLUMN unit TEXT NOT NULL DEFAULT 'POTS'")
            }
        }
    }
}
```

- [ ] **Step 7: Wire the migration into the Room builder**

In `di/AppContainer.kt`, replace the `db` builder:

```kotlin
    private val db = Room.databaseBuilder(
        context.applicationContext,
        NurseryDatabase::class.java,
        NurseryDatabase.NAME,
    ).addMigrations(NurseryDatabase.MIGRATION_1_2).build()
```

- [ ] **Step 8: `LineDraft` carries counts + selected unit**

In `ui/sell/SellViewModel.kt`, add the import and replace the `LineDraft` class:

```kotlin
import com.nursery.core.SaleUnit
```

```kotlin
data class LineDraft(
    val accession: String,
    val name: String,
    val group: String?,
    val light: String?,
    val isUnknown: Boolean,
    val qty: Int,
    val unitPriceCents: Long,
    val discountPct: Int,
    val unit: SaleUnit,
    val potsInNursery: Int,
    val tubesInNursery: Int,
    val miscInNursery: Int,
    val editIndex: Int?,
) {
    companion object {
        fun fromPlant(p: Plant) = LineDraft(
            accession = p.accession, name = p.name, group = p.group, light = p.light,
            isUnknown = false, qty = 1, unitPriceCents = 0, discountPct = 0,
            unit = SaleUnit.defaultFor(p.potsInNursery, p.tubesInNursery, p.miscInNursery),
            potsInNursery = p.potsInNursery, tubesInNursery = p.tubesInNursery, miscInNursery = p.miscInNursery,
            editIndex = null,
        )

        fun unknown(code: String) = LineDraft(
            accession = code, name = PlantBook.UNKNOWN_NAME, group = null, light = null,
            isUnknown = true, qty = 1, unitPriceCents = 0, discountPct = 0,
            unit = SaleUnit.POTS, potsInNursery = 0, tubesInNursery = 0, miscInNursery = 0,
            editIndex = null,
        )
    }
}
```

- [ ] **Step 9: `commitDraft` and `beginEdit` handle the unit**

In `ui/sell/SellViewModel.kt`, replace `commitDraft`:

```kotlin
    /** ② Commit the line-item form (add new, or replace when editing). */
    fun commitDraft(qty: Int, unitPriceCents: Long, discountPct: Int, unit: SaleUnit) {
        val d = _ui.value.draft ?: return
        val line = LineItem(
            accession = d.accession,
            name = d.name,
            qty = qty,
            unitPriceCents = unitPriceCents,
            discountPct = discountPct,
            unit = unit,
        )
        val lines = _ui.value.lines.toMutableList()
        if (d.editIndex != null && d.editIndex in lines.indices) lines[d.editIndex] = line else lines.add(line)
        _ui.update { it.copy(lines = lines, draft = null, notFoundCode = null) }
    }
```

And replace the `draft = LineDraft(...)` construction inside `beginEdit` (re-enrich counts from the book, preserve the saved unit and qty):

```kotlin
        _ui.update {
            it.copy(
                draft = LineDraft(
                    accession = line.accession,
                    name = line.name,
                    group = plant?.group,
                    light = plant?.light,
                    isUnknown = PlantBook.isUnknown(line),
                    qty = line.qty,
                    unitPriceCents = line.unitPriceCents,
                    discountPct = line.discountPct,
                    unit = line.unit,
                    potsInNursery = plant?.potsInNursery ?: 0,
                    tubesInNursery = plant?.tubesInNursery ?: 0,
                    miscInNursery = plant?.miscInNursery ?: 0,
                    editIndex = index,
                ),
            )
        }
```

- [ ] **Step 10: Line-item screen — dropdown + "Quantity" stepper**

In `ui/sell/LineItemScreen.kt`, add these imports:

```kotlin
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import com.nursery.core.SaleUnit
```

Replace the quantity-state line (was `var pots ...`):

```kotlin
    var qty by remember(draft) { mutableIntStateOf(draft.qty.coerceAtLeast(1)) }
    var unit by remember(draft) { mutableStateOf(draft.unit) }
```

Replace the "Pots stepper" block (the `Text("Pots", ...)` + its `Row`) with the dropdown followed by a "Quantity" stepper:

```kotlin
            UnitDropdown(selected = unit, onSelect = { unit = it })

            // Quantity stepper
            Text("Quantity", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) {
                FilledTonalIconButton(onClick = { if (qty > 1) qty-- }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Remove, contentDescription = "One fewer")
                }
                Text("$qty", style = MaterialTheme.typography.displaySmall)
                FilledTonalIconButton(onClick = { qty++ }, modifier = Modifier.size(64.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "One more")
                }
            }
```

Update the line-total calculation and the commit call to use `qty`/`unit`:

```kotlin
    val lineTotal = Money.lineTotalCents(qty, unitPriceCents, discountPct)
```

```kotlin
            BigButton(
                text = if (draft.editIndex != null) "Save changes" else "Add to receipt",
                onClick = {
                    vm.commitDraft(qty = qty, unitPriceCents = unitPriceCents, discountPct = discountPct, unit = unit)
                    onAdded()
                },
            )
```

Add the dropdown composable at the bottom of the file (after `LineItemScreen`):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitDropdown(selected: SaleUnit, onSelect: (SaleUnit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Unit") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SaleUnit.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, style = MaterialTheme.typography.bodyLarge) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
```

(`menuAnchor` and `ExposedDropdownMenu` are members of the `ExposedDropdownMenuBoxScope` receiver — no extra import. If `assembleDebug` reports them unresolved on this Material3 version, import `androidx.compose.material3.menuAnchor` / `androidx.compose.material3.ExposedDropdownMenu`.)

- [ ] **Step 11: Cart row — `qty` + show the unit**

In `ui/sell/CartScreen.kt`, update `LineRow`:

```kotlin
    val total = Money.lineTotalCents(line.qty, line.unitPriceCents, line.discountPct)
```

```kotlin
                Text(
                    "${line.qty} ${line.unit.label} × ${Money.formatAud(line.unitPriceCents)}$discountLabel",
                    style = MaterialTheme.typography.bodyMedium,
                )
```

- [ ] **Step 12: Receipt detail — `qty` + show the unit**

In `ui/receipts/ReceiptDetailScreen.kt`:

```kotlin
                val total = Money.lineTotalCents(line.qty, line.unitPriceCents, line.discountPct)
```

```kotlin
                        Text(
                            "${line.qty} ${line.unit.label} × ${Money.formatAud(line.unitPriceCents)}$disc",
                            style = MaterialTheme.typography.bodyMedium,
                        )
```

- [ ] **Step 13: Assemble the app to verify it compiles**

Run: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`. (Fix any unresolved-reference errors surfaced — most likely a missed `pots`→`qty` site or a dropdown import.)

- [ ] **Step 14: Commit**

```bash
git add app/src
git commit -m "App: unit-type dropdown on line item, plumb stock counts, qty rename, Room v2 migration"
```

---

## Task 5: Docs — update gotchas and deploy note

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/deploy/backend.md`

- [ ] **Step 1: Update the Room gotcha in `CLAUDE.md`**

Replace the `**Room**` bullet under "app/ structure notes":

```markdown
- **Room** is at `version = 2` (`NurseryDatabase.MIGRATION_1_2`: additive — plant stock counts +
  `line_items.unit`). The plant list is replaced wholesale on "Update plant list".
```

- [ ] **Step 2: Update the export-header rule in `CLAUDE.md`**

Replace the `**Export column order**` bullet under "Domain rules that span multiple files":

```markdown
- **Export column order** (`Export.HEADER`) is relied on by the Apps Script backend — keep it stable.
  Columns: `receipt, date, accession, name, qty, unit, unit_price, discount_pct, line_total`. `qty` is
  the count of `unit`s (Pots/Tubes/Misc); `unit` is the sale-unit chosen on the line-item screen,
  defaulted from the plant's `*InNursery` counts via `SaleUnit.defaultFor`.
```

- [ ] **Step 3: Add a redeploy note in `docs/deploy/backend.md`**

Append a short note so the next deploy carries the stock counts (place it near the existing redeploy guidance):

```markdown
> **2026-06-17:** `shared.js`/`shared.gs` `parsePlants` now also returns `potsInNursery` /
> `tubesInNursery` / `miscInNursery`. Redeploy the web app, then on each device tap **"Update plant
> list"** once so the cache repulls with the stock counts (they drive the Pots/Tubes/Misc default on
> the line-item screen). The `Sales` sheet now has a `unit` column after `qty`.
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/deploy/backend.md
git commit -m "Docs: note Room v2 migration, qty/unit export columns, shared.gs redeploy"
```

---

## Self-review

**Spec coverage:**
- Dropdown Pots/Tubes/Misc on the line-item screen → Task 4 Step 10.
- Default-selection rule (Pots→Misc→Tubes→Pots) → Task 1 (`SaleUnit.defaultFor`), wired in Task 4 Step 8 (`fromPlant`).
- Unknown line shows dropdown defaulting to Pots → Task 4 Step 8 (`unknown` sets `SaleUnit.POTS`).
- Stepper relabeled "Quantity" → Task 4 Step 10.
- Selection captured in the Sales sheet (`unit` after `qty`) → Task 2 Step 6 (`HEADER`/`rowAsStrings`).
- `pots`→`qty` rename → Task 2 (core), Task 4 (entity stays `pots`, mapper translates, UI uses `qty`).
- Stock counts plumbed (sheet → app) → Task 3 (backend), Task 4 Steps 1–3, 5 (DTO/entity/mapper).
- Additive Room v1→v2 migration → Task 4 Steps 6–7.
- Backend redeploy + "Update plant list" → Task 5 Step 3.

**Placeholder scan:** none — every code/step is concrete.

**Type consistency:** `SaleUnit { POTS, TUBES, MISC }` + `label` + `defaultFor(pots, tubes, misc)` used identically across tasks; `LineItem.qty`/`LineItem.unit`, `ExportRow.qty`/`ExportRow.unit`, `LineDraft.qty`/`LineDraft.unit`, `commitDraft(qty, unitPriceCents, discountPct, unit)`, and the `LineItemEntity.pots` physical column ↔ `qty` mapper seam are consistent throughout. `HEADER` order matches `rowAsStrings` order in Task 2.
