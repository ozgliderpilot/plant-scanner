# Pot-type defaults diverge for sell vs cull

## Context

Each accession carries Tubes / Pots / Misc counts. Volunteers sell and cull different mixes of those units, so a single default rule produces the wrong first tap often enough to matter on an accessibility-first UI.

## Decision

Sell defaults **Pots → Misc → Tubes**; cull defaults **Tubes → Pots → Misc**. Rules live in `SaleUnit.defaultFor` and `CullUnit.defaultFor` in `core/`.

## Consequences

Flow-specific defaults must stay tested separately. Changing one rule must not silently change the other.
