# Contributing to Quarkus Forge

Thank you for your interest in contributing! This guide covers everything you need to get started.

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 25 only (`[25,26)` enforced by Maven Enforcer; use `.sdkmanrc` with [SDKMAN!](https://sdkman.io/)) |
| Maven | 3.9+ (wrapper included — `./mvnw`) |
| Git | any recent |
| just | optional ([install](https://github.com/casey/just)) |

Optional for native image builds: GraalVM 25+ with `native-image` on `PATH`.

## Setup

```bash
git clone https://github.com/ayagmar/quarkus-forge.git
cd quarkus-forge
sdk env install                       # install Java 25 via SDKMAN! (optional)
./mvnw clean package -DskipTests      # build full jar
```

Run the TUI:

```bash
java --enable-native-access=ALL-UNNAMED -jar target/quarkus-forge.jar
```

Run headless:

```bash
java -jar target/quarkus-forge-headless.jar generate \
  --group-id org.acme --artifact-id demo \
  --preset web --dry-run
```

## Project Structure

```text
src/main/java/dev/ayagmar/quarkusforge/
├── api/          HTTP transport, JSON parsing, catalog caching
├── application/  Request assembly and startup orchestration helpers
├── archive/      ZIP download, extraction, Zip-Bomb and Zip-Slip protection
├── cli/          Picocli entry points, commands, options, exit codes
├── diagnostics/  Structured logging and diagnostic payloads
├── domain/       ProjectRequest, validation, compatibility rules
├── forge/        Forgefile models and persistence
├── headless/     Headless catalog loading, generation, output, timeouts
├── postgen/      Shell execution, IDE detection, post-generation actions
├── runtime/      Runtime wiring, bootstrap, and session summaries
└── ui/           TUI state machine, renderers, event routing
```

Entry points:
- `cli.QuarkusForgeCli` — TUI launch (picocli root command)
- `cli.HeadlessCli` — headless-only launch (picocli root command)

See [Architecture & Internals](docs/modules/ROOT/pages/architecture.adoc) for component diagrams and design principles.

## Development Workflow

A `justfile` is provided for common tasks (install [just](https://github.com/casey/just)):

```bash
just              # list all recipes
just build        # build full jar
just test         # unit + integration tests
just coverage     # tests + JaCoCo report
just docs-build   # docs build verification
just docs-linkcheck # docs link verification
just format       # auto-format
just verify       # format-check + headless-compile + all tests
just tui          # build and launch TUI
```

The shared `scripts/verify/` entrypoints are the source of truth used by CI and release automation:

```bash
scripts/verify/format-check.sh
scripts/verify/verify.sh
scripts/verify/headless-compile.sh
scripts/verify/coverage.sh
scripts/verify/docs-build.sh
scripts/verify/docs-linkcheck.sh
```

The `just` recipes above are thin shortcuts over those scripts.

### Run tests

```bash
./mvnw test               # unit tests only
scripts/verify/verify.sh  # CI-aligned unit + integration verification
```

### Format code

```bash
./mvnw spotless:apply                # auto-format (Google Java Format)
scripts/verify/format-check.sh       # check only (used in CI)
```

### Check headless profile compiles

```bash
scripts/verify/headless-compile.sh
```

### View coverage report

```bash
scripts/verify/coverage.sh
open target/site/jacoco/index.html       # unit test coverage
```

## Code Style

- **Formatter:** Google Java Format 1.28.0 (enforced via Spotless)
- Run `./mvnw spotless:apply` (or `just format`) before every commit
- No wildcard imports
- Prefer immutable records and sealed types for state
- Extract focused UI collaborators when ownership is clear (see `PostGenerationMenuState`, `MetadataSelectorManager`)

## Testing Conventions

| Test type | Pattern | Notes |
|-----------|---------|-------|
| Unit | `*Test.java` | Run by `maven-surefire-plugin` |
| Integration | `*IT.java` | Run by `maven-failsafe-plugin`; may start WireMock |
| TUI async | Use `UiScheduler.immediate()` | Deterministic, no sleep/timeouts |

- TUI state machine tests use `UiScheduler.immediate()` and `Duration.ZERO` debounce. Do not use `Thread.sleep()` in tests.
- Property-based tests go in the `archive` package for ZIP edge cases.

## Pull Request Checklist

- [ ] `scripts/verify/verify.sh` passes
- [ ] New code is covered by tests
- [ ] Docs updated if public behavior changed (CLI flags, keybindings, file paths, etc.)
- [ ] PR description filled out with summary and test notes

## Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```text
feat(ui): add keyboard shortcut for export Forgefile
fix(api): tolerate missing extension shortName
docs: update headless CLI examples
chore(ci): harden Windows native build
```

Use scopes: `api`, `archive`, `cache`, `ci`, `cli`, `core`, `docs`, `forge`, `headless`, `metadata`, `native`, `release`, `search`, `tests`, `tui`, `ui`, `validation`.

## Reporting Issues

- **Bugs:** Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.yml). Include `--verbose` output.
- **Feature requests:** Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.yml).
