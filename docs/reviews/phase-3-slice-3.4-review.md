# Phase 3 Slice 3.4 Review

## Findings

### Low - Generated-project path authority cleanup is still only partial

- `OutputPathResolver.resolveGeneratedProjectDirectory(...)` is introduced as the shared path authority in `src/main/java/dev/ayagmar/quarkusforge/util/OutputPathResolver.java:26`, and `UiRenderStateAssembler` already calls it directly in `src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:216` and `src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:279`. But `CoreTuiController` still keeps a same-purpose wrapper that just forwards to that utility in `src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:1133`. That leaves two authorities for the same policy and makes the cleanup easy to regress in later slices. This bundled cleanup should remove the redundant controller helper so callers depend on the resolver directly.

### Low - Extension interaction extraction still crosses the catalog-effects boundary

- `ExtensionEffects` is supposed to own extension command execution, but `CLEAR_SEARCH` still delegates into `CatalogEffects.clearSearchFilter(...)` in `src/main/java/dev/ayagmar/quarkusforge/ui/ExtensionEffects.java:42` while the actual mutation logic remains in `src/main/java/dev/ayagmar/quarkusforge/ui/CatalogEffects.java:94`. That keeps slice 3.4 coupled to slice 3.2 internals and leaves extension interaction behavior split across `ExtensionEffects`, `ExtensionInteractionHandler`, and `CatalogEffects`. The extraction is functionally fine, but the boundary is still blurred enough that future extension-command changes will need cross-helper edits.

## Assumptions And Residual Risks

- Review scope is commit `c82118c` (`refactor(ui): extract extension effects`), which also includes the bundled generated-project path authority cleanup.
- I did not find a slice-specific functional regression in the extracted extension command and navigation paths from code inspection.
- Automated verification at `HEAD` is currently blocked by an unrelated compile break from generation-state references still pointing at `CoreTuiController.GenerationState`, so I could not confirm this slice by running the targeted Maven tests in the current branch state.
- I did not identify a user-facing documentation gap for this slice because the changes are internal orchestration refactors rather than CLI or TUI behavior changes.

## Resolution

- Removed the redundant `CoreTuiController.resolveGeneratedProjectDirectory(...)` wrapper and updated controller call sites to depend on `OutputPathResolver.resolveGeneratedProjectDirectory(...)` directly, so the generated-project path now follows a single resolver authority.
- Kept the `CLEAR_SEARCH` cross-helper note as a low-severity follow-up for later cleanup because resolving it cleanly would cut across the current phase boundary after slice 3.4 was already verified and committed.
