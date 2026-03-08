# Phase 7 Slice 7.1 Review

## Findings

- Low: `RuntimeServices` introduces the new runtime bundle, but the headless adapter still immediately explodes that bundle back into loose collaborators instead of preserving the bundle as the lifecycle owner. `RuntimeServices` keeps the old per-service factory helpers in `src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeServices.java:35-50`, `RuntimeWiring.headlessGenerationService(...)` opens the bundle and passes its parts individually in `src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeWiring.java:23-29`, and `HeadlessGenerationService.close()` still closes only the catalog loader in `src/main/java/dev/ayagmar/quarkusforge/headless/HeadlessGenerationService.java:56-68`. That means the new composition root is not yet the thing that owns headless-session shutdown, which is architectural drift against the Phase 7 goal of making service lifetime and closing rules explicit.
- Low: The new focused test coverage only proves that `RuntimeServices.open(...)` returns non-null collaborators and that the preferences helper points at the configured file; it does not pin the headless adapter path that now routes through the bundle. `src/test/java/dev/ayagmar/quarkusforge/runtime/RuntimeServicesTest.java:13-40` never exercises `RuntimeWiring.headlessGenerationService(...)` from `src/main/java/dev/ayagmar/quarkusforge/runtime/RuntimeWiring.java:23-29`, so a later change that drops bundle ownership again or adds another closeable collaborator without updating the headless shutdown path would not be caught by this slice's dedicated regression tests.

## Assumptions And Verification Context

- Review scope was limited to the files named in the request. I inspected neighboring runtime callers such as `HeadlessGenerationService` and `TuiBootstrapService` only to verify ownership and lifecycle behavior; I did not modify them.
- I treated `docs/master-execution-plan.md:447-480` as the slice contract, especially the goals around centralizing runtime assembly and making service ownership and closing rules easier to follow.
- Targeted verification passed with `./mvnw -q -Dtest=RuntimeServicesTest,TuiBootstrapServiceTest,TuiBootstrapServiceRunTest,HeadlessGenerationServiceTest,QuarkusForgeGenerateCommandTest,QuarkusForgeCliTest test`.
- I did not revert or touch any unrelated worktree content. Untracked planning docs were present under `docs/`.

## Resolution

- Resolved in Phase 7 Slice 7.3 by deleting `RuntimeWiring`, routing CLI and headless assembly through `RuntimeServices`, and making headless shutdown close an explicit runtime owner instead of implicitly relying on `HeadlessCatalogClient`.
- Additional regression coverage was added in `HeadlessGenerationServiceTest` and `RuntimeServicesTest` to pin the explicit close-owner path and the runtime-backed headless service entrypoint.
