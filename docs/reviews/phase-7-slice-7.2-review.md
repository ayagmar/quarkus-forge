# Phase 7 Slice 7.2 Review

## Findings

1. Medium: `RuntimeWiring.headlessGenerationService(...)` still breaks the new runtime-bundle ownership boundary instead of preserving it. `RuntimeServices` is now documented and typed as the shared session-lifetime composition root for interactive and headless entry paths (`src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeServices.java:12-18`, `docs/modules/ROOT/pages/architecture.adoc:103`), but `RuntimeWiring` immediately opens that bundle and peels it back into raw collaborators (`src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeWiring.java:23-29`). The returned `HeadlessGenerationService` then closes only its internal `HeadlessCatalogClient`, which in turn closes the `QuarkusApiClient` directly (`src/main/java/dev/ayagmar/quarkusforge/headless/HeadlessGenerationService.java:56-68`). That happens to release the only closeable resource today, but it removes the single close boundary the slice is trying to establish. If `RuntimeServices` picks up any additional closeable collaborator later, the headless CLI path will leak it unless `RuntimeWiring` and `HeadlessGenerationService` are both kept in sync. This is architectural drift against the runtime-bundle adoption goal.

2. Low: I did not find a focused regression test that pins the headless adapter to the new bundle contract. Current tests cover `RuntimeServices.open(...)` and the `TuiBootstrapService` session flow, but there is no direct test around `RuntimeWiring.headlessGenerationService(...)` ownership or close behavior. That leaves the boundary drift above unguarded and makes it easy for later slices to keep decomposing `RuntimeServices` while the docs continue to describe it as the shared session-lifetime root.

## Assumptions / Verification Context

- Static review only; I did not modify production code and did not revert any unrelated worktree changes.
- Inspected `src/main/java/dev/ayagmar/quarkusforge/runtime/TuiBootstrapService.java`, `src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeWiring.java`, `src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeServices.java`, `src/main/java/dev/ayagmar/quarkusforge/headless/HeadlessGenerationService.java`, and `docs/modules/ROOT/pages/architecture.adoc`.
- `TuiBootstrapService`'s interactive, interactive-smoke, and headless-smoke adoption of `RuntimeServices` looked consistent on static inspection; the slice-local concern is the remaining headless adapter ownership gap in `RuntimeWiring`.
- I did not run `mvn` or `spotless` in this review pass.

## Resolution

- Resolved in Phase 7 Slice 7.3 by removing `RuntimeWiring` entirely and moving the remaining CLI/headless runtime entrypoints onto `RuntimeServices`.
- The missing regression coverage was addressed with focused tests for runtime-backed stored-prefill round-tripping, runtime-backed headless service creation, and explicit headless close-owner behavior.
