# Google Sheet is the hub; Access is out of band

## Context

The nursery’s system of record for stock is MS Access on a PC. Volunteers need mobile sell/cull/label flows offline. Wiring Android directly to Access is fragile and out of scope for a sideloaded scanner.

## Decision

The app’s only cloud peer is Google Sheets via Apps Script. Access remains upstream/downstream for plant pushes and reverse sync, but the Android app never talks to Access. Stock on mobile is **record-only**; count corrections stay in Access.

## Consequences

Mobile never decrements nursery counts locally. Stale plant-list rows are possible until the next cloud sync import after Access refreshes the Sheet.
