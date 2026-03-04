# JSON Output Mode for `generate` — Implementation Spec

> [!IMPORTANT]
> This file is a historical implementation spec.
> Canonical runtime behavior is documented in `README.md` and `docs/modules/ROOT/pages/**`.
> If this spec diverges from current code, treat product docs and code as source of truth.

**Issue**: #10 (follow-up)
**Branch target**: `feat/headless-cli` (after headless profile is merged)
**Prerequisite**: Headless profile migration complete (headless build/profile behavior already merged)
**Goal**: Add `--format json` option to the `generate` subcommand so CI/CD pipelines and
automation scripts can consume machine-parseable output.

---

## 1. Scope

| In scope | Out of scope |
|---|---|
| `--format json` option on `GenerateCommand` | New subcommands |
| JSON output for all `generate` outcomes | JSON output for `--help` / `--version` |
| Backward-compat with existing text output | Changes to TUI behavior |
| Tests for JSON and text modes | Performance benchmarks |

---

## 2. Design

### OutputFormat enum

**New file**: `src/main/java/dev/ayagmar/quarkusforge/OutputFormat.java`

```java
enum OutputFormat { TEXT, JSON }
```

### GenerateCommand option

```java
@Option(
    names = "--format",
    defaultValue = "text",
    description = "Output format: text (default) or json")
OutputFormat outputFormat = OutputFormat.TEXT;
```

> picocli supports enum options natively — `text`/`json` strings are case-insensitively
> matched to `OutputFormat.TEXT` / `OutputFormat.JSON` by picocli's built-in enum converter.
> No custom converter needed.

> **`--format` vs `--from`**: both options start with `--fo`. picocli 4.x supports abbreviated
> options, so `--for` is ambiguous (picocli reports an error). Full names are distinct and work
> correctly.

### HeadlessGenerationService signature change

```java
int run(GenerateCommand command, boolean globalDryRun, boolean verbose, OutputFormat outputFormat)
```

**Update all call sites:**
- `HeadlessCli.runHeadlessGenerate(command)` →
  `headlessService.run(command, false, verbose, command.outputFormat)`
- `QuarkusForgeCli.runHeadlessGenerate(command)` →
  `headlessGenerationService.run(command, dryRun, verbose, command.outputFormat)`

### Output stream contract

> In `text` mode: success to stdout, errors to stderr (current behavior preserved).
> In `json` mode: ALL outcomes — success, dry-run, **and errors** — go to **stdout**
> as a single JSON object; `--verbose` diagnostics still go to stderr.
> This allows CI scripts to read one stream: `result=$(cmd 2>/dev/null); echo "$result" | jq .`

### JSON schemas

**Dry-run success** (stdout):
```json
{"outcome":"dry-run-ok","groupId":"...","artifactId":"...","version":"...",
 "packageName":"...","outputDirectory":"...","platformStream":"...",
 "buildTool":"...","javaVersion":"...","extensions":["id1","id2"],
 "catalogSource":"live","stale":false}
```

**Generation success** (stdout):
```json
{"outcome":"success","projectRoot":"/abs/path/to/project","extensions":["id1","id2"],
 "catalogSource":"live","stale":false}
```

**Validation error** (stdout):
```json
{"outcome":"validation-error","errors":[{"field":"extension","message":"unknown id '...'"}]}
```

