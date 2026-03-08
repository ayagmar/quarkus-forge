# Phase 5 Slice 5.3 Review

## Findings

### Medium - `generationVisible` is still mutated outside the reducer even though this slice documents overlay visibility as reducer-owned

- The updated architecture page says overlay visibility flags now "update through reducer intents/effects and live in reducer-owned `UiState`" and `UiStateOwnership` classifies `overlays` as reducer-owned semantic state (`docs/modules/ROOT/pages/architecture.adoc:159-175`, `src/main/java/dev/ayagmar/quarkusforge/ui/UiStateOwnership.java:41-56`). The controller still bypasses that boundary: `syncReducerRuntimeState()` writes `reducerState = reducerState.withGenerationOverlayVisible(...)`, and that imperative mutation runs from `uiState()`, `renderModel()`, and before every dispatch loop rather than via a reducer intent (`src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:630-637`, `src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:872-885`, `src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:1208-1212`).
- That leaves the slice with architectural drift at its main boundary: startup overlay visibility now flows through reducer transitions, but generation overlay visibility still changes through controller-side state rewriting. The dedicated ownership test suite does not catch that mismatch because it only checks record-component coverage and that every listed `UiState` field is tagged reducer-owned (`src/test/java/dev/ayagmar/quarkusforge/ui/UiStateOwnershipTest.java:11-28`).

### Low - Selector availability now lives in an implicit controller-to-reducer intent contract, and the empty-options path is only unit-tested with fabricated booleans

- `MetadataInputIntent` now carries `boolean optionsAvailable`, populated by `CoreTuiController.handleMetadataSelectorFlow()` from `MetadataSelectorManager.optionsFor(...)` (`src/main/java/dev/ayagmar/quarkusforge/ui/UiIntent.java:87-89`, `src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:552-555`, `src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:1215-1217`). `CoreUiReducer.reduceMetadataInput()` then trusts that controller-supplied boolean while ignoring the intent's own `focusTarget` and deriving the target from reducer state instead (`src/main/java/dev/ayagmar/quarkusforge/ui/CoreUiReducer.java:657-682`).
- That does remove render-only selector info from `UiState`, but it replaces it with an ad-hoc boundary leak: the reducer can no longer determine selector navigability from its own state or from `UiRenderModel`; it depends on the caller to precompute and serialize that answer correctly. The new reducer tests lock in the synthetic contract by manually passing `true` and `false`, while the controller-level selector coverage only exercises the happy path with populated options (`src/test/java/dev/ayagmar/quarkusforge/ui/CoreUiReducerTest.java:670-720`, `src/test/java/dev/ayagmar/quarkusforge/ui/CoreTuiShellPilotTest.java:1243-1271`). I did not find a controller-level regression test for the empty-selector case after this contract shift.

## Assumptions And Verification Context

- Review scope was the current uncommitted Slice 5.3 worktree on `codex/master-plan-execution`, focused on the `UiState` render-field removal and `UiRenderModel` render-path switch. I left unrelated dirty and untracked files untouched.
- Targeted verification passed with `./mvnw -q -Dtest=CoreUiReducerTest,UiRenderStateAssemblerTest,UiStateOwnershipTest,UiStateSnapshotMapperTest,CoreTuiShellPilotTest test` and `./mvnw -q spotless:check -DskipTests`.
- Residual risk is that follow-on slices may treat the current ownership/docs as fully settled even though generation overlay visibility and metadata-selector availability still cross the reducer boundary through controller-managed synchronization.

## Resolution

- The medium finding was resolved in the next slice by introducing `UiIntent.GenerationOverlayVisibilityIntent` and updating `CoreTuiController.syncReducerRuntimeState()` to synchronize `generationVisible` through `CoreUiReducer` instead of directly rewriting `UiState`.
- The low finding is still a residual risk. The controller-provided `optionsAvailable` flag remains part of the metadata-selector boundary, and it should be revisited if a later slice touches selector routing again.
