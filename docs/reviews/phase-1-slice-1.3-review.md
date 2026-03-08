# Phase 1 Slice 1.3 Review

## Findings

1. High: The Windows release step runs the new interactive smoke through a Unix-style pipe (`.github/workflows/release.yml:143-144`), but the smoke path itself depends on `TuiRunner.create(...)` acquiring a real terminal and a usable terminal size (`src/main/java/dev/ayagmar/quarkusforge/runtime/TuiBootstrapService.java:110-152`). In local verification, the same smoke path failed with `Failed to get terminal size` when it was not launched under a properly initialized terminal. Piping through `tee` is exactly the kind of wrapper that can change console/TTY semantics on CI. That makes this step likely to be flaky or outright fail on `windows-latest` even when the native binary is healthy. The safer path is to preserve the real console for the smoke invocation and capture logs without interposing a pipe, or use a Windows-native transcript/logging mechanism that keeps the console attached.

## Assumptions And Residual Risks

- I did not find a user-facing documentation gap for this slice because the new flag is hidden and the change is release-workflow-only.
- The runtime and workflow tests cover the new session helper and workflow wiring, but they do not prove the exact `windows-latest` console behavior. That remains the main unresolved risk after this review.

## Resolution

- Fixed on the branch by removing the `tee` pipe from the Windows interactive smoke path. The workflow and shared Windows smoke script now preserve the console during execution, capture diagnostics via stderr redirection, and replay the log only on failure or when the render-ready marker is missing.
