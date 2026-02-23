# Headless CLI Generate

## Command
- `quarkus-forge generate` runs non-interactive project generation (`download -> extract`) without starting the TUI.
- Root command (`quarkus-forge`) still launches the TUI flow.

## Request Flags
- Metadata flags:
  - `--group-id`, `--artifact-id`, `--project-version`, `--package-name`, `--output-dir`
  - `--platform-stream`, `--build-tool`, `--java-version`
- Extension selection flags:
  - `--extension` (repeatable; also accepts comma-separated values)
  - `--preset` (repeatable; built-in presets: `web`, `data`, `messaging`, `favorites`)
- Validation mode:
  - `--dry-run` validates the full generation request (including extension IDs) and prints the resolved request without writing files.
  - `--dry-run` is accepted both as `quarkus-forge generate --dry-run ...` and `quarkus-forge --dry-run generate ...`.

## Presets
- `web`: `io.quarkus:quarkus-rest`, `io.quarkus:quarkus-arc`
- `data`: `io.quarkus:quarkus-hibernate-orm-panache`, `io.quarkus:quarkus-jdbc-postgresql`
- `messaging`: `io.quarkus:quarkus-messaging`, `io.quarkus:quarkus-smallrye-health`
- `favorites`: imports persisted favorites from `~/.quarkus-forge/favorites.json`

## Exit Codes
- `0`: success
- `2`: validation/input error
- `3`: network/API error
- `4`: archive/extraction/output-path error
- `130`: cancellation/interrupted

## Notes
- Extension IDs are validated strictly against the loaded catalog before generation.
- Catalog source labeling is surfaced in dry-run output (`live`, `cache`, optional `[stale]` marker).
- Output path resolves to `<output-dir>/<artifact-id>` and uses fail-if-exists behavior.
- `--verbose` emits structured JSON-line diagnostics to `stderr` (events include metadata load,
  catalog load, dry-run validation, generation start/success/failure).
