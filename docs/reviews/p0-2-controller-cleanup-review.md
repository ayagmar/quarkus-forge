# P0.2 Controller Cleanup Review

## Scope

- remove controller-local input effect mutation helpers that no longer need to live in `CoreTuiController`
- keep `CoreTuiController` focused on orchestration while `InputEffects` owns selector/text-input effect behavior
- add direct unit coverage for the extracted input-effect collaborator

## Findings

- No findings.

## Summary

- Reviewed the P0.2 controller cleanup across `CoreTuiController`, `InputEffects`, `UiEffectsRunner`, `UiEffectsPort`, `CatalogEffects`, and the targeted pilot/unit tests. I did not find bugs, regressions, architectural drift, duplication, or boundary violations that justify follow-up in the current slice.
- The extraction keeps selector/text-input mutation and async extension-search follow-up handling inside `InputEffects`, while `CoreTuiController` remains in the orchestration role expected for this phase.
- Coverage is adequate for the moved behavior: direct `InputEffects` tests exercise form-field edits, metadata-selector updates, and extension-search async follow-up intents, while existing controller async/search pilots still cover end-to-end status and debounce behavior.

## Verification

- `./mvnw -q -Dtest=InputEffectsTest,CoreTuiShellPilotTest,CoreTuiExtensionSearchPilotTest,CoreTuiAsyncDeterministicTest,UiStateSnapshotMapperTest,CoreUiReducerTest,CatalogEffectsTest test`
- `./mvnw -q spotless:check -DskipTests`

## Documentation

- No additional user-facing documentation gap surfaced in this slice. Behavior remains unchanged; the work is an internal ownership cleanup plus direct collaborator coverage.
