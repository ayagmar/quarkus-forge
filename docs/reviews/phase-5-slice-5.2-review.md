# Phase 5 Slice 5.2 Review

## Findings

### Medium - `UiRenderModel` still carries duplicate authority for the render-only slices it is supposed to isolate

- `UiRenderModel` stores a full `UiState reducerState` plus separate `metadataPanel`, `generation`, and `startupOverlay` fields (`src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderModel.java:5-11`). `UiRenderStateAssembler.renderModel()` feeds that record with a `UiState` already mutated by `reduceInputState(...)`, which has already overlaid those same render-only slices onto the state (`src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:62-74`, `src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:77-90`). `UiStateSnapshotMapper.renderModel()` then copies the same values back out of that augmented state into the record's top-level fields (`src/main/java/dev/ayagmar/quarkusforge/ui/UiStateSnapshotMapper.java:8-16`). The new type therefore documents a split, but it still encodes two sources of truth for the same data.
- That duplication is observable in behavior: `snapshotState()` reconstructs `metadataPanel`, `generation`, and `startupOverlay` from the record fields while retaining the rest of `reducerState` (`src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderModel.java:21-26`). A future caller can therefore create a mixed snapshot simply by passing an inconsistent `reducerState` and top-level slices, and there is no invariant or regression test that rejects that state. The new tests instead lock in the duplication by asserting that the duplicated fields are equal under the current happy-path mapper usage (`src/test/java/dev/ayagmar/quarkusforge/ui/UiStateSnapshotMapperTest.java:164-172`, `src/test/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssemblerTest.java:97-106`). This is architectural drift relative to the updated documentation, which now says `UiRenderModel` is the explicit container for these slices while the codebase transitions away from storing them on `UiState` (`docs/modules/ROOT/pages/architecture.adoc:175-177`).

## Assumptions And Residual Risks

- During review, Git no longer showed tracked worktree deltas for the slice files; the `UiRenderModel`/mapper/assembler/tests/docs state reviewed here matched the current `codex/master-plan-execution` branch contents, with unrelated untracked planning docs left untouched.
- I did not find a separate user-visible runtime regression in the current happy path beyond the boundary/duplication issue above.
- Targeted verification passed with `./mvnw -q -Dtest=UiStateSnapshotMapperTest,UiRenderStateAssemblerTest test` and `./mvnw -q spotless:check -DskipTests`.
- Residual risk is that follow-on slices may start consuming `UiRenderModel` as if it were already the single render-state authority, which would make the duplicated `UiState` payload harder to unwind and could let stale render-only data leak across future integration points.

## Resolution

- Resolved in Phase 5 Slice 5.3 by making `UiState` reducer-only, deleting `UiRenderModel.snapshotState()`, removing `UiRenderStateAssembler.reduceInputState(...)`, and having `UiStateSnapshotMapper` assemble `UiRenderModel` directly from pure reducer state plus explicit render slices.
- The renderer now consumes `UiRenderModel` directly, so there is one authority for render-only slices and no hybrid snapshot reconstruction path remains.
