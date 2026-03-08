# P0.1 Extension Flow Reducer Ownership Review

## Scope

- move the remaining extension key-classification path out of `CoreTuiController`
- keep reducer/effects as the only authority for extension-list semantics
- carry the minimum semantic extension state needed for reducer decisions

## Findings

- No findings.

## Summary

- Reviewed the remaining extension-flow ownership path across `CoreTuiController`, `CoreUiReducer`, `UiIntent`, `UiState`, and the listed reducer/shell/snapshot tests. I did not find bugs, regressions, architectural drift, duplication, or boundary violations in the current slice.
- The extension keyboard flow is now reducer-classified for focus handoff, escape unwind order, and shared shortcut routing, while controller code is limited to event dispatch plus effect/runtime bridging.
- The folded Windows backend fix also looks sound in the reviewed code: backend selection now normalizes `os.name` before matching Windows, and the targeted bootstrap tests cover Linux, Darwin, and Windows expectations.

## Verification

- `./mvnw -q -Dtest=CoreUiReducerTest,CoreTuiShellPilotTest,UiStateSnapshotMapperTest,TuiBootstrapServiceTest test`
- `./mvnw -q spotless:check -DskipTests`

## Documentation

- No additional user-facing documentation gap surfaced in this slice. Behavior remains unchanged; the change is an ownership cleanup plus regression coverage expansion.
