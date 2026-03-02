# Headless Build Profile — Implementation Spec

**Issue**: #10  
**Branch target**: `develop`  
**Goal**: `mvn -Pheadless package` produces a runnable headless artifact with no `dev.tamboui` classes; default build unchanged.

---

## 1. Scope

| In scope | Out of scope |
|---|---|
| New `headless` Maven profile | Changes to TUI behaviour |
| Pre-requisite package refactoring (favorites store) | New headless features |
| Decoupling tamboui from `QuarkusForgeCli` | Publish/deploy pipeline for headless artifact |
| `HeadlessCli` entry point | Changing headless UX (subcommand structure stays) |
| Headless native build support | Performance benchmarks |
| 1+ tests, README section | Running tests in headless compile mode |

---

## 2. Current Architecture — Coupling Inventory

### Files that import tamboui directly

```
QuarkusForgeCli.java       ← 2 imports (TuiConfig, Bindings), 3 static helpers + 4 constants
TuiBootstrapService.java   ← fully encapsulated; correct boundary already
AppBindingsProfile.java    ← pure tamboui; easy to exclude
ui/AppKeyActions.java       ← tamboui
ui/BodyPanelRenderer.java   ← tamboui (19 imports)
ui/CompactInputRenderer.java← tamboui
ui/CoreTuiController.java   ← tamboui (13 imports)
ui/ExtensionCatalogState.java← tamboui
ui/OverlayRenderer.java     ← tamboui (11 imports)
ui/PanelBorderStyleResolver.java ← tamboui
ui/PostGenerationMenuState.java  ← tamboui
ui/UiEventRouter.java       ← tamboui
ui/UiKeyMatchers.java       ← tamboui
ui/UiRoutingContext.java    ← tamboui
ui/UiTheme.java             ← tamboui
```

> **Note**: The remaining ~40 files in `ui/` have NO tamboui imports. Some must move to
> `api/` (see Pre-Phase 1) because they are referenced by files in the headless compile scope.

### Files in headless compile scope with hidden `ui/` dependencies

```
RuntimeConfig.java
  ← ui.ExtensionFavoritesStore.defaultFile()   → ForgeDataPaths.favoritesFile()
  ← ui.UserPreferencesStore.defaultFile()       → ForgeDataPaths.preferencesFile()
  Fix: call ForgeDataPaths directly; remove ui/ imports

HeadlessGenerationService.java
  ← ui.ExtensionFavoritesStore (for "favorites" preset resolution)
  Fix: move ExtensionFavoritesStore + implementations to api/ (see Pre-Phase 1)
```

### Files where headless path references QuarkusForgeCli for exit codes

```
HeadlessGenerationService.java  ← QuarkusForgeCli.EXIT_CODE_* + CommandLine.ExitCode.OK
ProjectRequestFactory.java      ← QuarkusForgeCli.EXIT_CODE_*
```

### Test files that reference QuarkusForgeCli.EXIT_CODE_* (must update in Phase 1)

```
QuarkusForgeGenerateCommandTest.java  ← QuarkusForgeCli.EXIT_CODE_* (10+ refs)
HeadlessGenerationServiceTest.java    ← QuarkusForgeCli.EXIT_CODE_NETWORK + CommandLine.ExitCode.OK
```

### GenerateCommand parent coupling

```java
// Current
@ParentCommand QuarkusForgeCli rootCommand;
// Must become interface-typed so HeadlessCli can also be a parent
```

---

## 3. Target Architecture

```
HeadlessCli (headless profile entry point)
  └── implements HeadlessRunner
  └── @Command subcommands = {GenerateCommand}

QuarkusForgeCli (default profile entry point)
  └── implements HeadlessRunner
  └── @Command subcommands = {GenerateCommand}

GenerateCommand
  └── @ParentCommand HeadlessRunner rootCommand  ← interface, not class

HeadlessRunner (interface)
  └── int runHeadlessGenerate(GenerateCommand cmd)

ExitCodes (new utility class, package-private)
  └── OK=0, USAGE=1, VALIDATION=2, NETWORK=3, ARCHIVE=4, CANCELLED=130
```

