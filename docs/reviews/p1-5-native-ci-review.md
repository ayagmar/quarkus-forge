# P1.5 Native And CI Follow-Up Coverage Review

## Scope

- tighten CI workflow regression coverage around native-size artifact uploads and final gating
- add one more malformed native build-report fixture for `CheckNativeSize`
- keep the slice coverage-only, with no runtime or workflow behavior changes

## Findings

- Pending async review

## Local Verification

- `./mvnw -q -Dtest=CiWorkflowTest,CheckNativeSizeScriptTest test`
- `./mvnw -q spotless:check -DskipTests`

## Notes

- No user-facing doc change was needed for this slice because the behavior stayed the same. The work improves confidence in existing CI/native verification contracts.
