# Phase 6 Slice 6.2 Review

## Findings

- Medium: The stored-prefill extraction is not actually usable from the real CLI entry path, so startup policy is still split across `cli/` and `application/`. `QuarkusForgeCli` still applies stored preferences itself in `src/main/java/dev/ayagmar/quarkusforge/cli/QuarkusForgeCli.java:114-115` and `src/main/java/dev/ayagmar/quarkusforge/cli/QuarkusForgeCli.java:193-274`, then calls `DefaultStartupStateService` with `storedPrefill = null` in `src/main/java/dev/ayagmar/quarkusforge/cli/QuarkusForgeCli.java:276-281`. That already misses the slice acceptance criterion that the CLI no longer own startup policy details directly. More importantly, the extracted service cannot replace that helper as written: Picocli injects non-blank defaults for group/artifact/version/output/platform/build/java in `src/main/java/dev/ayagmar/quarkusforge/cli/RequestOptions.java:45-115`, `RequestOptions.toCliPrefill()` forwards those values unchanged in `src/main/java/dev/ayagmar/quarkusforge/cli/RequestOptions.java:106-115`, and `DefaultStartupStateService.mergePrefill()` treats any non-blank requested value as authoritative in `src/main/java/dev/ayagmar/quarkusforge/application/DefaultStartupStateService.java:31-64`. If Slice 6.3 wires the CLI to the service directly, stored prefill will never win for those fields. The new service test masks this by constructing impossible blank requested values in `src/test/java/dev/ayagmar/quarkusforge/application/DefaultStartupStateServiceTest.java:31-41` instead of exercising the real `RequestOptions -> CliPrefill` path.
- Low: `docs/modules/ROOT/pages/architecture.adoc:97` overstates the migration by saying startup policy now lives under `application/` and that `DefaultStartupStateService` merges requested prefill, stored prefill, and compiled defaults. In production, stored-prefill policy is still owned by `QuarkusForgeCli`, so the architecture note is ahead of the implementation.

## Assumptions And Verification Context

- Review scope was limited to the startup policy extraction files named in the request plus the adjacent architecture/review docs. I did not revert or modify any other worktree changes.
- I used `docs/master-execution-plan.md:380-424` as the slice contract, especially the Slice 6.2 goal to move existing startup decision logic out of the CLI and the acceptance criterion that CLI no longer owns startup policy details directly.
- Targeted verification passed with `./mvnw -q -Dtest=DefaultStartupStateServiceTest,LiveStartupMetadataLoaderTest,QuarkusForgeCliStartupMetadataTest,QuarkusForgeCliPrefillTest test`.
- I also inspected the current `RequestOptions` explicitness/defaulting contract to check whether the new service can preserve the existing stored-prefill semantics once callers are rewired.

## Resolution

- The medium finding is resolved in Phase 6 Slice 6.3 by routing `RequestOptions.toExplicitCliPrefill()` into `StartupStateService`, removing the CLI-side stored-prefill mutation helper, and passing stored prefill as service input only for the non-dry-run TUI path.
- The low documentation finding is resolved in Phase 6 Slice 6.3 by narrowing the architecture note to the actual explicit-override plus stored-prefill contract now used in production.
