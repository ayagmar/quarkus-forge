# P0.2 Controller Cleanup Review

## Scope

- remove controller-local input effect mutation helpers that no longer need to live in `CoreTuiController`
- keep `CoreTuiController` focused on orchestration while `InputEffects` owns selector/text-input effect behavior
- add direct unit coverage for the extracted input-effect collaborator

## Findings

- Pending async review

## Local Verification

- `./mvnw -q -Dtest=InputEffectsTest,CoreTuiShellPilotTest,UiStateSnapshotMapperTest,CoreUiReducerTest test`
- `./mvnw -q spotless:check -DskipTests`

## Notes

- No user-facing doc change was needed for this slice because the behavior did not change. The work is an internal authority/ownership cleanup plus direct unit coverage for the extracted collaborator.
