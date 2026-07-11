# Unknown scans kept on sell/cull; blocked for labels

## Context

Volunteers sometimes scan a label that is not yet (or no longer) in the local plant list. Dropping the scan loses stock movement evidence. Reprinting labels for an unknown accession would enqueue work NiceLabel/Access cannot fulfil.

## Decision

On sell and cull, never drop a not-found scan — record as unknown with the scanned code kept. On label print requests, reject missing accessions with an administrator message and do not enqueue.

## Consequences

Unknown sales/culls need later reconciliation. Label print is administrator-gated for catalogue gaps.
