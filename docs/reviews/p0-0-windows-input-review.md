# P0.0 Windows Terminal Input Review

## Findings

1. Fixed: `src/main/java/dev/ayagmar/quarkusforge/runtime/TuiBootstrapService.java` no longer detects Windows with a broad `contains("win")` check. The selector now normalizes `os.name` and only treats names starting with `windows` as Windows, which prevents `Darwin` from being misclassified and preserves the intended JLine-on-Windows / Panama-elsewhere contract.

2. Fixed: `src/test/java/dev/ayagmar/quarkusforge/TuiBootstrapServiceTest.java` now includes a negative `Darwin` regression case in addition to the existing Linux and Windows checks, so the backend selector cannot silently regress back to substring matching.

## Assumptions And Verification Context

- `./mvnw -q -Dtest=TuiBootstrapServiceTest,TuiBootstrapServiceRunTest,QuarkusForgeCliBindingsProfileTest test`
- `scripts/verify/docs-build.sh`
- `./mvnw -q spotless:check -DskipTests`
- I did not find app-side Windows key normalization added in this slice. The change stays in runtime bootstrap/backend selection, and the troubleshooting doc now reflects that contract.
- The original review finding has been folded back into the branch and reverified.
