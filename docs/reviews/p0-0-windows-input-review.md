# P0.0 Windows Terminal Input Review

## Findings

- Async review pending.

## Local Verification

- `./mvnw -q -Dtest=TuiBootstrapServiceTest,TuiBootstrapServiceRunTest,QuarkusForgeCliBindingsProfileTest test`
- `scripts/verify/docs-build.sh`
- `./mvnw -q spotless:check -DskipTests`

## Local Notes

- The current app was forcing `tamboui.backend=panama` for all interactive runs.
- The installed TamboUI Panama Windows terminal reads character payloads from console input records but does not surface non-character key events, which matches the Windows arrow-key degradation symptom.
- The slice fixes backend selection at the runtime bootstrap layer by defaulting Windows to `jline3` while preserving explicit backend overrides.
