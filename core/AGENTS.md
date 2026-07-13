# core/

Pure Kotlin/JVM business logic. No Android imports.

## Commands

```bash
cd core && gradle test
cd core && gradle test --tests "com.nursery.core.MoneyTest"
```

Use a system `gradle`, not `./gradlew`. This module has its own `settings.gradle.kts` / `build.gradle.kts`.

## What belongs here

Money, receipt numbering, plant lookup, sync selection (`Sync`, `CullSync`, `RepotSync`), cloud-sync
combine (`CloudSync`), export row shaping (`Export`, `CullExport`, `RepotExport`), ready-for-sale
defaults (`RepotReadyForSale`), validation, search/filter — anything easy to get wrong.

## Rules

- Add or change logic here, with a JUnit test in `core/src/test/`.
- Edit `core/src/` only — `core/bin/` is generated and shows up in searches.
- Export `HEADER` lists are backend contracts; keep column order stable.

See root [`AGENTS.md`](../AGENTS.md) for cross-module invariants and [`CONTEXT.md`](../CONTEXT.md) for
domain vocabulary.
