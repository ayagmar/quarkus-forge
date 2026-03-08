# Phase 9 Slice 9.1 Review

## Findings

- Medium: `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:163` adds `onlyPostTuiActionExecutorMayDependOnRuntime`, but the runtime package already reaches into `postgen` through `TuiBootstrapService` and `IdeDetector` (`src/main/java/dev/ayagmar/quarkusforge/runtime/TuiBootstrapService.java:11`). Allowing `PostTuiActionExecutor` to depend back on `runtime` (`src/main/java/dev/ayagmar/quarkusforge/postgen/PostTuiActionExecutor.java:7`) formalizes a `runtime <-> postgen` package cycle instead of protecting the boundary. That is architectural drift, and this test suite will now bless it rather than catch it.
- Low: `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:121`, `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:134`, and `src/test/java/dev/ayagmar/quarkusforge/HeadlessArchitectureRulesTest.java:168` exempt classes by simple name only. A same-named class added in any subpackage would be silently whitelisted, which makes these rules brittle and easy to bypass accidentally. The exceptions should be tied to fully qualified classes or a tighter boundary mechanism.

## Assumptions / Verification Context

- Reviewed the current production dependencies in `application`, `headless`, `runtime`, and `postgen`, plus the package-level architecture notes, to compare the new ArchUnit rules against the intended layer boundaries.
- Verified locally with `./mvnw -q -Dtest=HeadlessArchitectureRulesTest,RuntimeServicesTest,HeadlessCliTest,QuarkusForgeCliTest,PostTuiActionExecutorTest test` and `./mvnw -q spotless:check -DskipTests`.

## Resolution

- Moved `TuiSessionSummary` from `runtime/` into `postgen/` so the post-generation seam no longer depends back on runtime and the `runtime <-> postgen` cycle is removed instead of codified.
- Replaced the simple-name-based ArchUnit exceptions with fully qualified name checks so the ownership rules apply to exact classes rather than any future same-named type.
