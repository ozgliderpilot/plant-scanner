# Plants tab is a full mirror, stock-gated

## Context

Access holds the full batch catalogue, including zero-stock and historical rows. The device needs a small offline lookup set that matches what volunteers can sell or propagate *now*, without merge/upsert complexity.

## Decision

Access pushes via `replacePlants`: clear and rewrite the whole Sheet `Plants` tab with stock > 0 accessions only (no upsert). The device imports that list and replaces its local plant cache wholesale the same way.

## Consequences

Offline lookups match current in-stock Access data after sync. Zero-stock accessions disappear from the device until stock returns; partial/delta sync is not supported.
