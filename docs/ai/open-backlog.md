# Open Backlog

Last compared against the codebase on 2026-03-07.

This file extracts the still-open work from:

- `docs/product-improvements-backlog.md`
- `docs/codebase-improvement-plan.md`
- `docs/ui-state-machine-refactor-spec.md`
- `docs/core-tui-controller-hardening-plan.md`

Items that are already reflected in the codebase were intentionally omitted here. In particular:

- the broader package split into `cli`, `runtime`, `headless`, `postgen`, `persistence`, and `forge` is largely in place
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
- Finish persistence ownership cleanup by moving `ExtensionFavoritesStore` abstractions/implementations out of `api` if the team wants package ownership to reflect storage rather than transport concerns.
- Add stronger ArchUnit/package-boundary rules beyond the current UI/headless guards, especially around `cli`, `runtime`, `postgen`, and persistence ownership.

## Runtime Seams

- Introduce a smaller abstraction boundary for headless catalog/generation orchestration so `HeadlessGenerationService` does not need to lean on concrete collaborators more than necessary.
- Consolidate runtime wiring/factory logic further so entrypoints do less direct object-graph assembly.
- Revisit I/O boundary abstractions only where they simplify seam-level tests immediately; avoid speculative framework-style indirection.

## Tests And Tooling

- Add direct automated coverage for any still-uncovered CI gating around native-size enforcement and malformed report handling if current Python fixtures are not yet exhaustive.
- Replace remaining ad hoc `withSystemProperty(...)` helpers in tests with the shared JUnit extension where practical.
- Recheck whether `just test-it` should be simplified further if the current `test-compile verify` recipe remains confusing for maintainers.
- Add architecture-sensitive maintainer checklists for routing, reducer/effects, native release changes, and other failure-prone areas.

## Product / UX

- Add a pre-generate “Safe Generate” preflight summary for output conflicts, invalid fields, and missing tools.
- Add a short, dismissible, remembered first-run onboarding flow.
- Add progressive UI density modes such as `compact`, `normal`, and `verbose`.
- Add short-lived “what changed” feedback after key actions.
- Add undo for recent extension/filter actions.
- Improve extension relevance ranking with deterministic tie-breakers.
- Improve long-text behavior in narrow terminals with a consistent clipping/ellipsis policy.
- Add better keyboard-shortcut discoverability by context.
- Expand post-generation prechecks so unavailable actions are shown with actionable reasons before selection.

## CLI / Delivery

- Add `--print-plan` for resolved generation plans without generating files.
- Add `--log-format text|json` instead of a boolean-only verbose toggle.
- Add `--non-interactive` to the full `quarkus-forge` command so the full binary can force headless behavior.
- Add shell completions for `bash`, `zsh`, `fish`, and `powershell` as a delivered feature, not just local generation helpers.
- Publish release checksum files and verification snippets as part of the release workflow.
- Expand native release smoke tests and add event-loop/render-cycle latency sanity checks where useful.
- Surface architecture-fitness summaries in CI, such as ArchUnit and contract-test counts.

## Docs / Site

- Expand the README binary comparison into a clear “Which binary should I use?” decision table.
- Expand troubleshooting into a symptom-driven index.
- Keep short ADR-style decision records for major architecture changes.
- Add an install selector by OS and usage mode to the landing page/site.
- Add an interactive terminal demo.
- Surface trust signals prominently: checksums, release freshness, CI status, and native smoke status.