### Headless compile scope (what is compiled in `headless` profile)

**Moved to `api/` in Pre-Phase 1 (thus included):**
- `api/ExtensionFavoritesStore.java` (moved from `ui/`)
- `api/FileBackedExtensionFavoritesStore.java` (moved from `ui/`)
- `api/ExtensionFavoritesPayload.java` (moved from `ui/`)
- `api/ExtensionFavoriteIds.java` (moved from `ui/`)
- `api/InMemoryExtensionFavoritesStore.java` (moved from `ui/`)

**Always included (no changes):**
- All of `api/` — all existing files
- All of `archive/` — all
- All of `domain/` — all
- All of `diagnostics/` — all
- All of `util/` — all
- `HeadlessCli.java` (new)
- `HeadlessCatalogClient.java`
- `HeadlessGenerationService.java`
- `HeadlessOutputPrinter.java`
- `HeadlessTimeouts.java`
- `GenerateCommand.java`
- `CliVersionProvider.java`
- `RequestOptions.java`
- `RuntimeConfig.java` (after removing `ui/` imports)
- `Forgefile.java`, `ForgefileLock.java`, `ForgefileStore.java`, `ForgeRecordValues.java`
- `IdeDetector.java`
- `ProjectRequestFactory.java`
- `ShellExecutor.java`, `ShellProcessRunner.java`, `ShellExecutorDiagnostics.java`
- `ValidationException.java`
- `AsyncFailureHandler.java`
- `ExitCodes.java` (new)
- `HeadlessRunner.java` (new)

**Excluded in headless profile (via `maven-compiler-plugin` excludes):**
- `dev/ayagmar/quarkusforge/ui/**` (entire package; favorites classes are already moved out)
- `dev/ayagmar/quarkusforge/AppBindingsProfile.java`
- `dev/ayagmar/quarkusforge/TuiBootstrapService.java`
- `dev/ayagmar/quarkusforge/QuarkusForgeCli.java`
- `dev/ayagmar/quarkusforge/PostTuiActionExecutor.java`
- `dev/ayagmar/quarkusforge/TuiSessionSummary.java`
- `dev/ayagmar/quarkusforge/StartupMetadataSelection.java`

> **Test compilation note**: tests are skipped in the headless profile (`<maven.test.skip>true</maven.test.skip>`)
> because many test files reference `QuarkusForgeCli` and TUI classes. The `skipTests` flag
> only skips execution — `maven.test.skip` skips both compilation and execution, which is
> required here since excluded main sources would cause test compilation to fail.
> Tests run exclusively with the default profile: `mvn test`.

---

## 4. Maven Configuration Strategy

### Problem with overriding existing shade execution

The base build's shade execution has no `<id>` tag:

```xml
<execution>          <!-- implicit id = "default" -->
  <phase>package</phase>
  <goals><goal>shade</goal></goals>
  <configuration>
    <finalName>quarkus-forge</finalName>
    <transformers>
      <transformer ...ServicesResourceTransformer/>
      <transformer ...ManifestResourceTransformer>
        <mainClass>dev.ayagmar.quarkusforge.QuarkusForgeCli</mainClass>
      </transformer>
    </transformers>
  </configuration>
</execution>
```

Overriding this execution from a profile requires matching the exact ID (`default`) AND
re-specifying all `<transformers>` (Maven merges configurations but transformers are
list-replaced, not merged). To avoid silent loss of `ServicesResourceTransformer`, use
a **property-based approach**:

1. Extract main class and final name into properties in the base `<properties>`:
   ```xml
   <forge.main.class>dev.ayagmar.quarkusforge.QuarkusForgeCli</forge.main.class>
   <forge.jar.name>quarkus-forge</forge.jar.name>
   ```
2. Reference these properties in the shade plugin configuration.
3. The `headless` profile overrides both properties.

