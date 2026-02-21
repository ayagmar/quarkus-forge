# Performance and Footprint Baseline

## Reference Measurement Profile
- Captured at: `2026-02-21T21:18:09Z` (UTC)
- OS: `Linux 6.19.2-2-cachyos x86_64`
- CPU: `Intel(R) Core(TM) Ultra 9 185H` (`22` logical CPUs)
- Memory: `62 GiB` total
- Terminal: `TERM=xterm-kitty`, `COLORTERM=truecolor`

## Artifact Footprint Baseline
- Shaded JVM artifact: `target/quarkus-forge.jar` = `5,026,763` bytes (`~4.8 MiB`)
- Original unshaded artifact: `target/original-quarkus-forge.jar` = `5,021,831` bytes
- Main project artifact: `target/quarkus-forge-0.1.0-SNAPSHOT.jar` = `80,681` bytes
- Native artifact baseline: not available in this environment (`native-image` unavailable)

Regression budget policy:
- Shaded JAR regression budget: `+10%` max from baseline
- Native artifact regression budget: `+10%` max from baseline (when native baseline exists)

## Runtime Dependency Inventory (Baseline)
Captured from `target/runtime-dependency-tree.txt`.

| Dependency | Why runtime-relevant |
|---|---|
| `dev.tamboui:tamboui-tui` | Core terminal UI runtime and event loop |
| `dev.tamboui:tamboui-core` | TamboUI core rendering/layout primitives |
| `dev.tamboui:tamboui-widgets` | Widgets used by shell UI (`TextInput`, `ListWidget`, `Paragraph`, etc.) |
| `dev.tamboui:tamboui-annotations` | Annotation dependency from TamboUI stack |
| `dev.tamboui:tamboui-panama-backend` | Primary high-performance terminal backend |
| `dev.tamboui:tamboui-jline3-backend` | Runtime fallback backend when Panama terminal access is unavailable |
| `org.jline:jline` | Required by JLine backend fallback |
| `info.picocli:picocli` | CLI argument parsing and command model |
| `com.fasterxml.jackson.core:jackson-databind` | JSON mapping for Quarkus API payloads |
| `com.fasterxml.jackson.core:jackson-annotations` | Jackson annotations support |
| `com.fasterxml.jackson.core:jackson-core` | Jackson streaming core |

## Dependency Audit (Current vs Latest Stable)
Latest versions checked via Maven Central metadata (`maven-metadata.xml`) and official project release pages/changelogs.

| Artifact | Current | Latest Stable (2026-02-21) | Decision |
|---|---:|---:|---|
| `dev.tamboui:tamboui-*` | `0.1.0` | `0.1.0` | Up-to-date |
| `info.picocli:picocli` | `4.7.7` | `4.7.7` | Up-to-date |
| `com.fasterxml.jackson.core:jackson-databind` | `2.20.2` | `2.21.0` | Follow-up upgrade |
| `org.junit.jupiter:junit-jupiter` | `6.0.1` | `6.0.3` | Follow-up upgrade |
| `org.assertj:assertj-core` | `3.27.6` | `3.27.7` | Follow-up upgrade |
| `com.github.tomakehurst:wiremock` | `2.27.2` | `3.0.1` | Follow-up upgrade (major) |
| `com.google.guava:guava` | `33.4.8-jre` | `33.5.0-jre` | Follow-up upgrade |
| `org.apache.httpcomponents:httpclient` | `4.5.14` | `4.5.14` | Up-to-date |
| `org.ow2.asm:asm` | `9.9` | `9.9.1` | Follow-up upgrade |
| `org.apache.commons:commons-lang3` | `3.17.0` | `3.20.0` | Follow-up upgrade |
| `org.antlr:antlr4-runtime` | `4.13.2` | `4.13.2` | Up-to-date |
| `commons-io:commons-io` | `2.20.0` | `2.21.0` | Follow-up upgrade |
| `org.apache.maven.plugins:maven-surefire-plugin` | `3.5.4` | `3.5.5` | Follow-up upgrade |
| `org.apache.maven.plugins:maven-failsafe-plugin` | `3.5.4` | `3.5.5` | Follow-up upgrade |

Notes:
- For plugins with latest line in beta/milestone (for example compiler/jar `4.0.0-beta*`), this project stays on the latest stable non-beta line.
- Follow-up upgrades are tracked in `docs/ai/progress.md` under `ISSUE-P0-08`.

## Automated Footprint Checks
- Runtime dependency tree is generated automatically during build:
  - `target/runtime-dependency-tree.txt`
  - via `maven-dependency-plugin` execution in `generate-resources`
- Regression checks run in `verify` via integration test:
  - `src/test/java/dev/ayagmar/quarkusforge/footprint/FootprintBaselineIT.java`
- Machine-readable baseline is versioned at:
  - `config/footprint-baseline.properties`

## Version Evidence URLs
Maven Central metadata:
- https://repo1.maven.org/maven2/dev/tamboui/tamboui-tui/maven-metadata.xml
- https://repo1.maven.org/maven2/info/picocli/picocli/maven-metadata.xml
- https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/maven-metadata.xml
- https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter/maven-metadata.xml
- https://repo1.maven.org/maven2/org/assertj/assertj-core/maven-metadata.xml
- https://repo1.maven.org/maven2/com/github/tomakehurst/wiremock/maven-metadata.xml
- https://repo1.maven.org/maven2/com/google/guava/guava/maven-metadata.xml
- https://repo1.maven.org/maven2/org/ow2/asm/asm/maven-metadata.xml
- https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/maven-metadata.xml
- https://repo1.maven.org/maven2/commons-io/commons-io/maven-metadata.xml
- https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-surefire-plugin/maven-metadata.xml
- https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-failsafe-plugin/maven-metadata.xml

Official release/changelog references:
- https://github.com/remkop/picocli/releases
- https://github.com/FasterXML/jackson-databind/releases
- https://junit.org/junit5/docs/current/release-notes/
- https://github.com/assertj/assertj/releases
- https://github.com/wiremock/wiremock/releases
- https://github.com/google/guava/releases
- https://asm.ow2.io/versions.html
- https://commons.apache.org/proper/commons-lang/changes-report.html
- https://commons.apache.org/proper/commons-io/changes.html
- https://github.com/apache/maven-surefire/releases
