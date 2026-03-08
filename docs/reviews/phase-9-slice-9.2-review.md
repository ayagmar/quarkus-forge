# Phase 9 Slice 9.2 Review

## Findings

- High: `docs/ai/open-backlog.md:50`, `docs/ai/open-backlog.md:62`, and `docs/ai/open-backlog.md:72` still duplicate the active UX, CLI/delivery, and docs/site backlog already maintained in `docs/product-improvements-backlog.md:3`, `docs/product-improvements-backlog.md:19`, `docs/product-improvements-backlog.md:33`, and `docs/product-improvements-backlog.md:40`. That leaves two maintained backlog sources for the same active workstreams, which conflicts with the new `docs/ai/open-backlog.md:8` to `docs/ai/open-backlog.md:9` rule that each active workstream should have one maintained source.
- Medium: `docs/ai/open-backlog.md:34`, `docs/ai/open-backlog.md:35`, and `docs/ai/open-backlog.md:46` still present already-completed cleanup as open backlog. `ExtensionFavoritesStore` is already under `persistence` rather than `api`, the ArchUnit suite already enforces package boundaries across `application`, `runtime`, `cli`, `headless`, `postgen`, and `persistence` in `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:15`, `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:60`, `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:84`, `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:139`, `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:148`, and `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:173`, and a repo-wide search under `src/test/java` found no remaining `withSystemProperty(...)` helper usages to replace.
- Low: `docs/ai/open-backlog.md:13` now understates the implemented package split by listing `cli`, `runtime`, `headless`, `postgen`, `persistence`, and `forge` but omitting the first-class `application` package that exists in the main codebase and is covered by the architecture rules.

## Assumptions / Verification Context

- Reviewed only `docs/ai/open-backlog.md` for this slice and recorded results in this file, per the ownership constraint.
- Verified that `docs/ai/open-backlog.md` no longer references `docs/codebase-improvement-plan.md`, `docs/core-tui-controller-hardening-plan.md`, or `docs/ui-state-machine-refactor-spec.md` directly.
- Cross-checked the remaining backlog entries against the current docs set, package layout, and `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java`, plus a repo-wide search for `withSystemProperty(...)` under `src/test/java`.

## Resolution

- Narrowed `docs/ai/open-backlog.md` to engineering follow-up work only and pointed product, UX, delivery, and site-facing work at `docs/product-improvements-backlog.md` as the maintained source.
- Removed already-completed cleanup items from the open backlog and updated the implemented package-split summary to include `application/`.