This way the profile only sets two properties and never touches plugin config.

---

## 5. Implementation Phases

### Phase 0 — Safety net (do first, before any refactor)

Run and record baseline:
```bash
mvn test -q 2>&1 | tail -5   # must be green; record number of passing tests
```

### Pre-Phase 1 — Move favorites store out of `ui/`

**Motivation**: `HeadlessGenerationService` needs `ExtensionFavoritesStore` to resolve the
`--preset favorites` option. These classes have zero tamboui deps; they belong in `api/`.

**Files to move** (all have only `api/` or stdlib imports):
- `ui/ExtensionFavoritesStore.java` → `api/ExtensionFavoritesStore.java`
- `ui/FileBackedExtensionFavoritesStore.java` → `api/FileBackedExtensionFavoritesStore.java`
- `ui/ExtensionFavoritesPayload.java` → `api/ExtensionFavoritesPayload.java`
- `ui/ExtensionFavoriteIds.java` → `api/ExtensionFavoriteIds.java`
- `ui/InMemoryExtensionFavoritesStore.java` → `api/InMemoryExtensionFavoritesStore.java`

Change package declaration in each file from `dev.ayagmar.quarkusforge.ui` to
`dev.ayagmar.quarkusforge.api`.

Update all import sites (search with: `rg "quarkusforge.ui.Extension" --include="*.java"`):
- Affected files: `HeadlessGenerationService.java`, `RuntimeConfig.java` (via defaults),
  `TuiBootstrapService.java`, test files.

**Also fix `RuntimeConfig.defaults()`** — replace:
```java
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.ui.UserPreferencesStore;
// ...
ExtensionFavoritesStore.defaultFile()  →  ForgeDataPaths.favoritesFile()
UserPreferencesStore.defaultFile()     →  ForgeDataPaths.preferencesFile()
```
Remove both `ui/` imports from `RuntimeConfig`. This breaks the last hidden `ui/` dep in the
headless compile scope.

**Verification loop:**
```bash
mvn test -q          # all tests still green
rg "quarkusforge.ui.Extension" src/   # must return 0 results
rg "quarkusforge.ui.UserPreferences|quarkusforge.ui.ExtensionFavorites" src/   # 0 results
rg "import dev.ayagmar.quarkusforge.ui" src/main/java/dev/ayagmar/quarkusforge/RuntimeConfig.java
# must return 0 results
```

### Phase 1 — Extract exit codes + update property references in pom.xml

**Part A — ExitCodes class**

**File**: `src/main/java/dev/ayagmar/quarkusforge/ExitCodes.java`

```java
final class ExitCodes {
    private ExitCodes() {}
    static final int OK         = 0;
    static final int USAGE      = 1;
    static final int VALIDATION = 2;
    static final int NETWORK    = 3;
    static final int ARCHIVE    = 4;
    static final int CANCELLED  = 130;
}
```

**Changes to production code:**
- `QuarkusForgeCli`: remove `EXIT_CODE_*` constants; replace all `EXIT_CODE_*` refs AND all
  `CommandLine.ExitCode.*` usages (5 occurrences: lines returning `USAGE` and `OK`) with
  `ExitCodes.*`; keep the `import picocli.CommandLine` (still needed for `CommandLine` itself)
- `AsyncFailureHandler`: replace `QuarkusForgeCli.EXIT_CODE_CANCELLED` and
  `QuarkusForgeCli.EXIT_CODE_NETWORK` → `ExitCodes.*` (3 occurrences)
- `HeadlessGenerationService`: replace `QuarkusForgeCli.EXIT_CODE_VALIDATION` →
  `ExitCodes.VALIDATION`; `CommandLine.ExitCode.OK` → `ExitCodes.OK`;
  remove `import picocli.CommandLine` (now unused)
- `ProjectRequestFactory`: replace `QuarkusForgeCli.EXIT_CODE_*` → `ExitCodes.*`

