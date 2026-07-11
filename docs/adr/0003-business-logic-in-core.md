# Business logic lives in pure `core/`

## Context

Money math, receipt numbering, sync selection, export shaping, and validation are easy to get wrong. Early builds had no Android SDK on the machine that ran unit tests, so logic buried in the app module was hard to verify.

## Decision

Put all error-prone business logic in a pure Kotlin/JVM `core/` module with no Android types, unit-tested with system `gradle`. Keep `app/` as thin Compose/Room/CameraX glue that delegates to `core/`.

## Consequences

New behaviour that can go wrong must land in `core/` with a test. ViewModels and Composables stay declarative; Android-only code is glue, not domain.
