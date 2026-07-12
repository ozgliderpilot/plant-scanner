# Accessibility-first UI for elderly volunteers

## Context

Primary users are elderly nursery volunteers outdoors or in bright sheds, often with gloves or reduced dexterity. Gesture-heavy or dense mobile UI patterns fail in that setting.

## Decision

Treat accessibility as a primary design constraint on every screen: large tap targets and text, high contrast, no flicker from silent background sync, and tap-not-gesture interactions (no swipe-to-delete).

## Consequences

UI density stays low by design. Features that need gestures, tiny controls, or busy status chrome need a different interaction model or are out of scope.