**Changes to test code (same Phase):**
- `QuarkusForgeGenerateCommandTest`: replace all `QuarkusForgeCli.EXIT_CODE_*` → `ExitCodes.*`
- `HeadlessGenerationServiceTest`: replace `QuarkusForgeCli.EXIT_CODE_NETWORK` → `ExitCodes.NETWORK`; `CommandLine.ExitCode.OK` → `ExitCodes.OK`; remove `import picocli.CommandLine`
- `QuarkusForgeCliStartupMetadataTest`: replace `QuarkusForgeCli.EXIT_CODE_VALIDATION` → `ExitCodes.VALIDATION`
- `ProjectRequestFactoryTest`: replace all `QuarkusForgeCli.EXIT_CODE_*` → `ExitCodes.*` (6 occurrences)

**Part B — Maven property extraction**

In base `<properties>` section of `pom.xml`:
```xml
<forge.main.class>dev.ayagmar.quarkusforge.QuarkusForgeCli</forge.main.class>
<forge.jar.name>quarkus-forge</forge.jar.name>
```

Update the shade plugin `<configuration>` block:
```xml
<mainClass>${forge.main.class}</mainClass>
<!-- ... -->
<finalName>${forge.jar.name}</finalName>
```

Update the native plugin `<configuration>`:
```xml
<mainClass>${forge.main.class}</mainClass>
<imageName>${forge.jar.name}</imageName>
```

**Verification loop:**
```bash
mvn test -q                              # all tests still green
rg "QuarkusForgeCli\.EXIT_CODE" src/    # must return 0 results
rg "CommandLine\.ExitCode" src/main/    # must return 0 results
mvn package -DskipTests -q              # jar produced as quarkus-forge.jar
```

### Phase 2 — Move TUI config helpers out of QuarkusForgeCli

Move from `QuarkusForgeCli` into `TuiBootstrapService` (making them private):
- `configureTerminalBackendPreference()`
- `appTuiConfig()`
- `appBindingsProfile()`
- Constants: `BACKEND_PROPERTY_NAME`, `BACKEND_ENV_NAME`, `PANAMA_BACKEND`, `TUI_TICK_RATE`

`TuiBootstrapService.run()` already calls these via `QuarkusForgeCli.*` — move them inline.

Remove the now-unused `dev.tamboui.tui.TuiConfig` and `dev.tamboui.tui.bindings.Bindings` imports
from `QuarkusForgeCli`. Also remove the now-unused `TUI_TICK_RATE` static field from
`QuarkusForgeCli` (it moves into `TuiBootstrapService`).

> **Do NOT remove `TUI_BOOTSTRAP_SERVICE`** — it is still used by `QuarkusForgeCli.runTui()`
> which delegates to `TUI_BOOTSTRAP_SERVICE.run(...)`. Only `TUI_TICK_RATE` becomes unused in
> `QuarkusForgeCli` after this move.

**Verification loop:**
```bash
rg "import dev.tamboui" src/main/java/dev/ayagmar/quarkusforge/QuarkusForgeCli.java
# must return 0 results

mvn test -q   # all tests still green
```

### Phase 3 — HeadlessRunner interface + GenerateCommand parent change

**File**: `src/main/java/dev/ayagmar/quarkusforge/HeadlessRunner.java`

```java
interface HeadlessRunner {
    int runHeadlessGenerate(GenerateCommand command);
}
```

**Changes:**
- `QuarkusForgeCli implements HeadlessRunner` (add `implements` clause)
- `GenerateCommand`: change `@ParentCommand QuarkusForgeCli rootCommand` →
  `@ParentCommand HeadlessRunner rootCommand`

**Verification loop:**
```bash
mvn compile -q    # check picocli-codegen produces no errors about parent command type
mvn test -q       # GenerateCommand tests must still pass
```

