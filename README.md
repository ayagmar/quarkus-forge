# Quarkus Forge

Quarkus Forge is a keyboard-first terminal UI (TUI) and headless CLI for generating and scaffolding Quarkus projects. It acts as a fast, offline-capable alternative to `quarkus create`, deeply integrated with `code.quarkus.io`'s remote metadata but built for terminal power users.

## Why use Quarkus Forge?

- **Keyboard-First TUI:** Zero-mouse, Vim-like bindings for navigating catalogs, toggling extensions, and validating inputs. Inline validation hints, search match counts, and animated progress feedback.
- **Speed & Caching:** Background loading and local snapshot caching mean you don't wait for the network to start configuring your project.
- **Headless & CI-Ready:** Powerful non-interactive modes for generating applications identically across local environments and CI pipelines.
- **Deterministic State:** Supports `Forgefile` and `forge.lock` for exact reproduction of generated applications, much like standard dependency managers.
- **Customizable:** Theming via `.tcss` files, configurable IDE command via `QUARKUS_FORGE_IDE_COMMAND`, post-generation hooks.
- **Workflow Enhancers:** Post-generation handoffs allow you to drop straight into VS Code, an interactive shell, or automatically publish to GitHub.

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

### UI Layer (`ui/`)
- **`CoreTuiController`** — Central TUI state machine managing focus, input, and generation flow.
- **`OverlayRenderer`** — Stateless overlay rendering (command palette, help, progress, post-generation menus).
- **`MetadataSelectorManager`** — Metadata selector state (platform stream, build tool, Java version cycling and label generation).
- **`UiTextConstants`** — UI text content (help lines, splash art, action labels).
- **`ExtensionCatalogState`** — Extension catalog search, filtering, favorites, presets, and category navigation.
- **`BodyPanelRenderer`** / **`FooterLinesComposer`** — Layout rendering helpers.

### CLI Layer (root package)
- **`QuarkusForgeCli`** — Picocli command entry point, TUI bootstrap, and runtime configuration.
- **`HeadlessGenerationService`** — Decoupled headless generation engine for CI/scripting, with `AsyncFailureHandler` for consistent error handling.
- **`PostTuiActionExecutor`** — Post-generation shell actions (IDE open, GitHub publish, terminal handoff).
- **`ForgeRecipeLockStore`** — Forgefile and forge.lock persistence.

### Archive Layer (`archive/`)
- **`SafeZipExtractor`** — Hardened ZIP extraction with Zip-Bomb and Zip-Slip protections.
- **`ProjectArchiveService`** — Orchestrates download, extraction, and progress reporting.

For a complete overview of the internal design, see the [Architecture & Internals](docs/modules/ROOT/pages/architecture.adoc) documentation.

## Requirements

- Java 25
- Maven 3.9+

## Build

```bash
mvn clean package -DskipTests
```

Output: `target/quarkus-forge.jar`

## Quick Start

### Interactive TUI
```bash
java -jar target/quarkus-forge.jar
```
Hit `?` for help, `Ctrl+P` for the command palette, or `/` to jump to extension search.

### Headless Generate
```bash
java -jar target/quarkus-forge.jar generate \
  --group-id org.acme \
  --artifact-id demo \
  --build-tool maven \
  --java-version 25
```

### Dry-Run
```bash
java -jar target/quarkus-forge.jar generate --dry-run
```

### Post-Generation Hooks
```bash
java -jar target/quarkus-forge.jar \
  --post-generate-hook="git init && git add . && git commit -m 'Initial commit'"
```

### Deterministic Replay
```bash
# Generate from recipe + lock
java -jar target/quarkus-forge.jar generate --recipe Forgefile --lock forge.lock

# Refresh lock after recipe changes
java -jar target/quarkus-forge.jar generate \
  --recipe Forgefile --lock forge.lock --refresh-lock --dry-run
```

## Customization

### Theming
Create a `.tcss` file with semantic color tokens:
```
# my-theme.tcss
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

### IDE Command
The "Open in IDE" post-generation action defaults to `code .` (VS Code). Override via:
```bash
export QUARKUS_FORGE_IDE_COMMAND="idea ."        # IntelliJ
export QUARKUS_FORGE_IDE_COMMAND="nvim ."        # Neovim
export QUARKUS_FORGE_IDE_COMMAND="cursor ."      # Cursor
```

## Where Files Live

- **Machine-local app state:** `~/.quarkus-forge/`
  - `catalog-snapshot.json` — catalog cache/snapshot
  - `preferences.json` — user preferences
  - `favorites.json` — favorite extensions
  - `recipes/` — reusable Forge recipes
- **Project/workflow files:**
  - `forge.lock` — deterministic lock for CI reproducibility
  - `Forgefile` — project recipe template

Recipe path resolution:
- `--recipe <name>`: uses local file if found; otherwise resolves `~/.quarkus-forge/recipes/<name>`.
- `--write-recipe <name>`: writes to `~/.quarkus-forge/recipes/<name>` when `<name>` is just a filename.

## Verification

```bash
mvn clean verify
```

Runs all unit and integration tests.

## Docs

- Antora docs source: `docs/`
- Site build scripts: `site/`
- Local docs guide: `site/README.md`
