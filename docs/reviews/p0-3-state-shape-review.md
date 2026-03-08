# P0.3 State Shape Review

## Scope

- make extension selection semantics an explicit grouped part of reducer-owned state
- replace brittle positional `ExtensionView` construction with the semantic snapshot factory
- keep reducer/controller code on semantic accessors rather than raw constructor order

## Findings

- No findings.

## Summary

- `UiState.ExtensionView` now groups list-focus semantics under `SelectionView`, and the inspected controller, reducer, and touched tests consistently route construction through `UiState.ExtensionView.snapshot(...)` instead of brittle positional constructor calls.
- I did not find slice-local regressions, architectural drift, duplication, boundary violations, missing regression coverage, or documentation gaps in the reviewed scope.

## Verification

- `./mvnw -q -Dtest=CoreUiReducerTest,UiStateSnapshotMapperTest,InputEffectsTest test`
- `./mvnw -q -Dtest=CoreTuiExtensionSearchPilotTest test`
- `./mvnw -q spotless:check -DskipTests`  -> fails on a pre-existing formatting violation in `src/test/java/dev/ayagmar/quarkusforge/ui/UiEventRouterTest.java`, outside this slice

## Notes

- No user-facing doc change was needed for this slice because runtime behavior did not change. The work hardens reducer state ownership and test readability.
