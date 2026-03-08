# Quarkus Forge

[![CI](https://github.com/ayagmar/quarkus-forge/actions/workflows/ci.yml/badge.svg)](https://github.com/ayagmar/quarkus-forge/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/ayagmar/quarkus-forge)](https://github.com/ayagmar/quarkus-forge/releases/latest)
[![codecov](https://codecov.io/gh/ayagmar/quarkus-forge/branch/master/graph/badge.svg)](https://codecov.io/gh/ayagmar/quarkus-forge)
[![Java](https://img.shields.io/badge/Java-25-blue)](https://openjdk.org/projects/jdk/25/)
[![JBang](https://img.shields.io/badge/JBang-quarkus--forge%40ayagmar-orange)](https://www.jbang.dev/)

> **[Documentation & Landing Page](https://ayagmar.github.io/quarkus-forge/)** · **[Getting Started](https://ayagmar.github.io/quarkus-forge/docs/getting-started/)**

Quarkus Forge is a keyboard-first terminal UI (TUI) and headless CLI for generating and scaffolding Quarkus projects. It acts as a fast, offline-capable alternative to `quarkus create`, deeply integrated with `code.quarkus.io`'s remote metadata but built for terminal power users.

![Quarkus Forge TUI](docs/images/tui-screenshot.png)

## Why use Quarkus Forge?

- **Keyboard-First TUI:** Zero-mouse, Vim-like bindings for navigating catalogs, toggling extensions, and validating inputs. Fuzzy search highlighting, chip-style selected extensions, Tamboui selector/input widgets with visible caret, and animated progress feedback.
- **Speed & Caching:** Background loading and local snapshot caching mean you don't wait for the network to start configuring your project.
- **Headless & CI-Ready:** Powerful non-interactive modes for generating applications identically across local environments and CI pipelines.
- **Deterministic State:** Supports `Forgefile` with an optional `locked` section for exact reproduction of generated applications, much like standard dependency managers.
- **Customizable:** Theming via `.tcss` files, IDE auto-detection with `QUARKUS_FORGE_IDE_COMMAND` override, post-generation hooks.
- **Workflow Enhancers:** Post-generation handoffs let you open in your auto-detected IDE, drop into a shell, or publish to GitHub — all from the keyboard.

## Quarkus Forge vs `quarkus create`

| Feature | Quarkus Forge | `quarkus create` |
|---------|--------------|-------------------|
| **Offline / no-internet** | ✅ snapshot cache fallback | ❌ requires network |
| **Keyboard-first TUI** | ✅ full Vim-style navigation | ❌ wizard prompts |
| **Deterministic replay** | ✅ Forgefile + `--lock` | ❌ |
| **CI headless jar** | ✅ no TUI deps (~40% smaller) | ⚠️ includes full CLI toolchain |
| **Fuzzy extension search** | ✅ | ❌ |
| **Session persistence** | ✅ remembers last config | ❌ |
| **Theming** | ✅ `.tcss` override | ❌ |
| **Post-gen IDE open** | ✅ auto-detects IDEs | ❌ |
| **Quarkus CLI required** | ❌ plain JRE / JBang | ✅ |

## TUI vs Headless

| | TUI (interactive) | Headless (`generate`) |
|--|--|--|
| **Best for** | Local development, exploration | CI pipelines, scripting, containers |
| **Jar** | `quarkus-forge.jar` | `quarkus-forge-headless.jar` |
| **Interaction** | Keyboard-driven UI | Flags only, non-interactive |
| **Extension search** | Live fuzzy search | `--extension` / `--preset` flags |
| **Forgefile** | Export via post-gen menu | `--from`, `--save-as`, `--lock` |
| **Post-gen hooks** | IDE open, GitHub, shell handoff | n/a |
| **JVM flag needed** | `--enable-native-access=ALL-UNNAMED` | none |

## Which Artifact Should I Use?

| If you want to... | Use | Why |
|--|--|--|
| Explore extensions interactively | `quarkus-forge.jar` | Includes the full TUI |
| Run in CI or containers | `quarkus-forge-headless.jar` | Smaller and has no TUI or terminal dependencies |
| Automate `generate` locally | `quarkus-forge-headless.jar` | Same command surface with less runtime baggage |
| Keep one local developer jar | `quarkus-forge.jar` | Supports both TUI and `generate` |
| Avoid the JVM at runtime | native binary | Standalone executable |

## GitHub Release Asset Guide

| Use case | Download this release asset |
|--|--|
| Interactive JVM app on any OS | `quarkus-forge-jvm.jar` |
| Headless JVM app for CI/containers | `quarkus-forge-headless.jar` |
| Interactive native app on Linux | `quarkus-forge-linux-x86_64` |
| Interactive native app on macOS (Apple Silicon) | `quarkus-forge-macos-aarch64` |
| Interactive native app on Windows | `quarkus-forge-windows-x86_64.exe` |
| Headless native app on Linux | `quarkus-forge-headless-linux-x86_64` |
| Headless native app on macOS (Apple Silicon) | `quarkus-forge-headless-macos-aarch64` |
| Headless native app on Windows | `quarkus-forge-headless-windows-x86_64.exe` |

Every GitHub release publishes a matching `.sha256` file for each artifact.

On Linux or macOS, download both files and verify locally:

```bash
shasum -a 256 -c <artifact>.sha256
```

Examples:

```bash
shasum -a 256 -c quarkus-forge-jvm.jar.sha256
shasum -a 256 -c quarkus-forge-headless-linux-x86_64.sha256
```

On Windows PowerShell:

```powershell
$expected = (Get-Content .\quarkus-forge-windows-x86_64.exe.sha256).Split(' ')[0].ToLower()
$actual = (Get-FileHash .\quarkus-forge-windows-x86_64.exe -Algorithm SHA256).Hash.ToLower()
$actual -eq $expected
```

## Keyboard Quick Reference (TUI)

| Key | Action |
|-----|--------|
| `?` | Help overlay |
| `Ctrl+P` | Command palette |
| `/` or `Ctrl+F` | Focus extension search |
| `Space` | Toggle extension |
| `Enter` / `Alt+G` | Generate project |
| `Ctrl+R` | Reload catalog |
| `Ctrl+K` | Toggle favorites-only |
| `Alt+S` | Toggle selected-only view |
| `v` | Cycle category filter |
| `c` | Toggle current category |
| `C` | Open all categories |
| `x` | Clear selected extensions |
| `Esc` | Unwind filter context / exit |
| `Ctrl+C` | Quit immediately |

Full keybindings: [docs/modules/ROOT/pages/ui/keybindings.adoc](docs/modules/ROOT/pages/ui/keybindings.adoc)

## Requirements

- Java 25+
- Maven 3.9+

## Build

### Full build (TUI + headless)
```bash
./mvnw clean package -DskipTests
```

Output: `target/quarkus-forge.jar`

### Headless-only build
```bash
./mvnw clean package -Pheadless
```

Output: `target/quarkus-forge-headless.jar` — ~40% smaller, no TUI or terminal dependencies.

### Native image build
```bash
./mvnw clean package -Pnative
```

Output: `target/quarkus-forge` — standalone binary, no JVM required at runtime.

> **Note:** Native image requires GraalVM or a compatible toolchain. Set `GRAALVM_HOME` before building.
> CI also enforces native binary size budgets for `headless-native` and `native` using produced binary file size; the build report and native-image log are included for diagnostics.

### Bash Completion
Generate completion scripts after building the jars:
```bash
just completion-bash
```

This writes:
- `target/completions/quarkus-forge.bash`
- `target/completions/quarkus-forge-headless.bash`

Load them into the current shell:
```bash
source target/completions/quarkus-forge.bash
source target/completions/quarkus-forge-headless.bash
```

### Release Checksums
Generate `.sha256` files for locally built release jars:
```bash
just release-checksums
```

Verify locally built artifacts:
```bash
sha256sum -c target/quarkus-forge.jar.sha256
sha256sum -c target/quarkus-forge-headless.jar.sha256
```

## Quick Start

### JBang (no build required)

Run directly from the JBang catalog:
```bash
jbang quarkus-forge@ayagmar
```

Headless-only (no TUI dependencies, ideal for CI):
```bash
jbang quarkus-forge-headless@ayagmar generate \
  --group-id org.acme \
  --artifact-id demo \
  --build-tool maven \
  --java-version 25
```

Install as a persistent local command:
```bash
jbang app install --name quarkus-forge quarkus-forge@ayagmar
quarkus-forge
```

### Interactive TUI
```bash
java --enable-native-access=ALL-UNNAMED -jar target/quarkus-forge.jar
```
> **Note:** The `--enable-native-access=ALL-UNNAMED` flag suppresses Panama FFM warnings from the TamboUI terminal backend.

Hit `?` for help, `Ctrl+P` for the command palette, or `/` to jump to extension search.

### Headless Generate
```bash
java -jar target/quarkus-forge-headless.jar generate \
  --group-id org.acme \
  --artifact-id demo \
  --build-tool maven \
  --java-version 25 \
  --preset web \
  --extension io.quarkus:quarkus-smallrye-health
```

> The full jar (`quarkus-forge.jar`) also supports the `generate` subcommand. The headless-only jar is preferred for CI/containers — no TUI or terminal dependencies.

### Dry-Run
```bash
java -jar target/quarkus-forge-headless.jar generate \
  --group-id org.acme \
  --artifact-id demo \
  --preset web \
  --extension io.quarkus:quarkus-smallrye-health \
  --dry-run
```

### Post-Generation Hooks
```bash
java -jar target/quarkus-forge.jar \
  --post-generate-hook="git init && git add . && git commit -m 'Initial commit'"
```

### Deterministic Replay
```bash
# Generate from a Forgefile template
java -jar target/quarkus-forge-headless.jar generate --from Forgefile

# Generate and write/update the locked section
java -jar target/quarkus-forge-headless.jar generate --from Forgefile --lock

# Verify no drift against locked section
java -jar target/quarkus-forge-headless.jar generate --from Forgefile --lock-check --dry-run

# Save current configuration as a shareable template
java -jar target/quarkus-forge-headless.jar generate --save-as my-template.json --lock \
  --group-id com.acme --artifact-id my-service -e io.quarkus:quarkus-rest
```

## Customization

### Theming
Create a `.tcss` file with semantic color tokens (one `token = value` per line):
```ini
base = #1e1e2e
text = #cdd6f4
accent = #f38ba8
focus = #89b4fa
muted = #6c7086
```
Apply via environment variable or system property:
```bash
export QUARKUS_FORGE_THEME=/path/to/my-theme.tcss
# or
java -Dquarkus.forge.theme=/path/to/my-theme.tcss -jar target/quarkus-forge.jar
```

### IDE Auto-Detection
After generating a project, Quarkus Forge auto-detects installed IDEs (IntelliJ IDEA, VS Code, Eclipse, Cursor, Zed, Neovim) and shows one menu entry per detected IDE.

To override auto-detection, set `QUARKUS_FORGE_IDE_COMMAND`:
```bash
export QUARKUS_FORGE_IDE_COMMAND="idea ."        # Force IntelliJ
export QUARKUS_FORGE_IDE_COMMAND="code-insiders ."  # VS Code Insiders
```

## Where Files Live

- **Machine-local app state:** `~/.quarkus-forge/`
  - `catalog-snapshot.json` — catalog cache/snapshot (offline fallback)
  - `preferences.json` — user preferences (restored on next launch)
  - `favorites.json` — favorite extensions
  - `recipes/` — reusable Forge recipes
- **Project/workflow files:**
  - `Forgefile` — shareable project template with optional `locked` section for CI reproducibility

Forgefile path resolution:
- `--from <name>`: uses local file if found; otherwise resolves `~/.quarkus-forge/recipes/<name>`.
- `--save-as <name>`: writes to `~/.quarkus-forge/recipes/<name>` when `<name>` is just a filename.

## Architecture

The codebase is organized into focused modules that follow SOLID principles and separate concerns cleanly.

### API Layer (`api/`)
- **`QuarkusApiClient`** — Async HTTP client with retry/backoff, implements `AutoCloseable` for resource safety. Responsible only for transport orchestration.
- **`ApiPayloadParser`** — Stateless JSON deserialization for all API payloads (extensions, metadata, streams, presets, OpenAPI).
- **`JsonFieldReader`** — Shared JSON field reading helpers used across all store and parser classes (DRY).
- **`CatalogSnapshotCache`** — Local catalog snapshot persistence and freshness management.

### Domain Layer (`domain/`)
- **`ProjectRequest`** / **`ProjectRequestValidator`** — Immutable project configuration with validation rules.
- **`MetadataCompatibilityContext`** — Enforces metadata-driven compatibility constraints (build tool ↔ Java version).
- **`CliPrefillMapper`** — Maps CLI options to validated project requests.

### Persistence Layer (`persistence/`)
- **`UserPreferencesStore`** — Persists the last successful request used to prefill the next TUI session.
- **`ExtensionFavoritesStore`** — Owns favorites/recents storage for TUI filters and headless `favorites` preset expansion.

### UI Layer (`ui/`)
- **`CoreTuiController`** — TUI orchestration shell and callback sink, rendering from immutable state snapshots.
- **`CatalogLoadCoordinator`** / **`GenerationFlowCoordinator`** — Dedicated async workflow coordinators for catalog loading and generation.
- **`CoreUiReducer`** — Pure reducer for migrated UI intents and effects.
- **`UiStateSnapshotMapper`** — Builds immutable `UiState` snapshots from controller-managed state slices.
- **`OverlayRenderer`** — Stateless overlay rendering (command palette, help, progress, post-generation menus).
- **`MetadataSelectorManager`** — Metadata selector state (platform stream, build tool, Java version cycling and label generation).
- **`UiTextConstants`** — UI text content (help lines, splash art, action labels).
- **`ExtensionCatalogState`** — Extension catalog search, filtering, favorites, presets, and category navigation.
- **`BodyPanelRenderer`** / **`FooterLinesComposer`** — Layout rendering helpers.

### Runtime Layer (`runtime/`)
- **`RuntimeWiring`** — Central composition owner for persistence-backed stores, headless generation wiring, and shared API/archive service assembly.
- **`TuiBootstrapService`** — Runtime wiring for TUI startup, catalog bootstrap, Tamboui backend preference, and session execution.
- **`TuiSessionSummary`** — Immutable summary of the final request and post-generation exit plan.

### Application Layer (`application/`)
- **`InputResolutionService`** — Shared pure pipeline for resolving CLI/headless prefill into validated request state.
- **`StartupMetadataSelection`** — Startup metadata source selection and fallback detail.

### CLI Layer (`cli/`)
- **`QuarkusForgeCli`** — Picocli command entry point for TUI mode, runtime configuration, and startup metadata resolution.
- **`HeadlessCli`** — Lightweight entry point for headless/CI mode (no TUI or terminal dependencies).
- **`GenerateCommand`** / **`RequestOptions`** — Headless generation command model and option parsing.
- **`ExitCodes`** — Central exit code constants shared by both entry points.

### Headless Layer (`headless/`)
- **`HeadlessGenerationService`** — Decoupled headless generation engine for CI/scripting, resolving Forgefiles through `InputResolutionService` and narrow catalog/generation/favorites collaborators.
- **`AsyncFailureHandler`** — Headless boundary adapter for consistent exit codes, diagnostics, and user-facing messages.
- **`HeadlessCatalogClient`** — Internal timeout-aware adapter that fronts catalog, preset, and archive services for one headless session.
- **`HeadlessOutputPrinter`** — Text-mode summaries and validation/error output.

Shared timeout/cancellation/failure classification is provided by `dev.ayagmar.quarkusforge.diagnostics.BoundaryFailure` and reused by headless and runtime code.

### Forge Layer (`forge/`)
- **`ForgefileStore`** — Forgefile persistence with omission-preserving top-level template fields.
- **`Forgefile`** / **`ForgefileLock`** — Shareable intent template plus explicit deterministic lock data.

### Post-Generation Layer (`postgen/`)
- **`PostTuiActionExecutor`** — Post-generation shell actions (IDE open, GitHub publish, terminal handoff).
- **`IdeDetector`** — Cross-platform IDE auto-detection (macOS, Linux, Windows).

### Archive Layer (`archive/`)
- **`SafeZipExtractor`** — Hardened ZIP extraction with Zip-Bomb and Zip-Slip protections.
- **`ProjectArchiveService`** — Orchestrates download, extraction, and progress reporting.

For a complete overview of the internal design, see the [Architecture & Internals](docs/modules/ROOT/pages/architecture.adoc) documentation.

## Docs

Full documentation is available at **[ayagmar.github.io/quarkus-forge](https://ayagmar.github.io/quarkus-forge/docs/)**.

Source pages (AsciiDoc):

- [Getting Started](docs/modules/ROOT/pages/getting-started.adoc)
- [TUI Usage](docs/modules/ROOT/pages/usage/tui.adoc)
- [Keybindings](docs/modules/ROOT/pages/ui/keybindings.adoc)
- [Headless CLI](docs/modules/ROOT/pages/cli/headless-mode.adoc)
- [Forge Files & State](docs/modules/ROOT/pages/reference/forge-files-and-state.adoc)
- [Theming](docs/modules/ROOT/pages/ui/theming.adoc)
- [Architecture](docs/modules/ROOT/pages/architecture.adoc)
- [Troubleshooting](docs/modules/ROOT/pages/troubleshooting.adoc)

Antora docs source: `docs/` · Site build scripts: `site/` · Local site guide: `site/README.md`

## Contributing

```bash
sdk env install          # install Java 25 via SDKMAN! (.sdkmanrc)
scripts/verify/verify.sh # shared CI/local verification entrypoint
just verify              # format-check + headless compile + all tests
just format              # auto-format
```

Docs verification uses the same shared entrypoints as CI:

```bash
scripts/verify/docs-build.sh
scripts/verify/docs-linkcheck.sh
```

Native packaging and release smoke checks use the same shared scripts as release automation:

```bash
scripts/verify/native-size.sh headless
scripts/verify/native-size.sh interactive
scripts/verify/native-release-smoke.sh <binary> <headless|interactive-posix|interactive-windows>
```

Or without `just`, use the shared scripts directly instead of retyping the underlying Maven and npm commands.

Coverage reports (after `scripts/verify/coverage.sh`): `target/site/jacoco/index.html` (HTML) and `target/site/jacoco/jacoco.xml` (XML).

See [CONTRIBUTING.md](CONTRIBUTING.md) for full setup guide, code style, testing conventions, and commit format. PRs welcome.
