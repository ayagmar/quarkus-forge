# Core TUI Shell

## Layout Structure
- Header: app title and context.
- Body: two panels.
  - Left panel: project metadata input fields (`groupId`, `artifactId`, `version`, `platformStream`, `buildTool`, `javaVersion`, `packageName`, `outputDir`).
  - Right panel: extension search, extension catalog list, selected summary.
- Footer: structured key hints + live status + validation feedback.

The shell keeps a stable widget tree and switches only the body split strategy by width:
- narrow terminals: vertical stack
- standard/wide terminals: horizontal side-by-side

## Focus Routing
- Deterministic focus order:
  1. `platformStream`
  2. `buildTool`
  3. `javaVersion`
  4. `groupId`
  5. `artifactId`
  6. `version`
  7. `packageName`
  8. `outputDir`
  9. extension search
  10. extension list
  11. submit action
- `Tab` moves to next focus target.
- `Shift+Tab` moves to previous focus target.
- `/` jumps directly to extension search when focus is not editing any text input.
- `Ctrl+F` jumps directly to extension search from any focus target.
- `Ctrl+L` jumps directly to extension list.
- From extension search, press `Down` to move focus to extension list.
- When extension list is focused, `Up` on the top row moves focus back to search.

## Key Bindings
- `Up` / `Down`: navigate extension list when focused.
- `j` / `k`: vim-style list navigation aliases for `Down` / `Up`.
- `Left` / `Right` (or `h` / `l`): section hierarchy navigation in extension list (`Left`: item -> section / section -> close, `Right`: section -> open/first item).
- `Home` / `End`: jump to first/last selectable extension row.
- `PgUp` / `PgDn`: jump to previous/next category section header.
- `Left` / `Right` / `Home` / `End`: cycle metadata selectors (`platformStream`, `buildTool`, `javaVersion`).
- `h` / `l`: vim-style selector aliases for `Left` / `Right`.
- `Space`: toggle extension selection when list is focused.
- `v`: cycle category filter in extension list.
- `x`: clear all selected extensions.
- `Space` on a category header: close/open that category.
- `f`: toggle favorite for the focused extension when list is focused.
- `c`: close/open the focused extension category.
- `C`: open all closed categories.
- `Ctrl+J`: jump to next visible favorite extension.
- `Ctrl+K`: toggle favorites-only filter mode.
- `Ctrl+E`: toggle expanded full error details in footer.
- `?`: toggle full-screen help overlay (shortcut matrix) when not editing a text input.
- `Ctrl+P`: toggle command palette with quick actions (`search/list focus`, favorites actions, category actions, reload, error details).
- `Enter`: attempt submit (blocked with validation feedback if invalid).
- `Alt+G`: attempt submit from any focus target.
- `Esc` or `Ctrl+C`: cancel active generation if running; in extension search/list, `Esc` clears active search, then favorites-only filter, then category filter; from empty search it returns focus to list; otherwise it exits the TUI.
- Backend startup preference is deterministic:
  - JVM with native access enabled: `panama,jline3`.
  - JVM without native access: `jline3` (with guidance to use `--enable-native-access=ALL-UNNAMED`; some terminal-native warnings can still appear).
  - Native image runtime: `panama,jline3`.

## Validation and Status
- Metadata + field validation are recalculated as project inputs change.
- `platformStream`, `buildTool`, and `javaVersion` are metadata-driven selectors (not free-text fields).
- Selector fields are rendered with inline radio markers (`(*)` selected / `( )` unselected) to expose discoverability.
- Footer status area is non-modal and always visible.
- Errors are surfaced inline in footer as actionable messages.
- Invalid metadata fields are highlighted with error-colored borders.
- Footer includes generation state (`idle`, `validating`, `loading`, `success`, `failed`, `cancelled`).
- Transition contract is documented in `docs/ui-state-machine.md`.
- Theme/token policy is documented in `docs/ui-theme.md`.

## Generation Flow
- On valid `Enter`, the UI starts async generation:
  1. compose generation request from metadata + selected extension IDs
  2. stream download project archive from Quarkus API
  3. extract archive safely to `<outputDir>/<artifactId>`
- While generation runs, interactive edits and focus moves are locked to prevent conflicting state changes.
- Pressing `Esc` during generation requests cancellation and keeps the app open.
- On success, footer shows build-tool-specific next step hint:
  - Maven: `cd <generated-path> && mvn quarkus:dev`
  - Gradle: `cd <generated-path> && ./gradlew quarkusDev`
- For non-interactive generation (CI/scripts), use `quarkus-forge generate` documented in `docs/cli-generate.md`.

## Deterministic Async Search
- Extension catalog is loaded from Quarkus API and indexed in-memory by stable extension identifier.
- Extension search filtering uses a configurable async debounce (`0ms` default for instant updates).
- The scheduler/debouncer layer is injectable so tests can run with virtual time instead of wall-clock delays.
- Empty-query ranking is deterministic and uses this precedence:
  1. API `order` value (ascending) when present.
  2. Curated popular baseline (with favorites as a ranking signal inside this stage).
  3. Alphabetical fallback by extension name then id.
- Extension catalog/filter/selection state is isolated in a dedicated UI component
  (`ExtensionCatalogState`) and consumed by `CoreTuiController`.
- Cancellation and stale-result protection ensure outdated async callbacks never overwrite newer search state.
- Multi-selection is tracked by stable extension IDs, independent from list navigation cursor state.
- Favorites are persisted under `~/.quarkus-forge/favorites.json` and restored on startup.
- Recently selected extensions are surfaced in a `Recently Selected` section (when no search/filter is active) and persisted in the same store.
- Catalog rows are grouped with stable category section headers.
- Keyboard navigation prefers extension rows; when the current focus is a section header (or all
  categories are collapsed), navigation moves across visible headers to keep traversal usable.
- Section headers show open/closed state (`[-]` / `[+]`) and hidden-item counts for closed categories.
- Favorites keep ranked position (API order precedence is preserved) and are marked/toggled via `*` indicator and favorite key actions.
- Extension list labels use one rule: display extension `name` only (no alias/short-name suffix noise).
- Extension search input title shows live match counters (`<matches>/<total>`).
- Catalog rendering has explicit loading and fallback/degraded visuals.
- Source labeling is explicit: `live`, `cache`, or `snapshot`; stale cache is marked `[stale]`.
- `Ctrl+R` triggers catalog refresh/retry without restarting the TUI.

## Local Cache
- Catalog cache policy is documented in `docs/catalog-cache.md`.
- Runtime fallback order is `live -> cache -> snapshot`.
