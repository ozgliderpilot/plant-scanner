# CI screenshot gallery

Review aid for Android-impacting pull requests: an emulator + Maestro walk captures thirteen
volunteer-critical screens and posts a collapsible PR comment. Screenshots never participate in
the merge gate (`ci-success` ignores the `app-screenshots` job). Settings is omitted (hidden
behind a version-tap gate).

## When it runs

- Same-repository `pull_request` only (not push to `main`, not forks).
- Only when the existing **app** path filter matches and the **app** assemble job succeeded.
- Soft check: emulator/Maestro failure may mark `app-screenshots` red; it must not fail
  `ci-success` or block merge.

## Gallery contract (13 frames)

Walk order matches volunteer workflows (sell → history/receipts → cull → catalog):

| # | Caption | Maestro name |
|---|---------|--------------|
| 1 | Actions | `01-actions` |
| 2 | Sell · scan | `02-sell-scan` |
| 3 | Sell · line item | `03-sell-line` |
| 4 | Sell · cart | `04-sell-cart` |
| 5 | Sell · confirm | `05-sell-confirm` |
| 6 | History | `06-history` |
| 7 | Receipts | `07-receipts` |
| 8 | Receipt detail | `08-receipt-detail` |
| 9 | Cull · scan | `09-cull-scan` |
| 10 | Cull · enter info | `10-cull-info` |
| 11 | Cull · success | `11-cull-success` |
| 12 | Culls | `12-culls` |
| 13 | Plants | `13-plants` |

Files on the orphan `ci-screenshots` branch:

```
pr/<pull_number>/<NN-name>-<shortsha>.png
pr/<pull_number>/manifest-<shortsha>.txt
```

The PR comment embeds **only** paths listed in this run's manifest (partial galleries are OK),
laid out in an HTML table with **three screenshots per row**. A new comment is posted every run
(not an edited sticky). On PR close, `.github/workflows/screenshots-cleanup.yml` deletes
`pr/<number>/`.

## CI mode (qaDebug only)

Compile-time fence: orchestrator + activator live under `app/src/qaDebug/`. Prod and qaRelease
builds do not contain the seed/activator code. Thin hooks in `main` (`CiMode` flags +
`CiScanPlaceholder`) stay inert unless activated.

**Launch extra** (cold start):

```bash
adb shell am start -n com.nursery.scanner.test/com.nursery.scanner.MainActivity \
  --ez com.nursery.scanner.CI_MODE true
```

Constant: `CiMode.EXTRA_CI_MODE` = `com.nursery.scanner.CI_MODE`.

When active, CI mode:

1. Stops / never starts the auto-export ticker (dummy endpoint must never be POSTed).
2. Sets camera permission bypass + static scan placeholder.
3. Seeds fixtures **once** (SharedPreferences flag); relaunch does not duplicate rows.

### Fixtures

| Setting | Value |
|---------|-------|
| Device prefix | `99` |
| Endpoint | `https://ci.invalid/exec` |
| Shared secret | `ci-secret` |
| Plant accessions | `1001`, `1002`, `1003` (sale walk `1001`; cull walk `1002`) |
| Seeded receipt | one SAVED receipt (Westringia / 1002) — opened for receipt detail |
| Seeded cull | one PENDING cull (Lomandra / 1003) |

Walked sale: 1 pot, unit price `$5.00`, no discount, default Card payment.
Walked cull: 1 pot, default reason/unit (no notes).

Normal qaDebug sideloads without the extra stay empty (no auto-seed).

## Maestro

- Flow: [`.maestro/gallery.yaml`](../../.maestro/gallery.yaml)
- Pinned CLI version: see `MAESTRO_VERSION` in [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
- Text selectors first; add Compose test tags only if a step proves flaky
- After typed fields, `hideKeyboard` before tapping Find / Add (IME covers those buttons on
  Pixel-class AVDs; BACK only dismisses the keyboard while it is showing)
- Screenshots use `takeScreenshot.path: maestro-out/<frame>` (workspace-relative)
- Emulator: API 30, x86_64, AOSP `default`, Pixel-class profile, animations off, 30 min timeout
  (default en-US; Maestro selectors are English — no brittle locale setprops on CI)
- Gallery runner: [`.github/scripts/screenshots/run-gallery.sh`](../../.github/scripts/screenshots/run-gallery.sh)
  (must stay a single emulator-runner `script:` line — that action splits multi-line scripts)

Helper scripts: [`.github/scripts/screenshots/`](../../.github/scripts/screenshots/).

## Soft vs red policy

| Outcome | `app-screenshots` | `ci-success` / merge |
|---------|-------------------|----------------------|
| Full gallery | green | unaffected |
| Partial gallery (flow died mid-way) | red (Maestro exit) but comment still posted | unaffected |
| Emulator / publish failure | red | unaffected |
| App assemble failure | job skipped | fails as today |

## Cursor Cloud

Cloud VMs have **no KVM** — do not install AVDs or run Maestro there. Visual review for Cloud Agent
Android PRs comes from this GitHub Actions path after the QA debug APK builds.
