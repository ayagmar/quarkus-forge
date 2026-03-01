# Quarkus Forge

Quarkus Forge is a keyboard-first terminal UI (TUI) and headless CLI for generating and scaffolding Quarkus projects. It acts as a fast, offline-capable alternative to `quarkus create`, deeply integrated with `code.quarkus.io`'s remote metadata but built for terminal power users.

It is designed for rapid iteration, leveraging asynchronous background loads, deterministic project state modeling, and safe archive extraction.

## Why use Quarkus Forge?

- **Keyboard-First TUI:** Zero-mouse, Vim-like bindings for navigating catalogs, toggling extensions, and validating inputs.
- **Speed & Caching:** Background loading and local snapshot caching mean you don't wait for the network to start configuring your project.
- **Headless & CI-Ready:** Powerful non-interactive modes for generating applications identically across local environments and CI pipelines.
- **Deterministic State:** Supports `Forgefile` and `forge.lock` for exact reproduction of generated applications, much like standard dependency managers.
- **Workflow Enhancers:** Post-generation handoffs allow you to drop straight into VS Code, an interactive shell, or automatically publish to GitHub.

## Architecture Highlights

The codebase separates pure domain logic from UI side-effects, making it highly testable. 
For a complete overview of the internal design, see the [Architecture & Internals](docs/modules/ROOT/pages/architecture.adoc) documentation.

Key components include:
* `CoreTuiController` - Strict state machine managing TUI inputs and rendering signals.
* `QuarkusApiClient` - Async client fetching extension metadata and binary ZIP payloads.
* `SafeZipExtractor` - Hardened utility protecting against Zip-Bomb and path-traversal (Zip-Slip) attacks during extraction.
* `HeadlessGenerationService` - Decoupled headless generation engine.

## Requirements

- Java 25 (built entirely on the newest LTS features)
- Maven 3.9+

## Build

```bash
mvn -q -DskipTests package
```

Jar output: `target/quarkus-forge.jar`

## Quick Start (Usage)

### 1. Interactive TUI
The primary mode of operation. Launch the interactive TUI:
```bash
java -jar target/quarkus-forge.jar
```
*Tip: In the TUI, hit `?` for help, `Ctrl+P` for the command palette, or `/` to jump to extension search.*

### 2. Headless Generate
Perfect for scripts and automation:
```bash
java -jar target/quarkus-forge.jar generate \
  --group-id org.acme \
  --artifact-id demo \
  --build-tool maven \
  --java-version 25
```

### 3. Quick Prototyping (Dry-Run & Hooks)
To check what would be generated without writing to disk:
```bash
java -jar target/quarkus-forge.jar generate --dry-run
```

To run a shell script automatically after the project is generated (e.g., initial git commit):
```bash
java -jar target/quarkus-forge.jar --post-generate-hook="git init && git add . && git commit -m 'Initial commit'"
```

### 4. Deterministic Replay (Recipes)
Generate an identical project using a recipe and a lock file:
```bash
java -jar target/quarkus-forge.jar generate --recipe Forgefile --lock forge.lock
```

Refresh the lock after intentional recipe changes:
```bash
java -jar target/quarkus-forge.jar generate \
  --recipe Forgefile \
  --lock forge.lock \
  --refresh-lock \
  --dry-run
```

## Where Files Live

Quarkus Forge uses two storage scopes:

- **Machine-local app state:** `~/.quarkus-forge/`
  - `catalog-snapshot.json` (catalog cache/snapshot)
  - `preferences.json` (user preferences)
  - `favorites.json` (favorite extensions)
  - `recipes/` (reusable Forge recipes)
- **Project/workflow files:** usually `forge.lock`, and optionally project-local `Forgefile`
  - Keep lock files with the repository/workflow for strict reproducibility in CI.
  - Reusable templates can live in `~/.quarkus-forge/recipes/` and be referenced by name.

Recipe path UX:

- `--recipe <name>`: uses local file if found; otherwise resolves `~/.quarkus-forge/recipes/<name>`.
- `--write-recipe <name>`: writes to `~/.quarkus-forge/recipes/<name>` when `<name>` is just a filename.
- Use `./Forgefile` when you explicitly want a project-local file path.

## Verification Loop (For Contributors)

Before submitting changes, run:
```bash
mvn -q spotless:check
mvn -q checkstyle:check
mvn -q test
npm ci --prefix site
npm run docs:build --prefix site
npm run docs:linkcheck --prefix site
```

## Docs

- Antora docs source: `docs/`
- Site build scripts: `site/`
- Local docs guide: `site/README.md`
- Forge files/state reference: `docs/modules/ROOT/pages/reference/forge-files-and-state.adoc`
