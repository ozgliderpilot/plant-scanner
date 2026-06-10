# Plant List screen — design

**Date:** 2026-06-10
**Status:** Approved (brainstorming)

## Goal

From the Sync tab, let the user open a full-screen, scrollable list of all cached
plants and filter it with a single search box that matches across every field.

## Background

The Sync tab's "Plant list" card today shows `$plantCount plants cached` and an
**Update plant list** button (a manual cloud pull). The cached plants live in Room
and are exposed as `PlantRepository.plants: Flow<List<Plant>>`.

The `Plant` model (`core/.../Models.kt`) has four fields:

- `accession: String` (also the barcode value)
- `name: String`
- `group: String?`
- `light: String?`

There is no price field, by prior spec decision.

## Scope

In scope:

- A new full-screen **Plants** screen reachable from the Sync card.
- A single search box filtering across all four fields.
- A scrollable, read-only list of matching plants.

Out of scope (YAGNI):

- Per-field filters, sorting controls, or filter chips (single search box only).
- Tapping a plant row to do anything (rows are read-only).
- Editing plants or any write path (the list is a cache view).
- Changes to how the cache is fetched/refreshed.

## Design

### 1. Navigation

- Add `Routes.PLANTS = "plants"`.
- Register it in `NurseryRoot`'s `NavHost` **outside** `TabRoutes`, so it is a
  full-screen sub-screen with the top/bottom bars hidden (same treatment as
  `Routes.SETTINGS`).
- `SyncScreen` gains an `onViewPlants: () -> Unit` parameter; `NurseryRoot` wires it
  to `navController.navigate(Routes.PLANTS)`.
- The screen navigates back via its own `ScreenHeader` back button
  (`navController.popBackStack()`).

### 2. Sync card change

- In the "Plant list" card, add a `BigButton(text = "View plant list", style = Secondary)`
  positioned **above** the existing "Update plant list" button.
- It is always enabled — viewing the cached list works offline (unlike the network
  actions, which require Wi-Fi).

### 3. ViewModel

New `PlantListViewModel(plantRepository: PlantRepository)`, registered in
`NurseryViewModelFactory`.

- `private val _query = MutableStateFlow("")`; `fun setQuery(q: String)`.
- `val query: StateFlow<String>` exposed for the text field.
- `val plants: StateFlow<List<Plant>>` =
  `combine(plantRepository.plants, _query) { list, q -> PlantSearch.filter(list, q) }`
  `.stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())`.

### 4. Filter logic (pure, in `core`)

New `PlantSearch` (object) in `core` with:

```kotlin
fun filter(plants: List<Plant>, query: String): List<Plant>
```

Behaviour:

- Trim the query. If blank, return `plants` unchanged.
- Otherwise lowercase the query and keep any plant where the query is a **substring**
  (case-insensitive) of **any** of: `accession`, `name`, `group`, `light`.
- Null `group`/`light` simply do not contribute a match (no crash).
- Order is preserved (same order as the input list).

This is the "one search box, all fields" behaviour, kept pure so it is unit-testable
in isolation — consistent with `PlantBook`, `Money`, and `Export` already living in
`core`.

### 5. UI — `PlantListScreen`

Structure mirrors `SettingsScreen`:

```
Column(fillMaxSize) {
    ScreenHeader(title = "Plants", onBack = onBack)
    OutlinedTextField(...)            // search box, pinned above the list
    LazyColumn(...) { items(plants) { PlantRow(it) } }
}
```

- **Search box:** `OutlinedTextField` bound to `query`/`setQuery`, full width, with a
  label/placeholder like "Search plants". Sits between the header and the list so it
  stays visible while the list scrolls.
- **Row** (`PlantRow`): a compact `Card` (not the tall `PlantCard` used by the Sell
  flow) showing:
  - `name` in a title style (bold-ish).
  - `Accession: <accession>` as a secondary line.
  - A secondary line combining group/light when present (e.g. "Group · Light"),
    omitted entirely when both are blank/null.
  - Read-only (no `clickable`).
- **Empty states:**
  - Cache empty (no plants at all): "No plants cached — tap Update plant list on the
    Sync screen."
  - Cache non-empty but filter matches nothing: "No plants match \"<query>\"."

Spacing/sizing uses `Dimens` (large touch targets, generous gaps) per the app's
elderly-volunteer design.

### 6. Error handling

No new failure modes. The screen reads an already-cached flow; it never hits the
network. The only "empty" conditions are the two empty states above.

## Testing

- Unit tests for `PlantSearch.filter` (test-first):
  - blank/whitespace query returns the full list unchanged
  - matches on each field individually: accession, name, group, light
  - case-insensitive matching
  - substring (not just prefix) matching
  - null `group`/`light` don't crash and don't match
  - a query matching nothing returns empty
  - input order preserved
- The Compose UI and ViewModel wiring are verified manually by running the app
  (open from Sync, scroll, type to filter, back).

## Files touched

- `core/.../PlantSearch.kt` (new) + `core/.../PlantSearchTest.kt` (new)
- `app/.../ui/nav/Destinations.kt` — add `Routes.PLANTS`
- `app/.../ui/sync/SyncScreen.kt` — add "View plant list" button + `onViewPlants`
- `app/.../ui/plants/PlantListScreen.kt` (new)
- `app/.../ui/plants/PlantListViewModel.kt` (new)
- `app/.../di/NurseryViewModelFactory.kt` — construct `PlantListViewModel`
- `app/.../ui/NurseryRoot.kt` — register `Routes.PLANTS`, wire `onViewPlants`
