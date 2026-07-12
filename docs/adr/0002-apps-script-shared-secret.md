# Apps Script + shared secret, not Sheets API

## Context

Two trusted devices need to read/write a Google Sheet. Official Sheets API brings OAuth, a Cloud project, consent screens, and token refresh — heavy for sideloaded internal use.

## Decision

Talk to a Google Apps Script web app over plain HTTPS/JSON, authenticated with a shared secret in Script Properties. Sheet formatting and append logic stay in the script.

## Consequences

Ops is “paste URL + secret into device settings.” There is no per-user Google identity; anyone with the secret can call the endpoint.
