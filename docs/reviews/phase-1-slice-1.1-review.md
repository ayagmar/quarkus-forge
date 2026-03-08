# Phase 1 Slice 1.1 Review

## Findings

1. Medium: `src/test/java/dev/ayagmar/quarkusforge/ReleaseWorkflowTest.java` only asserts that `release.yml` still contains the Linux-gated smoke step and helper invocation text. It does not assert that the native release matrix still includes a Linux interactive `quarkus-forge` row, which is the other half of the feature contract. A later matrix edit could remove or repurpose that row, leaving the new PTY smoke step permanently unreachable while this regression test still passes. That would silently drop the slice's intended Linux startup coverage from release packaging.

## Assumptions And Residual Risks

- No user-facing documentation gap is evident for this slice because the change is limited to internal release verification.
- The helper test exercises a fake PTY-driven shell program rather than a real native image, so native-image-specific terminal behavior on GitHub's Linux runner remains a residual risk until the release workflow is the source of truth.

## Resolution

- Added an explicit regression assertion in `src/test/java/dev/ayagmar/quarkusforge/ReleaseWorkflowTest.java` for the Linux interactive `quarkus-forge` matrix row so the test now fails if that release smoke coverage becomes unreachable.
