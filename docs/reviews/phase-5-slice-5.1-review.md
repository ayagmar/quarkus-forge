# Phase 5 Slice 5.1 Review

## Findings

### Medium - The new ownership map marks reducer-visible state as fully render-only

- `UiStateOwnership` and the maintained architecture page classify `metadataPanel` and `startupOverlay` as render-only slices (`src/main/java/dev/ayagmar/quarkusforge/ui/UiStateOwnership.java:41-72`, `docs/modules/ROOT/pages/architecture.adoc:161-175`), but the reducer still treats parts of both as authoritative state. `CoreUiReducer` reads `metadataPanel().platformStreamInfo()`, `buildToolInfo()`, and `javaVersionInfo()` to decide whether selector navigation is allowed (`src/main/java/dev/ayagmar/quarkusforge/ui/CoreUiReducer.java:784-792`), and it owns `startupOverlay.visible()` across catalog load intents (`src/main/java/dev/ayagmar/quarkusforge/ui/CoreUiReducer.java:22-85`). The new map therefore documents a stricter boundary than the code actually implements, which is architectural drift in a slice whose purpose is to define ownership before the next extraction steps.
- The slice's dedicated test suite does not catch that mismatch. `UiStateOwnershipTest` only proves that every top-level `UiState` field is listed once and that the hand-written render-only labels stay hand-written (`src/test/java/dev/ayagmar/quarkusforge/ui/UiStateOwnershipTest.java:11-31`). It never cross-checks the classification against reducer usage, so the false source of truth can land cleanly and mislead the follow-on render-model refactor.

## Assumptions And Residual Risks

- Review scope was commit `744725d` (`refactor(ui): define ui state ownership`) with the current worktree used as context for adjacent UI-state/render-model changes. I left unrelated dirty and untracked files untouched.
- I did not find a separate runtime regression in the ownership registry itself beyond the boundary misclassification above.
- Targeted verification passed with `./mvnw -q -Dtest=UiStateOwnershipTest,CoreUiReducerTest,UiStateSnapshotMapperTest,UiRenderStateAssemblerTest test` and `./mvnw -q spotless:check -DskipTests`.
- Residual risk is that the ongoing Phase 5 render-model extraction can amplify this mismatch if the next slice treats `metadataPanel` or the whole `startupOverlay` record as safe to move out of reducer-visible state before reducer dependencies are removed.

## Resolution

- Resolved in Phase 5 Slice 5.3 by removing render-only `metadataPanel`, `extensionsPanel`, `footer`, `generation`, and `startupOverlay` from `UiState`, updating `CoreUiReducer` to stop depending on render snapshots, and moving metadata-selector availability into `UiIntent.MetadataInputIntent`.
- `UiStateOwnership` and the architecture page now describe the post-split shape: all current `UiState` fields are reducer-owned, and render-only slices live in `UiRenderModel`.
