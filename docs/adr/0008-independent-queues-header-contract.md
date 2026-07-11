# Independent export queues + stable HEADER contract

Sales, culls, and label print requests export as independent queues in that order: a partial failure must not roll back a queue that already succeeded. Each Sheet tab’s column order (`Export.HEADER`, `CullExport.HEADER`, `LabelPrintExport.HEADER`) is a backend contract — change only with coordinated `core/` and `backend/` updates.
