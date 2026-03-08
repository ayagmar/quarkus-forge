package dev.ayagmar.quarkusforge.cli;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.application.DefaultStartupStateService;
import dev.ayagmar.quarkusforge.application.LiveStartupMetadataLoader;
import dev.ayagmar.quarkusforge.application.StartupMetadataLoader;
import dev.ayagmar.quarkusforge.application.StartupMetadataSelection;
import dev.ayagmar.quarkusforge.application.StartupRequest;
import dev.ayagmar.quarkusforge.application.StartupState;
import dev.ayagmar.quarkusforge.application.StartupStateService;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.headless.HeadlessGenerationService;
import dev.ayagmar.quarkusforge.headless.HeadlessOutputPrinter;
import dev.ayagmar.quarkusforge.postgen.PostTuiActionExecutor;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import dev.ayagmar.quarkusforge.runtime.RuntimeWiring;
import dev.ayagmar.quarkusforge.runtime.TuiBootstrapService;
import dev.ayagmar.quarkusforge.runtime.TuiSessionSummary;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
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

  private static final PostTuiActionExecutor POST_TUI_ACTION_EXECUTOR = new PostTuiActionExecutor();
  private static final TuiBootstrapService TUI_BOOTSTRAP_SERVICE = new TuiBootstrapService();
  private static final StartupStateService STARTUP_STATE_SERVICE = new DefaultStartupStateService();

  @Mixin private RequestOptions requestOptions = new RequestOptions();

  private final RuntimeConfig runtimeConfig;
  private final Function<java.net.URI, QuarkusApiClient> apiClientFactory;

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

  @Option(names = "--interactive-smoke-test", hidden = true, defaultValue = "false")
  private boolean interactiveSmokeTest;

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
    this(runtimeConfig, QuarkusApiClient::new);
  }

  QuarkusForgeCli(
      RuntimeConfig runtimeConfig, Function<java.net.URI, QuarkusApiClient> apiClientFactory) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
    this.apiClientFactory = Objects.requireNonNull(apiClientFactory);
  }

  @Override
  public Integer call() throws Exception {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    if (interactiveSmokeTest) {
      return runSmoke(
          diagnostics,
          "interactive-smoke",
          (initialState, smokeDiagnostics) ->
              TUI_BOOTSTRAP_SERVICE.runInteractiveSmoke(
                  initialState, runtimeConfig, smokeDiagnostics));
    }

    diagnostics.info("cli.start", of("mode", dryRun ? "dry-run" : "tui"));

    if (!dryRun) {
      applyStoredRequestDefaults(requestOptions, RuntimeWiring.loadStoredCliPrefill(runtimeConfig));
    }
    StartupState startupState = loadStartupState(requestOptions, diagnostics);
    StartupMetadataSelection startupMetadataSelection = startupState.metadataSelection();
    ForgeUiState initialState = startupState.initialState();
    if (!initialState.canSubmit() && dryRun) {
      diagnostics.error(
          "cli.validation.failed", of("errorCount", initialState.validation().errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return ExitCodes.VALIDATION;
    }

    if (dryRun) {
      diagnostics.info(
          "cli.dry-run.validated", of("metadataSource", startupMetadataSelection.sourceLabel()));
      HeadlessOutputPrinter.printPrefillSummary(
          initialState.request(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return ExitCodes.OK;
    }

    diagnostics.info("cli.tui.launch", of("searchDebounceMs", searchDebounceMs));
    TuiSessionSummary summary = runTui(initialState, searchDebounceMs, runtimeConfig, diagnostics);
    RuntimeWiring.saveLastRequest(runtimeConfig, summary.finalRequest());
    POST_TUI_ACTION_EXECUTOR.execute(summary, postGenerateHookCommand, diagnostics);
    return ExitCodes.OK;
  }

  public static int runWithArgs(String[] args) {
    return runWithArgs(args, RuntimeConfig.defaults());
  }

  static int runWithArgs(String[] args, RuntimeConfig runtimeConfig) {
    return new CommandLine(new QuarkusForgeCli(runtimeConfig)).execute(args);
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

  int runSmokeForTest(boolean verbose) throws Exception {
    return runSmoke(
        DiagnosticLogger.create(verbose),
        "smoke-test",
        (initialState, diagnostics) ->
            TuiBootstrapService.runHeadlessSmoke(runtimeConfig, diagnostics));
  }

  int runInteractiveSmokeForTest(boolean verbose) throws Exception {
    return runSmoke(
        DiagnosticLogger.create(verbose),
        "interactive-smoke",
        (initialState, diagnostics) ->
            TUI_BOOTSTRAP_SERVICE.runInteractiveSmoke(initialState, runtimeConfig, diagnostics));
  }

  /** Package-private for testing. */
  RequestOptions requestOptions() {
    return requestOptions;
  }

  /** Package-private for testing. */
  static void applyStoredRequestDefaults(RequestOptions requestOptions, CliPrefill storedPrefill) {
    if (storedPrefill == null) {
      return;
    }
    RequestOptions defaults = RequestOptions.defaults();
    requestOptions.groupId =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_GROUP_ID,
            requestOptions.groupId,
            defaults.groupId,
            storedPrefill.groupId());
    requestOptions.artifactId =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_ARTIFACT_ID,
            requestOptions.artifactId,
            defaults.artifactId,
            storedPrefill.artifactId());
    requestOptions.version =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_VERSION,
            requestOptions.version,
            defaults.version,
            storedPrefill.version());
    requestOptions.packageName =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_PACKAGE_NAME,
            requestOptions.packageName,
            defaults.packageName,
            storedPrefill.packageName());
    requestOptions.outputDirectory =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_OUTPUT_DIR,
            requestOptions.outputDirectory,
            defaults.outputDirectory,
            storedPrefill.outputDirectory());
    requestOptions.platformStream =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_PLATFORM_STREAM,
            requestOptions.platformStream,
            defaults.platformStream,
            storedPrefill.platformStream());
    requestOptions.buildTool =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_BUILD_TOOL,
            requestOptions.buildTool,
            defaults.buildTool,
            storedPrefill.buildTool());
    requestOptions.javaVersion =
        applyIfNotExplicit(
            requestOptions,
            RequestOptions.OPT_JAVA_VERSION,
            requestOptions.javaVersion,
            defaults.javaVersion,
            storedPrefill.javaVersion());
  }

  /**
   * Returns the stored value when the option was not explicitly provided on the command line. Uses
   * {@link RequestOptions#isExplicitlySet} to detect explicit CLI input, which avoids overriding an
   * intentional default-equal value with old prefill data.
   */
  private static String applyIfNotExplicit(
      RequestOptions requestOptions,
      String optionName,
      String current,
      String defaultValue,
      String stored) {
    if (!requestOptions.isExplicitlySet(optionName, current, defaultValue)
        && stored != null
        && !stored.isBlank()) {
      requestOptions.markPrefilled(optionName);
      return stored;
    }
    return current;
  }

  private StartupState loadStartupState(
      RequestOptions requestOptions, DiagnosticLogger diagnostics) {
    StartupMetadataLoader metadataLoader =
        new LiveStartupMetadataLoader(runtimeConfig.apiBaseUri(), apiClientFactory, diagnostics);
    return STARTUP_STATE_SERVICE.resolve(
        new StartupRequest(requestOptions.toCliPrefill(), null, metadataLoader));
  }

  @Override
  public int runHeadlessGenerate(GenerateCommand command) {
    try (HeadlessGenerationService service =
        RuntimeWiring.headlessGenerationService(runtimeConfig)) {
      return service.run(command, dryRun, verbose);
    }
  }

  private int runSmoke(DiagnosticLogger diagnostics, String mode, SmokeRunner smokeRunner)
      throws Exception {
    diagnostics.info("cli.start", of("mode", mode));

    StartupState startupState = loadStartupState(RequestOptions.defaults(), diagnostics);
    StartupMetadataSelection startupMetadataSelection = startupState.metadataSelection();
    ForgeUiState initialState = startupState.initialState();
    if (!initialState.canSubmit()) {
      diagnostics.error(
          "cli.validation.failed", of("errorCount", initialState.validation().errors().size()));
      HeadlessOutputPrinter.printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return ExitCodes.VALIDATION;
    }

    smokeRunner.run(initialState, diagnostics);
    return ExitCodes.OK;
  }

  @FunctionalInterface
  private interface SmokeRunner {
    void run(ForgeUiState initialState, DiagnosticLogger diagnostics) throws Exception;
  }
}
