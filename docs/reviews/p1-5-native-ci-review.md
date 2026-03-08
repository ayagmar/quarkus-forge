# P1.5 Native And CI Follow-Up Coverage Review

## Findings

### Medium - The new CI gate test still leaves the branch-protection-critical `if: always()` contract unguarded

- The slice claims to tighten final-gate coverage, but [`CiWorkflowTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/CiWorkflowTest.java#L28) only asserts the `needs` list and [`CiWorkflowTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/CiWorkflowTest.java#L29) only asserts that `needs.native-size.result` appears somewhere in the workflow text. The actual branch-protection contract also depends on [`ci.yml`](/home/ayagmar/Projects/Personal/java/quarkus-forge/.github/workflows/ci.yml#L201) keeping `if: always()` on `ci-status`; if that line is removed, the gate job is skipped when an upstream job fails, and required-check enforcement degrades even though the current test still passes. For a slice specifically targeting final CI gating, that missing assertion leaves the highest-impact regression path untested.

### Low - The malformed build-report expansion still skips one of the parser's three required sections

- [`CheckNativeSize.parseReport(...)`](/home/ayagmar/Projects/Personal/java/quarkus-forge/scripts/CheckNativeSize.java#L79) requires all three report fields: image total, code area, and image heap, with the section lookups happening in [`CheckNativeSize.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/scripts/CheckNativeSize.java#L85) and [`CheckNativeSize.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/scripts/CheckNativeSize.java#L121). The test suite now covers a malformed body, missing image heap, and missing image total in [`CheckNativeSizeScriptTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/scripts/CheckNativeSizeScriptTest.java#L104), [`CheckNativeSizeScriptTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/scripts/CheckNativeSizeScriptTest.java#L128), and [`CheckNativeSizeScriptTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/scripts/CheckNativeSizeScriptTest.java#L152), but there is still no regression test for a report missing `Code Area`. That keeps one required parse failure branch uncovered despite the slice's explicit goal of broadening malformed-report coverage.

## Summary

- I did not find a runtime bug, workflow behavior regression, duplication issue, or user-facing documentation gap in the reviewed slice beyond the two missing-test gaps above.
- The added fixture [`missing-image-total-build-report.html`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/resources/native-size/fixtures/missing-image-total-build-report.html) is aligned with the current parser contract and correctly exercises the missing-image-total failure path.

## Resolution

- Resolved by asserting the `ci-status` job keeps `if: always()` in [`CiWorkflowTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/CiWorkflowTest.java).
- Resolved by adding the missing `Code Area` malformed-report fixture and regression in [`CheckNativeSizeScriptTest.java`](/home/ayagmar/Projects/Personal/java/quarkus-forge/src/test/java/dev/ayagmar/quarkusforge/scripts/CheckNativeSizeScriptTest.java).

## Verification

- `./mvnw -q -Dtest=CiWorkflowTest,CheckNativeSizeScriptTest test`
- `./mvnw -q spotless:check -DskipTests`
