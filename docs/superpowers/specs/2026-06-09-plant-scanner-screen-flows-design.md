# Plant Nursery App — Screen-Flow Design

**Date:** 2026-06-09
**Status:** Phase 1 flows approved · Phase 2 sketched (deferred)
**Source spec:** `Mobile Application for Plant Nursery.md`

## Scope

Design the screen-flows for an Android app for the Royal Botanic Gardens Melbourne
volunteer nursery. **Phase 1 designed in depth; Phase 2 laid out only far enough to
prove the navigation shell holds it** (the user chose option B in brainstorming).

Phase 1 processes: **Sell** · **Sync plant list** · **Export sales**.
Phase 2 processes (deferred): New accession · Print label · Repot · Record death.

## Audience & accessibility (elderly volunteers)

The volunteers using this app are **elderly**. Accessibility is a **primary design
constraint across every screen** — Phase 1 and Phase 2 — not a later polish pass.

- **Big touch targets.** Minimum ~56dp tap targets; primary actions (Sell, Scan,
  Add to receipt, Finish & save) noticeably larger. Generous spacing so neighbouring
  buttons are never mis-tapped.
- **Big, legible text.** Large base font; respect the OS font-scale setting (never
  cap or override it). Body text no smaller than ~18sp; the numbers that matter
  (Total, Line total) larger still.
- **High contrast, clear layout.** Strong colour contrast (WCAG AA minimum), one
  primary action per screen, lots of whitespace, no clutter. Icons are always paired
  with a text label — never icon-only.
- **No flickering.** No flashing, blinking, or rapid-flash content (WCAG 2.3.1).
  Stable layouts that don't jump/reflow as data loads; a steady scanner viewfinder;
  calm spinners and toasts, not jarring ones.
- **Tap, don't gesture.** Every action is a visible, labelled button. Avoid
  swipe / long-press / pinch. Specifically **no swipe-to-delete** — removing a line is
  *tap line → explicit Remove button* (as drawn in the Sale flow). If a gesture is ever
  truly unavoidable, it must have an equivalent on-screen button.
- **Clear feedback & forgiveness.** Obvious pressed-state on every tap; confirm
  destructive actions; no auto-dismiss timeouts that rush the user.

## Decisions locked during brainstorming

| # | Decision | Choice |
|---|----------|--------|
| 1 | Home model | **Action-first** — home is an action grid; "Sell" opens a scan-driven receipt builder |
| 2 | Connectivity | **Offline-first** — selling works fully offline; **sales auto-export to Sheets when online** (plant-list update stays a manual pull) |
| 3 | Payment | **Not captured** — app records the sale + total only; cash/card handled outside the app |
| 4 | Receipt model | **Receipt = one customer** — one "Sell" tap opens one receipt; scan every plant they buy into it |
| 5 | Discount | **Per line item**, a **percentage** (changed from the source spec's flat-dollar Discount field) |
| 6 | Unit price | **Always keyed manually at sale** — the plant sheet carries **no price column**, so there is no pre-fill |
| 7 | Not-found scan | **Store the scanned barcode/accession on the line and sell as "unknown"** — recoverable later, never lost |
| 8 | Export destination | **Google Sheets only** — email removed from the spec |
| 9 | Export/Update UX | **One tap → progress → Done/Error**, no confirm step (manual actions only) |
| 10 | Auto-export | **Pending sales push automatically every N min when online** — pre-configured, **default 1 min**, silent on success *and* failure; manual "Export now" remains |
| 11 | Receipt number | **Purely local, namespaced per device** — each device has a configured **2-digit prefix**; receipt # = `PP-NNN` (e.g. `07-241`). Prefixes guarantee uniqueness across devices in the shared Sheet; no central coordination |
| 12 | Barcode = accession | **The label's Code 128 encodes the plant's accession number** — barcode and accession are one value; there is no separate barcode field. Scanning and typing both yield the accession |

## Navigation shell

Action-first home, a persistent sync/offline status chip, and a 3-tab bottom bar.

```
┌──────────────────────────────────────┐
│ Nursery        ⚠ Offline·3          │  ← top bar: status chip (Synced 2h ago / Offline·N pending)
├──────────────────────────────────────┤
│   [ 💰  Sell plants      ]          │  ← primary action
│   [🆕 Accession][🏷️ Label]          │  ← Phase 2 (dimmed in Phase 1)
│   [🪴 Repot   ][💀 Death ]          │
├──────────────────────────────────────┤
│  ⌗ Actions │ 🧾 Receipts │ 🔄 Sync │  ← bottom nav
└──────────────────────────────────────┘
```

### Screen inventory

- **Actions tab:** Home / action grid → each action launches its own task flow.
- **Receipts tab:** Sales history list (grouped by receipt) → Receipt detail (line items, total).
- **Sync tab:** Plant-list status + "Update plant list"; "Export to Google Sheets".
- **Global/persistent:** offline + pending-count chip in the top bar; toasts ("Saved locally", "Synced").

## Sale flow (Phase 1 — primary)

```
Home ─[Sell]→ New receipt 07-241 opened (# = «device prefix»-«local seq», generated locally)
   │
   ▼
① Scan barcode  ──(or)──  type accession #   (the barcode IS the accession — same value)
   │
   ├─ found ───────────────► ② Line item
   └─ not found ───────────► sell as "unknown" (store scanned #, name "unknown") ► ② Line item

② Line item: plant name / group auto-filled
   inputs: Pots · Unit price (keyed each time — no price in sheet) · Discount %
   live Line total = pots × price × (1 − discount%)
   │ [Add to receipt]
   ▼
③ Receipt 07-241 (cart): running line list + Total
   • tap a line → edit / remove
   • [＋ Scan another] → back to ①
   • [Finish & save]
   ▼
④ Confirmation: receipt #, Total AUD, payment method
   [New sale] / [Done → Home]
   (Pending sync count is intentionally omitted from this screen.)
```

**Edge paths (each a single state, not a new flow):**

- **Not found:** "Not in plant list" → Retry · Type # · **Sell as unknown** (scanned barcode/accession
  stored on the line so it can be reconciled later).