> **Risk**: picocli annotation processor (`picocli-codegen`) may fail to resolve the
> interface parent type at compile time.
>
> **How to verify AP succeeded**: check that `target/classes/META-INF/picocli/` contains
> the `CommandSpec` descriptors after `mvn compile`.
>
> **Fallback if AP fails**: use `@Spec CommandLine.Model.CommandSpec spec` in
> `GenerateCommand` and retrieve the parent via
> `(HeadlessRunner) spec.parent().userObject()` in `call()`.

### Phase 4 — HeadlessCli entry point

**File**: `src/main/java/dev/ayagmar/quarkusforge/HeadlessCli.java`

The class must import **nothing from `ui/`**, `TuiBootstrapService`, `AppBindingsProfile`,
`PostTuiActionExecutor`, or `TuiSessionSummary`.

```java
package dev.ayagmar.quarkusforge;

import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "quarkus-forge",
    versionProvider = CliVersionProvider.class,
    subcommands = {GenerateCommand.class},
    description = "Quarkus Forge headless CLI (no TUI)")
public final class HeadlessCli implements Callable<Integer>, HeadlessRunner {

    @Spec CommandSpec spec;

    @Option(names = "--verbose", defaultValue = "false",
            description = "Emit structured JSON-line diagnostics to stderr")
    private boolean verbose;

    @Option(names = {"-h", "--help"}, usageHelp = true,
            description = "Show this help message and exit.")
    private boolean helpRequested;

    @Option(names = {"-V", "--version"}, versionHelp = true,
            description = "Print version information and exit.")
    private boolean versionRequested;

    private final RuntimeConfig runtimeConfig;
    private final HeadlessGenerationService headlessService;

    public HeadlessCli() { this(RuntimeConfig.defaults()); }

    HeadlessCli(RuntimeConfig runtimeConfig) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
        this.headlessService = new HeadlessGenerationService(
                new HeadlessCatalogClient(runtimeConfig), runtimeConfig);
    }

    @Override
    public Integer call() {
        // Root command without subcommand: print usage (no TUI to launch)
        spec.commandLine().usage(System.out);
        return ExitCodes.OK;
    }

    @Override
    public int runHeadlessGenerate(GenerateCommand command) {
        return headlessService.run(command, false, verbose);
    }

    public static int runWithArgs(String[] args) {
        return runWithArgs(args, RuntimeConfig.defaults());
    }

    static int runWithArgs(String[] args, RuntimeConfig runtimeConfig) {
        return new CommandLine(new HeadlessCli(runtimeConfig)).execute(args);
    }

    public static void main(String[] args) {
        int exit = runWithArgs(args);
        if (exit != 0) System.exit(exit);
    }
}
```

**Verify no `ui/` in import closure:**
```bash
rg "import dev.tamboui|quarkusforge.ui\." \
    src/main/java/dev/ayagmar/quarkusforge/HeadlessCli.java
# must return 0 results

mvn compile -q
mvn test -q
```

### Phase 5 — Maven headless profile

Add to `pom.xml` `<profiles>` section:

```xml
<profile>
  <id>headless</id>
  <properties>
    <!-- Override main class and jar name for headless artifact -->
    <forge.main.class>dev.ayagmar.quarkusforge.HeadlessCli</forge.main.class>
    <forge.jar.name>quarkus-forge-headless</forge.jar.name>
    <!-- Skip tests: many test files reference QuarkusForgeCli and TUI classes.
         Use maven.test.skip (not skipTests) to skip BOTH compilation and execution.
         skipTests alone would still compile test sources and fail on excluded class refs. -->
    <maven.test.skip>true</maven.test.skip>
  </properties>
  <dependencies>
    <!-- Demote tamboui to provided so it is excluded from the shaded jar -->
    <dependency>
      <groupId>dev.tamboui</groupId>
      <artifactId>tamboui-tui</artifactId>
      <version>${tamboui.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>dev.tamboui</groupId>
      <artifactId>tamboui-panama-backend</artifactId>
      <version>${tamboui.version}</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <excludes>
            <exclude>dev/ayagmar/quarkusforge/ui/**</exclude>
            <exclude>dev/ayagmar/quarkusforge/AppBindingsProfile.java</exclude>
            <exclude>dev/ayagmar/quarkusforge/TuiBootstrapService.java</exclude>
            <exclude>dev/ayagmar/quarkusforge/QuarkusForgeCli.java</exclude>
            <exclude>dev/ayagmar/quarkusforge/PostTuiActionExecutor.java</exclude>
            <exclude>dev/ayagmar/quarkusforge/TuiSessionSummary.java</exclude>
            <exclude>dev/ayagmar/quarkusforge/StartupMetadataSelection.java</exclude>
          </excludes>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

> **How the shade plugin picks up the override**: in Phase 1 we replaced hardcoded values
> with `${forge.main.class}` and `${forge.jar.name}`. The headless profile overrides these
> properties, so the shade plugin automatically uses `HeadlessCli` and outputs
> `quarkus-forge-headless.jar` — no separate shade execution needed.

**Verification loop:**
```bash
# 1. Compile only (fast check)
mvn -Pheadless compile -q

