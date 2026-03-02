# Contributing to Quarkus Forge

Thank you for your interest in contributing! This guide covers everything you need to get started.

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 25+ (use `.sdkmanrc` with [SDKMAN!](https://sdkman.io/)) |
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

```
src/main/java/dev/ayagmar/quarkusforge/
├── api/          HTTP transport, JSON parsing, catalog caching
├── archive/      ZIP download, extraction, Zip-Bomb and Zip-Slip protection
├── domain/       ProjectRequest, validation, Forgefile, compatibility rules
├── headless/     Headless-only classes (ProjectRequestFactory, HeadlessCatalogClient, etc.)
└── ui/           TUI state machine, renderers, event routing
```

Entry points:
- `QuarkusForgeCli` — TUI launch (picocli root command)
- `HeadlessCli` — headless-only launch (picocli root command)

See [Architecture & Internals](docs/modules/ROOT/pages/architecture.adoc) for component diagrams and design principles.

## Development Workflow

A `justfile` is provided for common tasks (install [just](https://github.com/casey/just)):

```bash
just              # list all recipes
just build        # build full jar
just test         # unit + integration tests
just coverage     # tests + JaCoCo report
just format       # auto-format
just verify       # format-check + headless-compile + all tests
just tui          # build and launch TUI
```

Or use Maven directly (`./mvnw` or `mvn`):

### Run tests

```bash
./mvnw test                          # unit tests only
./mvnw verify                        # unit + integration tests + coverage
./mvnw verify -DskipTests -DskipITs=false  # integration tests only (skip unit, keep ITs)
```

### Format code

```bash
./mvnw spotless:apply                # auto-format (Google Java Format)
./mvnw spotless:check                # check only (used in CI)
```

### Check headless profile compiles

```bash
./mvnw clean compile -Pheadless
```

### View coverage report

```bash
./mvnw verify
open target/site/jacoco/index.html       # unit test coverage
open target/site/jacoco-it/index.html    # integration test coverage
```

## Code Style

- **Formatter:** Google Java Format 1.28.0 (enforced via Spotless)
- Run `./mvnw spotless:apply` (or `just format`) before every commit
- No wildcard imports
- Prefer immutable records and sealed types for state
- Extract state machines into dedicated classes (see `PostGenerationMenuState`, `MetadataSelectorManager`)

## Testing Conventions

| Test type | Pattern | Notes |
|-----------|---------|-------|
| Unit | `*Test.java` | Run by `maven-surefire-plugin` |
| Integration | `*IT.java` | Run by `maven-failsafe-plugin`; may start WireMock |
| TUI async | Use `UiScheduler.immediate()` | Deterministic, no sleep/timeouts |

- TUI state machine tests use `UiScheduler.immediate()` and `Duration.ZERO` debounce. Do not use `Thread.sleep()` in tests.
- Property-based tests go in the `archive` package for ZIP edge cases.

## Pull Request Checklist

- [ ] `./mvnw clean verify` passes
- [ ] New code is covered by tests
- [ ] Docs updated if public behavior changed (CLI flags, keybindings, file paths, etc.)
- [ ] PR description filled out with summary and test notes

## Commit Style

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(ui): add keyboard shortcut for export Forgefile
fix(api): tolerate missing extension shortName
docs: update headless CLI examples
chore(ci): harden Windows native build
```

Use scopes: `api`, `archive`, `cache`, `ci`, `cli`, `core`, `docs`, `forge`, `headless`, `metadata`, `native`, `release`, `search`, `tests`, `tui`, `ui`, `validation`.

## Reporting Issues

- **Bugs:** Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.yml). Include `--verbose` output.
- **Feature requests:** Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.yml).
