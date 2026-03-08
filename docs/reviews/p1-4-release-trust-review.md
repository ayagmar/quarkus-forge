# P1.4 Release Artifact Trust Signals Review

## Findings

1. Low: `getting-started.adoc` duplicated the release-asset choice table in both the new GitHub release guide and the checksum section, creating a second authority path for the same user decision.
2. Low: the initial checksum regression guard only pinned JVM and Linux artifact paths from `jreleaser.yml`, leaving the macOS and Windows assets described in the new docs unguarded.

## Summary

- The release config already published individual checksum assets through JReleaser, so this slice pins that contract in the existing release workflow regression test instead of adding duplicate release plumbing.
- User-facing docs now make the release asset choices and `.sha256` verification path explicit in the README, getting-started guide, and headless guide.

## Resolution

- Resolved the docs duplication by keeping the asset-selection table in the GitHub release guide and making the checksum section refer back to that single guide.
- Resolved the incomplete regression guard by extending `ReleaseWorkflowTest` to pin the macOS and Windows interactive and headless artifact paths as well.

## Verification

- `./mvnw -q -Dtest=ReleaseWorkflowTest test`
- `scripts/verify/docs-build.sh`
- `./mvnw -q spotless:check -DskipTests`
