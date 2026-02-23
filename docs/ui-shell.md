# Core TUI Shell

## Layout Structure
- Header: app title and context.
- Body: two panels.
  - Left panel: project metadata input fields (`groupId`, `artifactId`, `version`, `packageName`, `outputDir`, `buildTool`, `javaVersion`).
  - Right panel: extension search, extension catalog list, selected summary.
- Footer: structured key hints + live status + validation feedback.

The shell keeps a stable widget tree and switches only the body split strategy by width:
- narrow terminals: vertical stack
- standard/wide terminals: horizontal side-by-side

## Focus Routing
- Deterministic focus order:
  1. `groupId`
  2. `artifactId`
  3. `version`
  4. `packageName`
  5. `outputDir`
  6. `buildTool`
  7. `javaVersion`
  8. extension search
  9. extension list
  10. submit action
- `Tab` moves to next focus target.
- `Shift+Tab` moves to previous focus target.
- `/` jumps directly to extension search when focus is not editing `outputDir` or extension search input.
- `Ctrl+F` jumps directly to extension search from any focus target.
- `Ctrl+L` jumps directly to extension list.
- In extension search, `Down` moves focus to extension list.
- In extension list, `Up` on the top row moves focus back to search.

## Key Bindings
- `Up` / `Down`: navigate extension list when focused.
- `Home` / `End`: jump to first/last selectable extension row.
- `Space`: toggle extension selection when list is focused.
- `f`: toggle favorite for the focused extension when list is focused.
- `c`: close/open the focused extension category.
- `C`: open all closed categories.
- `Ctrl+J`: jump to next visible favorite extension.
- `Ctrl+K`: toggle favorites-only filter mode.
- `Enter`: attempt submit (blocked with validation feedback if invalid).
- `Esc` or `Ctrl+C`: cancel active generation if running, otherwise exit the TUI.
- Backend startup preference is deterministic:
  - JVM with native access enabled: `panama,jline3`.
  - JVM without native access: `jline3` (with guidance to use `--enable-native-access=ALL-UNNAMED`; some terminal-native warnings can still appear).
  - Native image runtime: `panama,jline3`.

## Validation and Status
- Metadata + field validation are recalculated as project inputs change.
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
- Catalog rows are grouped with stable category section headers, and keyboard navigation skips
  section-header rows deterministically.
- Section headers show open/closed state (`[-]` / `[+]`) and hidden-item counts for closed categories.
- Favorites keep ranked position (API order precedence is preserved) and are marked/toggled via `*` indicator and favorite key actions.
- Extension list labels use one rule: display extension `name` only (no alias/short-name suffix noise).
- Catalog rendering has explicit loading and fallback/degraded visuals.
- Source labeling is explicit: `live`, `cache`, or `snapshot`; stale cache is marked `[stale]`.
- `Ctrl+R` triggers catalog refresh/retry without restarting the TUI.

## Local Cache
- Catalog cache policy is documented in `docs/catalog-cache.md`.
- Runtime fallback order is `live -> cache -> snapshot`.
