# Phase 3 Slice 3.1 Review

## Findings

1. Medium: `src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:273` and `src/main/java/dev/ayagmar/quarkusforge/ui/CoreTuiController.java:1192` now each carry their own `resolveGeneratedProjectDirectory(...)` implementation. Slice 3.1 was supposed to extract render-state assembly cleanly, but this leaves duplicate authority for the generated-project path. If either copy changes later, the footer/pre-generation plan can drift from the actual generation target used by the controller, which is exactly the kind of mixed-path behavior this refactor was trying to remove.

2. Low: `src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:69` and `src/main/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssembler.java:210` moved several runtime-derived branches into the new assembler, but `src/test/java/dev/ayagmar/quarkusforge/ui/UiRenderStateAssemblerTest.java:38` only covers a happy-path snapshot and a render-context lookup. There is no direct collaborator-level regression test for cancellation state propagation, footer suppression while overlays/post-generation are visible, or the footer error-detail fallback paths. Those behaviors are still reachable through broader controller tests, but this extraction increased the surface area of assembler-owned logic without pinning the new branch points close to the collaborator.

## Assumptions And Residual Risks

- `./mvnw -q -Dtest=UiRenderStateAssemblerTest,UiStateSnapshotMapperTest test` passes in the current worktree, so I did not find a concrete render-state regression in the exercised paths.
- I assumed Phase 3 Slice 3.1 remains an internal refactor only. I did not identify a user-facing documentation gap from this slice alone because it does not change CLI or TUI semantics.
- Residual risk remains around future drift between the controller’s generation target path and the assembler’s displayed path until the duplicate authority is collapsed.

## Resolution

- Collapsed generated-project path resolution onto `dev.ayagmar.quarkusforge.util.OutputPathResolver.resolveGeneratedProjectDirectory(ProjectRequest)`, and updated both `CoreTuiController` and `UiRenderStateAssembler` to use that shared utility so the rendered target path and actual generation target now follow one authority path.
