# Pot-type defaults diverge for sell vs cull

Sell and cull both pick a default pot type from nursery counts, but with opposite priorities: sell prefers **Pots → Misc → Tubes**; cull prefers **Tubes → Pots → Misc**. That matches how volunteers typically sell vs discard stock; the rules live in `SaleUnit.defaultFor` and `CullUnit.defaultFor` in `core/`.
