# P1.4 Release Artifact Trust Signals Review

## Findings

- Pending async review.

## Summary

- The release config already published individual checksum assets through JReleaser, so this slice pins that contract in the existing release workflow regression test instead of adding duplicate release plumbing.
- User-facing docs now make the release asset choices and `.sha256` verification path explicit in the README, getting-started guide, and headless guide.

## Verification

- `./mvnw -q -Dtest=ReleaseWorkflowTest test`
- `scripts/verify/docs-build.sh`
- `./mvnw -q spotless:check -DskipTests`