> `JsonSupport.writeString(Map)` is already in `api/` and handles this serialization;
> catch and swallow `IOException` (unlikely since we're serializing simple primitive maps).

---

## 3. Implementation

### All output paths in `HeadlessGenerationService.run()` that must be updated

1. `catch (IllegalArgumentException ...)` block — `System.err.println(...)` → JSON variant
2. `HeadlessOutputPrinter.printValidationErrors(...)` (2 call sites) → JSON variant when `json` mode
3. `HeadlessOutputPrinter.printDryRunSummary(...)` (1 call site) → JSON variant when `json` mode
4. `System.out.println("Generation succeeded: ...")` (1 call site) → JSON variant when `json` mode
5. `AsyncFailureHandler.handleFailure(...)` (3 call sites) — these print error messages to
   stderr; in JSON mode the error must go to stdout instead. Options:
   a. Add `OutputFormat` parameter to `handleFailure()` and switch output stream accordingly, or
   b. Have `handleFailure()` return an error description alongside the exit code (e.g., via a
      record) and let the caller handle JSON printing. Option (b) is cleaner but a bigger refactor.

### Test signature update

`HeadlessGenerationServiceTest`: the `run()` signature gains a 4th parameter. Update the
3 existing test calls:
```java
// Before:
service.run(command, true, false)
// After:
service.run(command, true, false, OutputFormat.TEXT)
```

---

## 4. Tests

Add to `HeadlessCliTest` or a new `HeadlessCliJsonOutputTest`:

```java
@Test
void headless_generate_dry_run_json_format_outputs_valid_json() {
    CliCommandTestSupport.stubCatalogEndpoints();
    CommandResult result = CliCommandTestSupport.runHeadless(
            CliCommandTestSupport.runtimeConfig(tempDir,
                URI.create("http://localhost:" + wireMockServer.port())),
            "generate", "--dry-run", "--format", "json", "-g", "org.acme", "-a", "demo");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).contains("\"outcome\":\"dry-run-ok\"");
    assertThat(result.standardOut()).contains("\"artifactId\":\"demo\"");
}

@Test
void headless_generate_dry_run_json_format_validation_error_to_stdout() {
    CliCommandTestSupport.stubCatalogEndpoints();
    CommandResult result = CliCommandTestSupport.runHeadless(
            CliCommandTestSupport.runtimeConfig(tempDir,
                URI.create("http://localhost:" + wireMockServer.port())),
            "generate", "--dry-run", "--format", "json",
            "-g", "org.acme", "-a", "demo",
            "--extension", "io.quarkus:does-not-exist");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.VALIDATION);
    assertThat(result.standardOut()).contains("\"outcome\":\"validation-error\"");
    assertThat(result.standardError()).isEmpty();   // errors → stdout in json mode
}

@Test
void headless_generate_without_format_flag_uses_text_output() {
    // Backward-compat: default is TEXT, preserving current behavior
    CliCommandTestSupport.stubCatalogEndpoints();
    CommandResult result = CliCommandTestSupport.runHeadless(
            CliCommandTestSupport.runtimeConfig(tempDir,
                URI.create("http://localhost:" + wireMockServer.port())),
            "generate", "--dry-run", "-g", "org.acme", "-a", "demo");
    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).contains("Dry-run validated successfully:");
    assertThat(result.standardOut()).doesNotContain("{\"outcome\"");
}
```

---

## 5. Verification

```bash
# Happy path: JSON dry-run output (requires network/mock)
java -jar target/quarkus-forge-headless.jar generate --dry-run --format json \
    -g org.acme -a demo 2>/dev/null | jq -r .outcome
# must output: dry-run-ok

# Text mode unchanged:
java -jar target/quarkus-forge-headless.jar generate --dry-run \
    -g org.acme -a demo 2>/dev/null | head -1
# must output: Dry-run validated successfully:
```

---

## 6. Notes

- **Invalid `--format` values**: picocli validates enum options automatically. Passing
  `--format yaml` produces an error like `"Invalid value for option '--format': expected one of
  [TEXT, JSON] (case-insensitive) but was 'yaml'"` and exits with code 2 (`ExitCodes.VALIDATION`).
  No custom validation code needed.

- **picocli enum type converter**: picocli 4.x handles enum options out of the box;
  the string `"json"` is matched case-insensitively to `OutputFormat.JSON`. If picocli rejects
  the enum directly (unlikely), add `@Option(..., converter = OutputFormatConverter.class)` with
  a one-line `ITypeConverter<OutputFormat>` implementation.

- **`--format json` works in both CLIs**: `GenerateCommand` is a subcommand of both `HeadlessCli`
  and `QuarkusForgeCli`. The `outputFormat` field is on `GenerateCommand`, so `--format json`
  works identically in both paths.

- **Documentation**: update `docs/modules/ROOT/pages/cli/headless-mode.adoc` to document the
  `--format json` option with examples.
