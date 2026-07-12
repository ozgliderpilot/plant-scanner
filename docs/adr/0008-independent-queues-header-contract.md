# Independent export queues + stable HEADER contract

## Context

Sales, culls, and label print requests hit different Sheet tabs with different column layouts. A single atomic multi-tab export would block successful queues behind a failed sibling. The Apps Script appenders index columns by position.

## Decision

Export as independent queues in order sales → culls → labels: a partial failure must not roll back a queue that already succeeded. Treat each tab’s column order (`Export.HEADER`, `CullExport.HEADER`, `LabelPrintExport.HEADER`) as a backend contract — change only with coordinated `core/` and `backend/` updates.

## Consequences

One queue can be `EXPORTED` while another stays pending. Header edits require lockstep releases; silent column reshuffles break the Sheet.
