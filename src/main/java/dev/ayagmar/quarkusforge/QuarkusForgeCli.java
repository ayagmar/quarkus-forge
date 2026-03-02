package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import dev.ayagmar.quarkusforge.ui.UserPreferencesStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(
    name = "quarkus-forge",
    versionProvider = CliVersionProvider.class,
    subcommands = {GenerateCommand.class},
    description = "Quarkus forge terminal UI")
public final class QuarkusForgeCli implements Callable<Integer>, HeadlessRunner {

  private static final Duration STARTUP_METADATA_REFRESH_TIMEOUT = Duration.ofSeconds(2);
  private static final ShellExecutor SHELL_EXECUTOR = new ShellExecutor();
  private static final PostTuiActionExecutor POST_TUI_ACTION_EXECUTOR =
      new PostTuiActionExecutor(SHELL_EXECUTOR);
  private static final TuiBootstrapService TUI_BOOTSTRAP_SERVICE = new TuiBootstrapService();

  @Mixin private RequestOptions requestOptions = new RequestOptions();

  private final RuntimeConfig runtimeConfig;
  private final HeadlessGenerationService headlessGenerationService;

  @Option(
      names = "--dry-run",
      defaultValue = "false",
      description = "Validate CLI prefill and print summary without starting TUI")
  private boolean dryRun;

  @Option(
      names = "--search-debounce-ms",
      defaultValue = "0",
      description = "Debounce delay for extension search updates")
  private int searchDebounceMs;

