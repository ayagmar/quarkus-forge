# Open Backlog

Last compared against the codebase on 2026-03-07.

This file tracks forward-looking work that remains open after the completed architecture and
verification refactors.

Closed planning docs for the architecture execution program are intentionally not treated as active
backlog authorities here. This keeps one maintained source per currently open workstream.

Product, UX, delivery, and site-facing backlog items live in `docs/product-improvements-backlog.md`
so this file can stay focused on engineering follow-up work.

Items that are already reflected in the codebase were intentionally omitted. In particular:

- the broader package split into `application`, `cli`, `runtime`, `headless`, `postgen`,
  `persistence`, and `forge` is largely in place
- direct tests already exist for `UrlConnectionTransport` and `HeadlessCatalogClient`
- the system-property JUnit helper already exists
- `justfile` coverage output already points to merged JaCoCo paths
- UI refactor milestones for extension-catalog splitting, catalog/startup-overlay intents/effects, render-adapter extraction, and shared shortcut/command-palette action routing are complete

## TUI Refactor Follow-Ups

- Migrate the remaining extension interaction flows to intents/reducer ownership, especially category filter/toggle/open-all actions and the `Esc` unwind chain that still live in controller-managed helpers.
- Remove remaining duplicate transition authority for migrated slices, especially where `CoreTuiController` still owns reducer-like state changes or computes effect inputs from mutable controller fields.
- Shrink the `UiEffectsRunner` to `CoreTuiController` callback surface further only where narrower ports materially remove controller-owned state plumbing.
- Clarify the reducer-owned vs snapshot-only `UiState` subset so migrated fields cannot be silently dropped or recomputed outside the reducer path, while keeping mutable widget internals as adapter-layer concerns.
- Finish the final cleanup milestone from the state-machine refactor: delete obsolete controller helpers/fields and reduce `CoreTuiController` further toward a true orchestration shell.
- Keep adding semantic-first tests, especially parity coverage for remaining shortcut/list/palette overlaps, and use render-string assertions selectively with a small golden-render suite only where layout regressions are worth pinning.

## Codebase Structure

- Remove the remaining deprecated root-package compatibility shims for Forgefile types once external compatibility constraints allow it:
  - `src/main/java/dev/ayagmar/quarkusforge/Forgefile.java`
  - `src/main/java/dev/ayagmar/quarkusforge/ForgefileLock.java`
  - `src/main/java/dev/ayagmar/quarkusforge/ForgefileStore.java`

## Runtime Seams

- Introduce a smaller abstraction boundary for headless catalog/generation orchestration so `HeadlessGenerationService` does not need to lean on concrete collaborators more than necessary.
- Consolidate runtime wiring/factory logic further so entrypoints do less direct object-graph assembly.
- Revisit I/O boundary abstractions only where they simplify seam-level tests immediately; avoid speculative framework-style indirection.

## Tests And Tooling

- Add direct automated coverage for any still-uncovered CI gating around native-size enforcement and malformed report handling if current Python fixtures are not yet exhaustive.
- Recheck whether `just test-it` should be simplified further if the current `test-compile verify` recipe remains confusing for maintainers.
