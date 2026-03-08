# Phase 2 Slice 2.2 Review

## Findings

1. High: The release workflow delegation is currently broken, so the native packaging job will fail before it reaches any real smoke verification. `.github/workflows/release.yml` now calls `scripts/verify/release-native-smoke.sh "$binary"`, but the script added in this slice is actually `scripts/verify/native-release-smoke.sh`, it requires a second `mode` argument, and it is not executable in the current tree (`-rw-r--r--`). The new `ReleaseWorkflowTest` mirrors the broken workflow string rather than the real script contract, so the regression test will still pass while `release.yml` fails at runtime. This is a release-blocking regression for Phase 2.2, not just a missing polish item.

## Assumptions And Residual Risks

- I did not find a separate user-facing documentation gap for this slice because the changed surface here is CI/release wiring, not runtime behavior or a documented maintainer command yet.
- The CI workflow delegation looks coherent against the current `scripts/verify` surface. The main unresolved risk is the broken release-smoke handoff described above.

## Resolution

- Fixed on the branch by aligning the workflow, regression test, and shared wrapper on the tracked executable `scripts/verify/native-release-smoke.sh` contract. The release workflow now passes the required mode explicitly for headless, POSIX interactive, and Windows interactive binaries.
