# Phase 2 Slice 2.1 Review

## Findings

1. Medium: The new `scripts/verify` entrypoints are not self-rooting, so they only work when the caller's current directory is the repository root. Every script shells out through relative paths such as `./mvnw`, `site`, `scripts/interactive_native_smoke.py`, or `target/...` without first `cd`-ing to the repo root (`scripts/verify/coverage.sh:4`, [scripts/verify/docs-build.sh](/home/ayagmar/Projects/Personal/java/quarkus-forge/scripts/verify/docs-build.sh#L4), [scripts/verify/native-interactive-smoke-posix.sh](/home/ayagmar/Projects/Personal/java/quarkus-forge/scripts/verify/native-interactive-smoke-posix.sh#L5), [scripts/verify/native-size.sh](/home/ayagmar/Projects/Personal/java/quarkus-forge/scripts/verify/native-size.sh#L5-L37)). That undermines the stated goal of using these as shared entrypoints across CI, release, docs, and local maintainer commands: a direct invocation like `../quarkus-forge/scripts/verify/verify.sh` or a workflow step with a different working directory will fail with path-resolution errors.

2. Low: The `JustfileTest` update only guards a subset of the new delegated recipes, so several new command surfaces can drift without detection. It covers `test-unit`, `test`, `format-check`, `headless-check`, `docs-build`, and the POSIX smoke recipe, but it does not assert delegation for `coverage`, `docs-linkcheck`, `native-size`, or `native-smoke-windows` ([justfile](/home/ayagmar/Projects/Personal/java/quarkus-forge/justfile#L52-L88), [JustfileTest.java](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/JustfileTest.java#L21-L37)). Since Slice 2.1’s value is the new single-entrypoint wiring, missing guards on those recipes leaves part of the slice unprotected against accidental re-inline or recipe drift.

## Assumptions And Residual Risks

- The new `scripts/verify/` entrypoints are intentionally thin wrappers over the existing commands so Slice 2.2 can switch CI and release workflows to them without changing behavior.
- I did not treat the later `native-release-smoke.sh` wiring as a Slice 2.1 finding because it landed after this slice and belongs to the workflow-centralization follow-up.
- Even after fixing the issues above, the main residual risk for this slice remains platform-specific execution semantics when the shared scripts are invoked from CI or non-root working directories.

## Resolution

- Fixed on the branch by introducing `scripts/verify/_lib.sh`, sourcing it from every verification entrypoint, and expanding `JustfileTest` coverage across the remaining delegated recipes.
