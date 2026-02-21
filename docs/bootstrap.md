# Bootstrap

## Build Baseline
- Java runtime baseline: Java 25.
- Build tool: Maven.
- TUI runtime dependencies: `dev.tamboui:tamboui-tui` + `dev.tamboui:tamboui-panama-backend`.
- CLI parser baseline: `picocli`.

## Verification Commands
Use these gates after each issue:
- `mvn -q spotless:check`
- `mvn -q checkstyle:check`
- `mvn -q test`
- `mvn -q verify`
- `mvn -q -Pnative package`

## Native Profile Notes
- Native profile is wired with `org.graalvm.buildtools:native-maven-plugin`.
- In this environment, `native-image` is not installed, so `native.skip=true` is the default.
- To produce a native binary when GraalVM is available, run with `-Dnative.skip=false`.

## Offline Maven Notes
- `.mvn/settings.xml` mirrors repositories to the local Maven cache path.
- `.mvn/maven.config` sets the project-local Maven repository at `.m2/repository`.