# 2. Build jar
mvn -Pheadless package -q

# 3. Verify tamboui is absent
jar tf target/quarkus-forge-headless.jar | grep "tamboui"
# must return empty

# 4. Verify main class in manifest
unzip -p target/quarkus-forge-headless.jar META-INF/MANIFEST.MF | grep Main-Class
# must show: Main-Class: dev.ayagmar.quarkusforge.HeadlessCli

# 5. Smoke-test the jar (--help checks require no network; --dry-run requires code.quarkus.io)
java -jar target/quarkus-forge-headless.jar --help
java -jar target/quarkus-forge-headless.jar generate --help
# The following requires network access to code.quarkus.io — skip in offline CI:
java -jar target/quarkus-forge-headless.jar generate --dry-run \
    --group-id org.acme --artifact-id demo
# exit 0, prints "Prefill validated successfully" or similar dry-run summary

# 6. Default build still works
mvn package -DskipTests -q
jar tf target/quarkus-forge.jar | grep "tamboui" | wc -l
# must be > 0 (tamboui still present in default build)
```

### Phase 6 — Tests

Add `HeadlessCliTest` (new file, uses WireMock like `QuarkusForgeGenerateCommandTest`).

**First**, extract `stubCatalogEndpoints()` from the private method in `QuarkusForgeGenerateCommandTest`
into `CliCommandTestSupport` as a static helper (so both test classes can reuse it).

```java
class HeadlessCliTest {
    @TempDir Path tempDir;
    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @AfterEach
    void tearDown() { if (wireMockServer != null) wireMockServer.stop(); }

    @Test
    void headless_root_command_without_subcommand_prints_usage_and_exits_zero() {
        // No mocks needed — root command just prints usage
        int exit = HeadlessCli.runWithArgs(new String[]{},
                CliCommandTestSupport.runtimeConfig(tempDir,
                    URI.create("http://localhost:" + wireMockServer.port())));
        assertThat(exit).isEqualTo(ExitCodes.OK);
    }

