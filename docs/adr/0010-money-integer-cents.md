# Money is integer cents; price keyed at sale

## Context

The plant sheet has no price column — pricing is decided at the till. Floating-point money math drifts. Cash/card is handled outside the app at the nursery.

## Decision

All monetary math uses integer cents. Unit price is keyed on the line item at sale; discount is a per-line percentage (0–100). Payment method is recorded on the receipt; the app does not process payments.

## Consequences

Totals are deterministic and testable in `core/`. Price history lives on saved line items, not on the plant cache.
