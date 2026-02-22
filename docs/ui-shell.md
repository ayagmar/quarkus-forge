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

## Key Bindings
- `Up` / `Down`: navigate extension list when focused.
- `Space`: toggle extension selection when list is focused.
- `Enter`: attempt submit (blocked with validation feedback if invalid).
- `Esc` (or quit key): cancel active generation if running, otherwise exit the TUI.

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

## Deterministic Async Search
- Extension catalog is loaded from Quarkus API and indexed in-memory by stable extension identifier.
- Extension search filtering uses a debounced async scheduler (`120ms` default).
- The scheduler/debouncer layer is injectable so tests can run with virtual time instead of wall-clock delays.
- Extension catalog/filter/selection state is isolated in a dedicated UI component
  (`ExtensionCatalogState`) and consumed by `CoreTuiController`.
- Cancellation and stale-result protection ensure outdated async callbacks never overwrite newer search state.
- Multi-selection is tracked by stable extension IDs, independent from list navigation cursor state.
- Catalog rendering has explicit loading and fallback/degraded visuals.