    @Test
    void headless_generate_dry_run_exits_zero() {
        CliCommandTestSupport.stubCatalogEndpoints();   // extracted from QuarkusForgeGenerateCommandTest
        RuntimeConfig config = CliCommandTestSupport.runtimeConfig(tempDir,
                URI.create("http://localhost:" + wireMockServer.port()));
        int exit = HeadlessCli.runWithArgs(
                new String[]{"generate", "--dry-run", "-g", "org.acme", "-a", "demo"},
                config);
        assertThat(exit).isEqualTo(ExitCodes.OK);
    }
}
```

> `CliCommandTestSupport.stubCatalogEndpoints()` must be extracted from `QuarkusForgeGenerateCommandTest`
> in this Phase and the original calls updated to use the shared static method.

Also add to `CliCommandTestSupport`:
```java
static CommandResult runHeadless(RuntimeConfig runtimeConfig, String... args) {
    return captureCommandOutput(() -> HeadlessCli.runWithArgs(args, runtimeConfig));
}
```

**Note**: the new `HeadlessCliTest` is a default-profile test (it compiles and runs against
the full codebase, where `HeadlessCli` is just another class). The headless profile itself
has tests skipped.

### Phase 7 — Native headless profile

The `headless-native` profile adds only the native image build goal. All source exclusions
and dependency demotions come from the `headless` profile, so **both must be active together**:
`mvn -Pheadless,headless-native package`.

```xml
<profile>
  <id>headless-native</id>
  <properties>
    <native.skip>false</native.skip>
    <native.access.flag>--enable-native-access=ALL-UNNAMED</native.access.flag>
    <native.profile.active>true</native.profile.active>
  </properties>
  <build>
    <plugins>
      <plugin>
        <groupId>org.graalvm.buildtools</groupId>
        <artifactId>native-maven-plugin</artifactId>
        <version>${graalvm.native.plugin.version}</version>
        <extensions>true</extensions>
        <executions>
          <execution>
            <id>build-native-headless</id>
            <phase>package</phase>
            <goals>
              <goal>compile-no-fork</goal>
            </goals>
          </execution>
        </executions>
        <configuration>
          <skip>${native.skip}</skip>
          <!-- forge.main.class and forge.jar.name already set by headless profile -->
          <imageName>${forge.jar.name}</imageName>
          <mainClass>${forge.main.class}</mainClass>
          <buildArgs>
            <buildArg>--no-fallback</buildArg>
            <buildArg>-Os</buildArg>
            <buildArg>--emit build-report</buildArg>
            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
            <!-- -H:+SharedArenaSupport was needed for tamboui's Panama backend; tentatively
                 omit it here. If the native build fails with a SharedArena error, add it back. -->
            <buildArg>--enable-url-protocols=http,https</buildArg>
            <!-- Keep native-access flag for safety; HttpClient internals may need it.
                 Remove only if the build succeeds without it and size reduction is desired. -->
            <buildArg>${native.access.flag}</buildArg>
          </buildArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

**Verification:**
```bash
mvn -Pheadless,headless-native package
./target/quarkus-forge-headless generate --help
./target/quarkus-forge-headless generate --dry-run -g org.acme -a demo
# check binary size (expect ≈ 8–15 MB reduction from baseline ~27.5 MB)
```

> **Note**: GraalVM resource-config and reflect-config may need a headless variant if the
> native build fails with missing reflective access. Start without custom configs; add
> only if native compile fails with a specific reflection error.
>
> `-H:+SharedArenaSupport` was required for tamboui's Panama foreign-function backend.
> It is omitted tentatively; add it back only if the native build reports a `SharedArena`
> error. `${native.access.flag}` (`--enable-native-access=ALL-UNNAMED`) is kept since
> `java.net.http.HttpClient` internals may require it for native compilation.

### Phase 8 — Documentation

- **README**: add "Headless Build" section:
  - `mvn -Pheadless package` (JVM jar, no TUI)
  - `mvn -Pheadless,headless-native package` (native binary, requires GraalVM)
  - Expected artifact: `target/quarkus-forge-headless.jar` / `target/quarkus-forge-headless`
  - Note that `generate --help` shows all available options
- **`docs/modules/ROOT/pages/cli/headless-mode.adoc`**: update build instructions to reflect
  the new `-Pheadless` flag (the file currently documents headless mode at the feature level;
  add a "Building the headless artifact" section)

---

