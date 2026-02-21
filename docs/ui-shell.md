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
- `Esc` (or quit key): exit the TUI.

## Validation and Status
- Metadata + field validation are recalculated as project inputs change.
- Footer status area is non-modal and always visible.
- Errors are surfaced inline in footer as actionable messages.

## Deterministic Async Search
- Extension search filtering uses a debounced async scheduler (`120ms` default).
- The scheduler/debouncer layer is injectable so tests can run with virtual time instead of wall-clock delays.
- Cancellation and stale-result protection ensure outdated async callbacks never overwrite newer search state.
