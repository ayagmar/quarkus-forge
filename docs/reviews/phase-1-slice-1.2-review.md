# Phase 1 Slice 1.2 Review

## Findings

1. Medium: `src/test/java/dev/ayagmar/quarkusforge/ReleaseWorkflowTest.java` only validates that the workflow condition changed from Linux-only to `runner.os != 'Windows'`. It does not assert that the release matrix still contains a macOS interactive `quarkus-forge` artifact entry, which is the other half of the slice's behavior. A later matrix edit could drop or repurpose the macOS interactive row while this test still passes, silently removing the intended macOS smoke coverage.

## Assumptions And Residual Risks

- The slice intentionally reuses the existing POSIX PTY smoke helper rather than adding a macOS-specific path.
- Residual risk remains around GitHub-hosted macOS runner PTY behavior and `python3` availability, which cannot be reproduced from this Linux workspace and still needs CI confirmation.
- No user-facing documentation gap is evident here because the change is limited to internal release workflow coverage.

## Resolution

- Added an explicit regression assertion in `src/test/java/dev/ayagmar/quarkusforge/ReleaseWorkflowTest.java` for the macOS interactive `quarkus-forge` matrix row so the test now fails if that release smoke coverage is removed.