## 6. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| picocli-codegen rejects `HeadlessRunner` interface as `@ParentCommand` type | Medium | Medium | Test in Phase 3; fallback: `@Spec CommandSpec spec` + cast via `(HeadlessRunner) spec.parent().userObject()` in `GenerateCommand.call()` |
| `maven-compiler-plugin` `<excludes>` miss a file after future refactoring | Medium | Low | Add a CI check: `jar tf target/quarkus-forge-headless.jar | grep tamboui` must be empty |
| Existing test files reference `QuarkusForgeCli` / TUI — headless compile would fail | Certain | None | Mitigated by `<maven.test.skip>true</maven.test.skip>` in headless profile |
| `provided` tamboui scope means an accidentally-included TUI file COMPILES but fails at runtime with `ClassNotFoundException` | Low | High | Phase 5 smoke tests (`java -jar ... --help`, `generate --help`) catch this before any acceptance check; CI must run these tests |
| `RuntimeConfig` or other headless-scope files gain new `ui/` imports in future | Medium | Low | Phase 5 verification step `mvn -Pheadless compile` is part of CI for the headless profile |
| GraalVM native config for headless needs separate reflect/resource config | Medium | Medium | Start without; add only if native compile fails with specific reflection error |
| Moving favorites classes to `api/` breaks import in many test/TUI files | Low | Medium | `rg` search before moving to enumerate all import sites; update all in same commit |
| `-H:+SharedArenaSupport` needed in headless-native despite removing tamboui | Low | Low | Build without it first; add back only if GraalVM reports a SharedArena error |

---

## 7. Acceptance Checklist

- [ ] `mvn test` (default profile) green before and after all phases (no regressions)
- [ ] `rg "quarkusforge.ui.Extension" src/` returns 0 results (Pre-Phase 1 done)
- [ ] `rg "import dev.tamboui" src/main/java/dev/ayagmar/quarkusforge/QuarkusForgeCli.java` returns 0 results (Phase 2 done)
- [ ] `rg "QuarkusForgeCli\.EXIT_CODE" src/` returns 0 results (Phase 1 done)
- [ ] `rg "CommandLine\.ExitCode" src/main/` returns 0 results (Phase 1 done)
- [ ] `rg "QuarkusForgeCli" src/main/java/dev/ayagmar/quarkusforge/HeadlessGenerationService.java` returns 0 results
- [ ] `rg "QuarkusForgeCli" src/main/java/dev/ayagmar/quarkusforge/ProjectRequestFactory.java` returns 0 results
- [ ] `mvn -Pheadless compile -q` succeeds
- [ ] `mvn -Pheadless package -q` succeeds
- [ ] `jar tf target/quarkus-forge-headless.jar | grep tamboui` returns empty
- [ ] `unzip -p target/quarkus-forge-headless.jar META-INF/MANIFEST.MF | grep Main-Class` shows `HeadlessCli`
- [ ] `java -jar target/quarkus-forge-headless.jar generate --dry-run -g org.acme -a demo` exits 0
- [ ] `java -jar target/quarkus-forge.jar` (default) still launches TUI
- [ ] `mvn test` after all changes still green
- [ ] `HeadlessCliTest` passes (both test methods green)
- [ ] README updated with headless build section
- [ ] `docs/modules/ROOT/pages/cli/headless-mode.adoc` updated
- [ ] Native headless binary (optional): `mvn -Pheadless,headless-native package` succeeds; binary size < 20 MB

---

## 8. Order of Operations

```
Phase 0 (baseline test run)
  → Pre-Phase 1 (move favorites to api/, fix RuntimeConfig) → verify
    → Phase 1 (ExitCodes + Maven properties) → verify
      → Phase 2 (move TUI helpers to TuiBootstrapService) → verify
        → Phase 3 (HeadlessRunner interface + GenerateCommand) → verify
          → Phase 4 (HeadlessCli entry point) → verify
            → Phase 5 (Maven headless profile) → verify
              → Phase 6 (HeadlessCliTest) → verify
                → Phase 7 (native headless, optional)
                  → Phase 8 (docs)
```

Each phase is independently commitable. If a phase hits a blocker, all preceding
phases are already in place and not wasted.

---

## 9. Non-Goals (explicitly not doing)

- No runtime feature flags or `if (headless)` branches in shared code
- No changes to the TUI code path or TUI behavior
- No merging of `QuarkusForgeCli` and `HeadlessCli` into one class with conditional logic
- No removal of picocli from the default profile
- No running the full test suite under the headless Maven profile