  @Option(
      names = "--verbose",
      defaultValue = "false",
      description = "Emit structured JSON-line diagnostics to stderr")
  private boolean verbose;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "Show this help message and exit.")
  private boolean helpRequested;

  @Option(
      names = {"-V", "--version"},
      versionHelp = true,
      description = "Print version information and exit.")
  private boolean versionRequested;

  @Option(
      names = "--post-generate-hook",
      defaultValue = "",
      description = "Shell command executed in generated project directory after TUI exits")
  private String postGenerateHookCommand;

  public QuarkusForgeCli() {
    this(RuntimeConfig.defaults());
  }

  QuarkusForgeCli(RuntimeConfig runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
    this.headlessGenerationService =
        new HeadlessGenerationService(new HeadlessCatalogClient(runtimeConfig), runtimeConfig);
  }

  @Override
  public Integer call() throws Exception {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    diagnostics.info("cli.start", df("mode", dryRun ? "dry-run" : "tui"));

    UserPreferencesStore preferencesStore =
        UserPreferencesStore.fileBacked(runtimeConfig.preferencesFile());
    if (!dryRun) {
      applyStoredRequestDefaults(requestOptions, preferencesStore.loadLastRequest());
    }
    ProjectRequest request = ProjectRequestFactory.fromOptions(requestOptions);
    StartupMetadataSelection startupMetadataSelection = loadStartupMetadataSelection(diagnostics);
    ProjectRequest requestWithResolvedStream =
        ProjectRequestFactory.applyRecommendedPlatformStream(
            request, startupMetadataSelection.metadataCompatibility());
    ForgeUiState initialState =
        ProjectRequestFactory.buildInitialState(
            requestWithResolvedStream, startupMetadataSelection.metadataCompatibility());
    if (!initialState.canSubmit() && shouldBlockOnStartupValidation(dryRun)) {
      diagnostics.error(
          "cli.validation.failed", df("errorCount", initialState.validation().errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return ExitCodes.VALIDATION;
    }

    if (dryRun) {
      diagnostics.info(
          "cli.dry-run.validated", df("metadataSource", startupMetadataSelection.sourceLabel()));
      printPrefillSummary(
          initialState.request(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return ExitCodes.OK;
    }

    diagnostics.info("cli.tui.launch", df("searchDebounceMs", searchDebounceMs));
    TuiSessionSummary summary = runTui(initialState, searchDebounceMs, runtimeConfig, diagnostics);
    preferencesStore.saveLastRequest(summary.finalRequest());
    POST_TUI_ACTION_EXECUTOR.execute(summary, postGenerateHookCommand, diagnostics);
    return ExitCodes.OK;
  }

  public static int runWithArgs(String[] args) {
    return runWithArgs(args, RuntimeConfig.defaults());
  }

  static int runWithArgs(String[] args, RuntimeConfig runtimeConfig) {
    return new CommandLine(new QuarkusForgeCli(runtimeConfig)).execute(args);
  }

  static boolean shouldBlockOnStartupValidation(boolean dryRunMode) {
    return dryRunMode;
  }

  public static void main(String[] args) {
    int exitCode = runWithArgs(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static TuiSessionSummary runTui(
      ForgeUiState initialState,
      int searchDebounceMs,
      RuntimeConfig runtimeConfig,
      DiagnosticLogger diagnostics)
      throws Exception {
    return TUI_BOOTSTRAP_SERVICE.run(initialState, searchDebounceMs, runtimeConfig, diagnostics);
  }

  int runSmokeForTest(boolean verbose) {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    diagnostics.info("cli.start", df("mode", "smoke-test"));

    ProjectRequest request = ProjectRequestFactory.fromOptions(defaultRequestOptions());
    StartupMetadataSelection startupMetadataSelection = loadStartupMetadataSelection(diagnostics);
    ProjectRequest requestWithResolvedStream =
        ProjectRequestFactory.applyRecommendedPlatformStream(
            request, startupMetadataSelection.metadataCompatibility());
    ForgeUiState initialState =
        ProjectRequestFactory.buildInitialState(
            requestWithResolvedStream, startupMetadataSelection.metadataCompatibility());
    if (!initialState.canSubmit()) {
      diagnostics.error(
          "cli.validation.failed", df("errorCount", initialState.validation().errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return ExitCodes.VALIDATION;
    }

    runHeadlessSmoke(initialState, 0, runtimeConfig, diagnostics);
    return ExitCodes.OK;
  }

  private static RequestOptions defaultRequestOptions() {
    RequestOptions defaults = new RequestOptions();
    defaults.groupId = "org.acme";
    defaults.artifactId = "quarkus-app";
    defaults.version = "1.0.0-SNAPSHOT";
    defaults.packageName = null;
    defaults.outputDirectory = ".";
    defaults.platformStream = "";
    defaults.buildTool = "maven";
    defaults.javaVersion = "25";
    return defaults;
  }

  private static void applyStoredRequestDefaults(
      RequestOptions requestOptions, CliPrefill storedPrefill) {
    if (storedPrefill == null) {
      return;
    }
    RequestOptions defaults = defaultRequestOptions();
    if (Objects.equals(requestOptions.groupId, defaults.groupId)
        && !storedPrefill.groupId().isBlank()) {
      requestOptions.groupId = storedPrefill.groupId();
    }
    if (Objects.equals(requestOptions.artifactId, defaults.artifactId)
        && !storedPrefill.artifactId().isBlank()) {
      requestOptions.artifactId = storedPrefill.artifactId();
    }
    if (Objects.equals(requestOptions.version, defaults.version)
        && !storedPrefill.version().isBlank()) {
      requestOptions.version = storedPrefill.version();
    }
    if ((requestOptions.packageName == null || requestOptions.packageName.isBlank())
        && !storedPrefill.packageName().isBlank()) {
      requestOptions.packageName = storedPrefill.packageName();
    }
    if (Objects.equals(requestOptions.outputDirectory, defaults.outputDirectory)
        && !storedPrefill.outputDirectory().isBlank()) {
      requestOptions.outputDirectory = storedPrefill.outputDirectory();
    }
    if (Objects.equals(requestOptions.platformStream, defaults.platformStream)
        && !storedPrefill.platformStream().isBlank()) {
      requestOptions.platformStream = storedPrefill.platformStream();
    }
    if (Objects.equals(requestOptions.buildTool, defaults.buildTool)
        && !storedPrefill.buildTool().isBlank()) {
      requestOptions.buildTool = storedPrefill.buildTool();
    }
    if (Objects.equals(requestOptions.javaVersion, defaults.javaVersion)
        && !storedPrefill.javaVersion().isBlank()) {
      requestOptions.javaVersion = storedPrefill.javaVersion();
    }
  }

  static void runHeadlessSmoke(
      ForgeUiState initialState,
      int searchDebounceMs,
      RuntimeConfig runtimeConfig,
      DiagnosticLogger diagnostics) {
    diagnostics.info(
        "tui.session.start",
        df("smokeMode", true),
        df("searchDebounceMs", Math.max(0, searchDebounceMs)),
        df("mode", "headless-smoke"));
    try (QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
      CatalogDataService catalogDataService =
          new CatalogDataService(
              apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
      diagnostics.info("catalog.load.start", df("mode", "tui"));
      catalogDataService.load().handle(catalogLoadDiagnostics(diagnostics)).join();
      diagnostics.info("tui.session.exit", df("outcome", "completed"));
    }
  }

  static java.util.function.BiFunction<CatalogData, Throwable, ExtensionCatalogLoadResult>
      catalogLoadDiagnostics(DiagnosticLogger diagnostics) {
    return (catalogData, throwable) -> {
      if (throwable == null) {
        diagnostics.info(
            "catalog.load.success",
            df("mode", "tui"),
            df("source", catalogData.source().label()),
            df("stale", catalogData.stale()),
            df("detail", catalogData.detailMessage()));
        return toExtensionCatalogLoadResult(catalogData);
      }
      Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(throwable);
      if (cause instanceof CancellationException) {
        diagnostics.error("catalog.load.cancelled", df("mode", "tui"));
      } else {
        diagnostics.error(
            "catalog.load.failure",
            df("mode", "tui"),
            df("causeType", cause.getClass().getSimpleName()),
            df("message", ErrorMessageMapper.userFriendlyError(cause)));
      }
      throw new CompletionException(cause);
    };
  }

  private static ExtensionCatalogLoadResult toExtensionCatalogLoadResult(CatalogData catalogData) {
    return new ExtensionCatalogLoadResult(
        catalogData.extensions(),
        catalogData.source(),
        catalogData.stale(),
        catalogData.detailMessage(),
        catalogData.metadata());
  }

  private static void printPrefillSummary(
      ProjectRequest request, String sourceLabel, String sourceDetail) {
    Path generatedProjectDirectory =
        Path.of(request.outputDirectory()).resolve(request.artifactId()).normalize();
    System.out.println("Prefill validated successfully:");
    System.out.println(" - groupId: " + request.groupId());
    System.out.println(" - artifactId: " + request.artifactId());
    System.out.println(" - version: " + request.version());
    System.out.println(" - packageName: " + request.packageName());
    System.out.println(" - outputDirectory: " + request.outputDirectory());
    System.out.println(" - platformStream: " + request.platformStream());
    System.out.println(" - buildTool: " + request.buildTool());
    System.out.println(" - javaVersion: " + request.javaVersion());
    System.out.println(" - metadataSource: " + sourceLabel);
    if (sourceDetail != null && !sourceDetail.isBlank()) {
      System.out.println(" - metadataDetail: " + sourceDetail);
    }
    System.out.println(" - generatedProjectDirectory: " + generatedProjectDirectory);
  }

  private StartupMetadataSelection loadStartupMetadataSelection(DiagnosticLogger diagnostics) {
    try (QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
      MetadataDto metadata =
          apiClient
              .fetchMetadata()
              .get(STARTUP_METADATA_REFRESH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      diagnostics.info("metadata.load.success", df("source", "live"));
      return new StartupMetadataSelection(
          MetadataCompatibilityContext.success(metadata), "live", "");
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      StartupMetadataSelection selection =
          snapshotFallbackSelection("Live metadata refresh interrupted");
      diagnostics.error(
          "metadata.load.fallback",
          df("source", selection.sourceLabel()),
          df("detail", selection.detailMessage()));
      return selection;
    } catch (TimeoutException timeoutException) {
      StartupMetadataSelection selection =
          snapshotFallbackSelection(
              "Live metadata refresh timed out after "
                  + STARTUP_METADATA_REFRESH_TIMEOUT.toMillis()
                  + "ms");
      diagnostics.error(
          "metadata.load.fallback",
          df("source", selection.sourceLabel()),
          df("detail", selection.detailMessage()));
      return selection;
    } catch (ExecutionException executionException) {
      Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(executionException);
      StartupMetadataSelection selection =
          snapshotFallbackSelection(
              "Live metadata unavailable (%s)"
                  .formatted(ErrorMessageMapper.userFriendlyError(cause)));
      diagnostics.error(
          "metadata.load.fallback",
          df("source", selection.sourceLabel()),
          df("detail", selection.detailMessage()),
          df("causeType", cause.getClass().getSimpleName()));
      return selection;
    }
  }

  private static StartupMetadataSelection snapshotFallbackSelection(String fallbackReason) {
    MetadataCompatibilityContext snapshotCompatibility = MetadataCompatibilityContext.loadDefault();
    String detailMessage =
        fallbackReason
            + (snapshotCompatibility.loadError() == null
                ? "; using bundled metadata snapshot"
                : "; bundled metadata snapshot unavailable");
    return new StartupMetadataSelection(snapshotCompatibility, "snapshot fallback", detailMessage);
  }

  @Override
  public int runHeadlessGenerate(GenerateCommand command) {
    return headlessGenerationService.run(command, dryRun, verbose);
  }

  private static DiagnosticField df(String name, Object value) {
    return DiagnosticField.of(name, value);
  }
}
