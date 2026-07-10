# CI screenshot gallery

Review aid for Android-impacting PRs: emulator + Maestro walk posts a collapsible PR comment.
Soft check only (`ci-success` ignores `app-screenshots`). Settings is omitted (version-tap gate).

## When it runs

- Same-repository `pull_request` only (not push to `main`, not forks).
- Same **app** path filter as the assemble job; runs after that job succeeds.
- Emulator/Maestro failure may mark `app-screenshots` red; it must not block merge.

## Frames

Source of truth: [`.maestro/gallery-frames.txt`](../../.maestro/gallery-frames.txt) (`id|caption`).
Walk: [`.maestro/gallery.yaml`](../../.maestro/gallery.yaml).

Files on orphan `ci-screenshots`: `pr/<n>/<id>-<shortsha>.png` + `manifest-<shortsha>.txt`.
Comment embeds this run’s manifest only (partial OK), three thumbs per row. New comment each run.
On PR close, [`.github/workflows/screenshots-cleanup.yml`](../../.github/workflows/screenshots-cleanup.yml)
deletes `pr/<n>/`.

## CI mode (qaDebug only)

`CiNurseryApplication` + `CiBootstrap` live under `src/qaDebug/`. Launch extra
`com.nursery.scanner.CI_MODE` (see `CiMode.EXTRA_CI_MODE`). When `CiMode.active`: stop
auto-export, camera placeholder + permission skip, seed fixtures every cold start
(Maestro `clearState`).

Fixtures: prefix `99`, dummy endpoint, plants `1001`/`1002`/`1003`, one SAVED receipt, one PENDING
cull. Walked sale is `1001` pot @ `$5.00` (−10%) plus `1003` pot @ `$4.00` before confirm
(no extra frames). A second same-day sale (`1002` @ `$8.00`) runs after confirm so receipts
shows two today. Walked cull uses `1002` with notes and qty 3. Walked print label: unknown
`9999` (not-found frame), then `1003` with copies bumped to 2.

## Runner

- [`.github/scripts/screenshots/run-gallery.sh`](../../.github/scripts/screenshots/run-gallery.sh)
  (single emulator-runner `script:` line)
- Normalize + publish + comment:
  [`.github/scripts/screenshots/`](../../.github/scripts/screenshots/)
- Maestro CLI version: `MAESTRO_VERSION` in [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
- Emulator: API 30 x86_64 AOSP, Pixel-class, animations off
- Primary actions use Compose `testTag` / Maestro `id`; typed fields stay text selectors

## Soft vs red

| Outcome | `app-screenshots` | merge gate |
|---------|-------------------|------------|
| Full / partial gallery | green / red (Maestro) | unaffected |
| Emulator / publish failure | red | unaffected |
| App assemble failure | skipped | fails as today |

## Cursor Cloud

No KVM — do not run AVDs/Maestro there; use this Actions path after the QA debug APK builds.
