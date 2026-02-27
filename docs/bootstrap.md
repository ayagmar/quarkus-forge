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
- Native profile is wired with `org.graalvm.buildtools:native-maven-plugin` (`0.11.4`) and compiles the native image during `package` when `-Dnative.skip=false` is set.
- In this environment, `native-image` is installed at `~/.sdkman/candidates/java/25-graal/bin/native-image`, but it is not on `PATH` under the default Temurin Java runtime.
- To produce a native binary, run with GraalVM active:
  - `JAVA_HOME=$HOME/.sdkman/candidates/java/25-graal PATH=$JAVA_HOME/bin:$PATH mvn -q -Pnative -Dnative.skip=false package`
- Produced binary path: `target/quarkus-forge`.
- JVM TUI runtime backend policy:
  - With `--enable-native-access=ALL-UNNAMED`, backend preference is `panama,jline3`.
  - Without native-access flag, runtime prefers `jline3` and prints guidance; some terminal-native warnings may still appear depending on environment.
- Native image build includes `--enable-native-access=ALL-UNNAMED` during compilation so the produced binary does not require extra launcher flags for terminal FFM access.

## Maven Repository Notes
- TamboUI dependencies are resolved from Sonatype snapshots:
  - `https://central.sonatype.com/repository/maven-snapshots/`
- `pom.xml` enables snapshots for `ossrh-snapshots` and disables releases for that repository.
- `.mvn/maven.config` sets the project-local Maven repository at `.m2/repository`.

## CLI Prefill Flags
- `--group-id`, `--artifact-id`, `--project-version`, `--package-name`, `--output-dir`
- `--output-dir` is the parent directory for the generated project root (`<output-dir>/<artifact-id>`).
- `--platform-stream`, `--build-tool`, `--java-version` are validated against metadata compatibility rules before submit.
- In the TUI, `platformStream`, `buildTool`, and `javaVersion` are selector fields driven by metadata options.
- `--search-debounce-ms` configures extension-search debounce delay in the TUI shell (`0` default for instant updates).
- `--dry-run` validates input and prints resolved initial state without starting the TUI.
- Validation rejects malformed Maven identifiers and Windows-invalid output paths (reserved names and invalid characters).
- Headless non-interactive generation is documented in `docs/cli-generate.md` (`quarkus-forge generate`).
