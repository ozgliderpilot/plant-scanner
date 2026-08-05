# Magic link + per-device Users claim

## Context

Volunteers install from Play Store and previously typed the `/exec` URL, access code, and device
prefix by hand. A custom-scheme magic link can prefill those fields without owning a domain, but a
shared link must not let a second phone silently take over the same device prefix.

## Decision

1. **Magic link** `plantscanner://setup?prefix=PP&url=…&code=…` prefills device prefix, endpoint
   URL, and nursery access code (`SHARED_SECRET`). The app generates a fresh **device secret** and
   stores it locally — the link never carries the device secret.
2. Every Android device-bound POST (`getPlants`, `append*`) sends `devicePrefix` + `deviceSecret`
   alongside the nursery access code.
3. The Apps Script backend keeps the shared-secret gate, then for device-bound actions claims or
   verifies against a **Users** sheet tab (`device_prefix`, `name`, `secret`):
   - no row → append `name=unknown` and set `secret`
   - row with empty `secret` → set `secret` (first claim)
   - row with `secret` set → must match, else `Unauthorized`
4. Access reverse-sync / plant-push actions stay shared-secret only (no Users claim).

## Consequences

- A magic link for a given prefix activates on the first phone that syncs successfully; a second
  phone opening the same link generates a different device secret and is rejected until an admin
  clears that row’s `secret` cell (reinstall / replacement phone).
- Ops can pre-seed Users rows (`prefix` + volunteer `name`, blank `secret`) or let first sync create
  `unknown` rows.
- Reinstall on the same phone also needs a cleared `secret` (or a new prefix) because the local
  device secret is wiped with app data.
