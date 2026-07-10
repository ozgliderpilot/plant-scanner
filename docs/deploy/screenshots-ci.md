# CI screenshot gallery

Review aid for Android-impacting pull requests: an emulator + Maestro walk captures eight
volunteer-critical screens and posts a collapsible PR comment. Screenshots never participate in
the merge gate (`ci-success` ignores the `app-screenshots` job).

## When it runs

- Same-repository `pull_request` only (not push to `main`, not forks).
- Only when the existing **app** path filter matches and the **app** assemble job succeeded.
- Soft check: emulator/Maestro failure may mark `app-screenshots` red; it must not fail
  `ci-success` or block merge.

## Gallery contract (8 frames)

| # | Caption | Maestro name |
|---|---------|--------------|
| 1 | Actions | `01-actions` |
| 2 | Plants | `02-plants` |
| 3 | Sell · scan | `03-sell-scan` |
| 4 | Sell · line item | `04-sell-line` |
| 5 | Sell · cart | `05-sell-cart` |
| 6 | Sell · confirm | `06-sell-confirm` |
| 7 | Receipts | `07-receipts` |
| 8 | Culls | `08-culls` |

Files on the orphan `ci-screenshots` branch:

```
pr/<pull_number>/<NN-name>-<shortsha>.png
pr/<pull_number>/manifest-<shortsha>.txt
```

The PR comment embeds **only** paths listed in this run's manifest (partial galleries are OK).
A new comment is posted every run (not an edited sticky). On PR close,
`.github/workflows/screenshots-cleanup.yml` deletes `pr/<number>/`.

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
| Plant accessions | `1001`, `1002`, `1003` (walk uses `1001`) |
| Seeded receipt | one SAVED receipt (Westringia / 1002) |
| Seeded cull | one PENDING cull (Lomandra / 1003) |

Walked sale: 1 pot, unit price `$5.00`, no discount, default Card payment.

Normal qaDebug sideloads without the extra stay empty (no auto-seed).

## Maestro

- Flow: [`.maestro/gallery.yaml`](../../.maestro/gallery.yaml)
- Pinned CLI version: see `MAESTRO_VERSION` in [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
- Text selectors first; add Compose test tags only if a step proves flaky
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
