# Bootstrap

## Build Baseline
- Java runtime baseline: Java 25.
- Build tool: Maven.
- TUI runtime dependencies: `dev.tamboui:tamboui-tui` + `dev.tamboui:tamboui-panama-backend`.
- TamboUI dependency version: `0.2.0-SNAPSHOT`.
- CLI parser baseline: `picocli`.
- Domain bootstrap defaults:
  - `groupId=org.acme`
  - `artifactId=quarkus-app`
  - `version=1.0.0-SNAPSHOT`
  - `packageName` derived from `groupId + artifactId` when omitted.

## Verification Commands
Use these gates after each issue:
- `mvn -q spotless:check`
- `mvn -q checkstyle:check`
- `mvn -q test`
- `mvn -q verify`
- `mvn -q -Pnative package`

## Archive Generation Safety
- Archive download/extraction hardening is documented in `docs/archive-safety.md`.
- Generated ZIPs are downloaded as streams to temporary files and extracted with zip-slip/symlink/zip-bomb guards.
- Submit flow uses `ProjectArchiveService` (`download -> extract -> cleanup`) with `OverwritePolicy.FAIL_IF_EXISTS`.
- Generation target path resolves as `<output-dir>/<artifact-id>` to keep `--output-dir=.` usable by default.
- TUI cancellation (`Esc` during generation) requests safe abort and ensures temporary archive cleanup.

## Core TUI Shell
- The core two-panel shell, focus routing model, and key bindings are documented in `docs/ui-shell.md`.

## Native Profile Notes
- Native profile is wired with `org.graalvm.buildtools:native-maven-plugin`.
- In this environment, `native-image` is installed at `~/.sdkman/candidates/java/25-graal/bin/native-image`, but it is not on `PATH` under the default Temurin Java runtime.
- To produce a native binary, run with `-Dnative.skip=false` and ensure GraalVM is active (or invoke Maven with `JAVA_HOME` set to GraalVM).

## Maven Repository Notes
- TamboUI dependencies are resolved from Sonatype snapshots:
  - `https://central.sonatype.com/repository/maven-snapshots/`
- `pom.xml` enables snapshots for `ossrh-snapshots` and disables releases for that repository.
- `.mvn/maven.config` sets the project-local Maven repository at `.m2/repository`.

## CLI Prefill Flags
- `--group-id`, `--artifact-id`, `--project-version`, `--package-name`, `--output-dir`
- `--output-dir` is the parent directory for the generated project root (`<output-dir>/<artifact-id>`).
- `--build-tool`, `--java-version` are validated against metadata compatibility rules before submit.
- `--search-debounce-ms` configures extension-search debounce delay in the TUI shell (`0` default for instant updates).
- `--dry-run` validates input and prints resolved initial state without starting the TUI.
- Validation rejects malformed Maven identifiers and Windows-invalid output paths (reserved names and invalid characters).
- Headless non-interactive generation is documented in `docs/cli-generate.md` (`quarkus-forge generate`).
