# P0.3 State Shape Review

## Scope

- make extension selection semantics an explicit grouped part of reducer-owned state
- replace brittle positional `ExtensionView` construction with the semantic snapshot factory
- keep reducer/controller code on semantic accessors rather than raw constructor order

## Findings

- Pending async review

## Local Verification

- `./mvnw -q -Dtest=CoreUiReducerTest,UiStateSnapshotMapperTest,InputEffectsTest test`
- `./mvnw -q spotless:check -DskipTests`

## Notes

- No user-facing doc change was needed for this slice because runtime behavior did not change. The work hardens reducer state ownership and test readability.
