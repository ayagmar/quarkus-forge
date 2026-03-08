# P0.1 Extension Flow Reducer Ownership Review

## Scope

- move the remaining extension key-classification path out of `CoreTuiController`
- keep reducer/effects as the only authority for extension-list semantics
- carry the minimum semantic extension state needed for reducer decisions

## Findings

- Pending async review

## Local Verification

- `./mvnw -q -Dtest=CoreUiReducerTest,CoreTuiShellPilotTest,UiStateSnapshotMapperTest,TuiBootstrapServiceTest test`
- `./mvnw -q spotless:check -DskipTests`

## Notes

- No user-facing doc change was needed for this slice because behavior stayed the same; the change removes controller-local authority and adds reducer-level regression coverage for the existing shortcuts and focus handoff rules.
