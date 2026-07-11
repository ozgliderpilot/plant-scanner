# Business logic lives in pure `core/`

All logic that is easy to get wrong (money, numbering, sync selection, export shaping, validation, search/filter) lives in a pure Kotlin/JVM `core/` module with no Android types, unit-tested with system `gradle`. The `app/` module is thin Compose/Room/CameraX glue that delegates to `core/`.