- **Mistake / abandon:** tap a line to edit or remove; backing out of an empty receipt discards it (no save).
- **Offline:** the entire loop works; the receipt saves locally and increments the pending count.

## Sync & Export (Phase 1 — supporting)

Two "talk to the cloud" jobs. **Sales export runs automatically on a timer; the plant-list
update is a manual pull.** Both manual buttons use the same pattern:
**tap → progress (inline spinner) → Done or Error. No confirm, no chooser.**

```
Sync tab (online)
┌───────────────────────────┐
│ 🌿 Plant list             │
│ 1,204 plants · 2h ago     │
│ [ Update plant list ]     │  tap → ⏳ → Done / Error
├───────────────────────────┤
│ 🧾 Sales export           │
│ Auto every 1 min · on     │
│ 0 pending · last 12:04     │
│ [ Export now ]            │  tap → ⏳ → Done / Error
└───────────────────────────┘
```

- **Update plant list (pull):** replaces the cached list from Google Sheets; shows plant count +
  last-updated so a scan's data is trustworthy. Stays **manual** — refreshed only when a volunteer asks.
- **Sales export (push) — automatic:** a background job pushes the **pending (not-yet-exported)**
  receipts to Sheets **every N minutes when online** (period pre-configured, **default 1 minute**).
  Export includes the transaction date (per spec). Default scope = **all pending** (no date-range picker).
- **Silent by design:** auto-export shows **no popups and no flicker** (per the accessibility rules) —
  the only feedback is the top-bar status chip and the tile (pending drops to 0, "last HH:MM").
- **Export now (manual fallback):** the same push on demand, keeping the one-tap → Done/Error
  feedback — useful before closing up or to confirm a sync visibly.
- **Done:** receipts marked exported, pending → 0.
- **Auto-export failure** (offline mid-tick, Sheets/auth): **silent** — nothing marked exported,
  pending unchanged, retried on the next tick. **Nothing lost.** (The visible Error screen with a
  "Try again" button is reserved for a manual *Export now*.)
- **Offline:** auto-export simply skips its ticks and sales keep saving locally; pending climbs and the
  chip shows "Offline·N". *Update plant list* / *Export now* are disabled with a plain reason
  ("needs Wi-Fi"). Exporting resumes automatically when back online.

**No double-counting:** exported receipts are flagged, so neither an auto-tick nor a manual *Export
now* ever re-sends them.

## Phase 2 sketches (DEFERRED — revisit later)

> Parked at the user's request; captured only to confirm the shell holds them. Not finalized.

All four light up a dimmed home tile and reuse the same spine —
**scan/pick plant → small action form → confirm → saved locally → synced later** —
except New accession, which starts without a scan. Each writes a pending inventory change
that the **same Sync push** sends up (matching Phase 2's auto-update-inventory goal).

- **New accession:** name / group / light / batch qty → Create & assign # → offers to print labels.
- **Print label:** scan/pick → number of labels → Add to print queue (cabled NiceLabel printer still prints).
- **Repot (tube→pot):** scan → qty repotted → Mark "Ready for Sale".
- **Record death:** scan → qty died → optional reason → Record death (subtract from inventory).

## Cross-cutting behavior

- **Accessibility-first (elderly volunteers):** big buttons + text, high contrast, no
  flickering, tap-not-gesture — see *Audience & accessibility* above; applies to every screen.
- **Offline-first everywhere:** the device's cached plant list backs every scan; all changes
  (sales now; repot/death/etc. later) are written locally first, then pushed up — **sales auto-export
  on a timer**, the plant-list update is a manual pull.
- **Trust signals:** persistent status chip + pending count so volunteers always know whether
  data is saved-but-unsent.
- **One shared scanner + plant card** component is reused by Sell and all Phase 2 actions.

## Resolved questions (were "subject to discussion")

All four open questions from the source spec are now answered:

1. **Unit price column in the plant sheet?** → **No.** Unit price is always keyed manually at
   point of sale (decision #6); there is no pre-fill.
2. **Plant-list source of truth?** → **Google Sheets.** The app pulls from and trusts the Sheet;
   the upstream MS Access → Sheets export pipeline is **out of scope** for the app.
3. **Receipt-number scheme?** → **Purely local** (decision #11) — generated on-device, no central
   coordination.
4. **Multiple devices running at once?** → **Unlikely, but assume yes.** Each device carries a
   configured **2-digit prefix**, so locally-numbered receipts stay collision-free in the shared
   Sheet (decision #11). No sync-merge logic needed — exports just append uniquely-numbered rows.
