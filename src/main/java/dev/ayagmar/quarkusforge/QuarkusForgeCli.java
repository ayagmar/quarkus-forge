package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.archive.ArchiveException;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.ui.UserPreferencesStore;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Bindings;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(
    name = "quarkus-forge",
    versionProvider = CliVersionProvider.class,
    subcommands = {GenerateCommand.class},
    description = "Quarkus forge terminal UI")
public final class QuarkusForgeCli implements Callable<Integer> {
  static final int EXIT_CODE_VALIDATION = 2;
  static final int EXIT_CODE_NETWORK = 3;
  static final int EXIT_CODE_ARCHIVE = 4;
  static final int EXIT_CODE_CANCELLED = 130;
  private static final String BACKEND_PROPERTY_NAME = "tamboui.backend";
  private static final String BACKEND_ENV_NAME = "TAMBOUI_BACKEND";
  private static final String PANAMA_BACKEND = "panama";

  private static final String PRESET_FAVORITES = "favorites";
  private static final Duration STARTUP_METADATA_REFRESH_TIMEOUT = Duration.ofSeconds(2);
  static final Duration STARTUP_SPLASH_MIN_DURATION = Duration.ofMillis(450);
  private static final Duration TUI_TICK_RATE = Duration.ofMillis(40);
  private static final Duration DEFAULT_HEADLESS_CATALOG_TIMEOUT = Duration.ofSeconds(20);
  private static final Duration DEFAULT_HEADLESS_GENERATION_TIMEOUT = Duration.ofMinutes(2);
  private static final String HEADLESS_CATALOG_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.catalog-timeout-ms";
  private static final String HEADLESS_GENERATION_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.generation-timeout-ms";
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
    this.headlessGenerationService = new HeadlessGenerationService();
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
    ProjectRequest request = toProjectRequest(requestOptions);
    StartupMetadataSelection startupMetadataSelection = loadStartupMetadataSelection(diagnostics);
    ProjectRequest requestWithResolvedStream =
        applyRecommendedPlatformStream(request, startupMetadataSelection.metadataCompatibility());
    ForgeUiState initialState =
        buildInitialState(
            requestWithResolvedStream, startupMetadataSelection.metadataCompatibility());
    if (!initialState.canSubmit() && shouldBlockOnStartupValidation(dryRun)) {
      diagnostics.error(
          "cli.validation.failed", df("errorCount", initialState.validation().errors().size()));
      printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return CommandLine.ExitCode.USAGE;
    }

    if (dryRun) {
      diagnostics.info(
          "cli.dry-run.validated", df("metadataSource", startupMetadataSelection.sourceLabel()));
      printPrefillSummary(
          initialState.request(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return CommandLine.ExitCode.OK;
    }

    diagnostics.info("cli.tui.launch", df("searchDebounceMs", searchDebounceMs));
    TuiSessionSummary summary = runTui(initialState, searchDebounceMs, runtimeConfig, diagnostics);
    preferencesStore.saveLastRequest(summary.finalRequest());
    POST_TUI_ACTION_EXECUTOR.execute(summary, postGenerateHookCommand, diagnostics);
    return CommandLine.ExitCode.OK;
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

    ProjectRequest request = toProjectRequest(defaultRequestOptions());
    StartupMetadataSelection startupMetadataSelection = loadStartupMetadataSelection(diagnostics);
    ProjectRequest requestWithResolvedStream =
        applyRecommendedPlatformStream(request, startupMetadataSelection.metadataCompatibility());
    ForgeUiState initialState =
        buildInitialState(
            requestWithResolvedStream, startupMetadataSelection.metadataCompatibility());
    if (!initialState.canSubmit()) {
      diagnostics.error(
          "cli.validation.failed", df("errorCount", initialState.validation().errors().size()));
      printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return CommandLine.ExitCode.USAGE;
    }

    runHeadlessSmoke(initialState, 0, runtimeConfig, diagnostics);
    return CommandLine.ExitCode.OK;
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
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    CatalogDataService catalogDataService =
        new CatalogDataService(
            apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
    diagnostics.info("catalog.load.start", df("mode", "tui"));
    catalogDataService.load().handle(QuarkusForgeCli.catalogLoadDiagnostics(diagnostics)).join();
    diagnostics.info("tui.session.exit", df("outcome", "completed"));
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

  static void configureTerminalBackendPreference() {
    if (isBackendPreferenceExplicitlyConfigured()) {
      return;
    }
    System.setProperty(BACKEND_PROPERTY_NAME, defaultBackendPreference());
  }

  static Bindings appBindingsProfile() {
    return AppBindingsProfile.bindings();
  }

  static TuiConfig appTuiConfig() {
    return TuiConfig.builder().tickRate(TUI_TICK_RATE).bindings(appBindingsProfile()).build();
  }

  static String defaultBackendPreference() {
    return PANAMA_BACKEND;
  }

  private static boolean isBackendPreferenceExplicitlyConfigured() {
    String propertyValue = System.getProperty(BACKEND_PROPERTY_NAME);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return true;
    }
    String envValue = System.getenv(BACKEND_ENV_NAME);
    return envValue != null && !envValue.isBlank();
  }

  static ForgeUiState buildInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    ValidationReport fieldValidation = new ProjectRequestValidator().validate(request);
    ValidationReport compatibilityValidation = metadataCompatibility.validate(request);
    return new ForgeUiState(
        request, fieldValidation.merge(compatibilityValidation), metadataCompatibility);
  }

  static ProjectRequest toProjectRequest(RequestOptions options) {
    CliPrefill prefill =
        new CliPrefill(
            options.groupId,
            options.artifactId,
            options.version,
            options.packageName,
            options.outputDirectory,
            options.platformStream,
            options.buildTool,
            options.javaVersion);
    return CliPrefillMapper.map(prefill);
  }

  static ProjectRequest applyRecommendedPlatformStream(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibilityContext) {
    if (!request.platformStream().isBlank()) {
      return request;
    }
    if (metadataCompatibilityContext.loadError() != null) {
      return request;
    }
    MetadataDto metadata = metadataCompatibilityContext.metadataSnapshot();
    if (metadata == null) {
      return request;
    }
    String recommendedStream = metadata.recommendedPlatformStreamKey();
    if (recommendedStream.isBlank()) {
      return request;
    }
    return new ProjectRequest(
        request.groupId(),
        request.artifactId(),
        request.version(),
        request.packageName(),
        request.outputDirectory(),
        recommendedStream,
        request.buildTool(),
        request.javaVersion());
  }

  static void printValidationErrors(
      ValidationReport validation, String sourceLabel, String sourceDetail) {
    System.err.println("Input validation failed:");
    if (sourceLabel != null && !sourceLabel.isBlank()) {
      System.err.println(" - metadataSource: " + sourceLabel);
    }
    if (sourceDetail != null && !sourceDetail.isBlank()) {
      System.err.println(" - metadataDetail: " + sourceDetail);
    }
    for (var error : validation.errors()) {
      System.err.println(" - " + error.field() + ": " + error.message());
    }
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

  static void printDryRunSummary(
      ProjectRequest request, List<String> extensionIds, String sourceLabel, boolean stale) {
    Path generatedProjectDirectory =
        Path.of(request.outputDirectory()).resolve(request.artifactId()).normalize();
    System.out.println("Dry-run validated successfully:");
    System.out.println(" - groupId: " + request.groupId());
    System.out.println(" - artifactId: " + request.artifactId());
    System.out.println(" - version: " + request.version());
    System.out.println(" - packageName: " + request.packageName());
    System.out.println(" - outputDirectory: " + request.outputDirectory());
    System.out.println(" - platformStream: " + request.platformStream());
    System.out.println(" - buildTool: " + request.buildTool());
    System.out.println(" - javaVersion: " + request.javaVersion());
    System.out.println(" - extensions: " + extensionIds);
    System.out.println(" - catalogSource: " + sourceLabel + (stale ? " [stale]" : ""));
    System.out.println(" - generatedProjectDirectory: " + generatedProjectDirectory);
  }

  static int mapHeadlessFailureToExitCode(Throwable throwable) {
    Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(throwable);
    return switch (cause) {
      case CancellationException ignored -> EXIT_CODE_CANCELLED;
      case ApiClientException ignored -> EXIT_CODE_NETWORK;
      case ArchiveException ignored -> EXIT_CODE_ARCHIVE;
      default -> EXIT_CODE_ARCHIVE;
    };
  }

  static String normalizePresetName(String presetName) {
    if (presetName == null) {
      return "";
    }
    return presetName.trim().toLowerCase(Locale.ROOT);
  }

  CatalogData loadCatalogData() throws ExecutionException, InterruptedException, TimeoutException {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    CatalogDataService catalogDataService =
        new CatalogDataService(
            apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
    Duration timeout = headlessCatalogTimeout();
    CompletableFuture<CatalogData> loadFuture = catalogDataService.load();
    try {
      return loadFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeoutException) {
      loadFuture.cancel(true);
      throw new TimeoutException("catalog load timed out after " + timeout.toMillis() + "ms");
    }
  }

  Map<String, List<String>> loadBuiltInPresets(String platformStream)
      throws ExecutionException, InterruptedException, TimeoutException {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    CompletableFuture<Map<String, List<String>>> presetsFuture =
        apiClient.fetchPresets(platformStream);
    Duration timeout = headlessCatalogTimeout();
    try {
      return presetsFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException timeoutException) {
      presetsFuture.cancel(true);
      throw new TimeoutException("preset load timed out after " + timeout.toMillis() + "ms");
    }
  }

  List<String> resolveRequestedExtensions(
      List<String> extensionInputs,
      List<String> presetInputs,
      Set<String> knownExtensionIds,
      Map<String, List<String>> presetExtensionsByName) {
    List<ValidationError> errors = new ArrayList<>();
    LinkedHashSet<String> resolved = new LinkedHashSet<>();

    for (String presetInput : presetInputs) {
      String preset = normalizePresetName(presetInput);
      if (preset.isBlank()) {
        continue;
      }
      if (PRESET_FAVORITES.equals(preset)) {
        resolved.addAll(
            ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile())
                .loadFavoriteExtensionIds());
        continue;
      }
      List<String> presetExtensions = presetExtensionsByName.get(preset);
      if (presetExtensions == null) {
        String allowedPresets = String.join(", ", presetExtensionsByName.keySet());
        if (!allowedPresets.isBlank()) {
          allowedPresets = allowedPresets + ", " + PRESET_FAVORITES;
        } else {
          allowedPresets = PRESET_FAVORITES;
        }
        errors.add(
            new ValidationError(
                "preset", "unknown preset '" + presetInput + "'. Allowed: " + allowedPresets));
        continue;
      }
      resolved.addAll(presetExtensions);
    }

    for (String extensionInput : extensionInputs) {
      if (extensionInput == null || extensionInput.isBlank()) {
        errors.add(new ValidationError("extension", "must not be blank"));
        continue;
      }
      resolved.add(extensionInput.trim());
    }

    for (String extensionId : resolved) {
      if (!knownExtensionIds.contains(extensionId)) {
        errors.add(new ValidationError("extension", "unknown extension id '" + extensionId + "'"));
      }
    }

    if (!errors.isEmpty()) {
      throw new ValidationException(errors);
    }
    return List.copyOf(resolved);
  }

  private StartupMetadataSelection loadStartupMetadataSelection(DiagnosticLogger diagnostics) {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    try {
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

  Integer runHeadlessGenerate(GenerateCommand command) {
    return headlessGenerationService.run(command, dryRun, verbose, new CliHeadlessOperations(this));
  }

  CompletableFuture<Path> startHeadlessGeneration(
      GenerationRequest generationRequest, Path outputPath, Consumer<String> progressLineConsumer) {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    ProjectArchiveService archiveService =
        new ProjectArchiveService(apiClient, new SafeZipExtractor());
    return archiveService.downloadAndExtract(
        generationRequest,
        outputPath,
        OverwritePolicy.FAIL_IF_EXISTS,
        () -> Thread.currentThread().isInterrupted(),
        progress ->
            progressLineConsumer.accept(
                switch (progress) {
                  case REQUESTING_ARCHIVE -> "requesting project archive from Quarkus API...";
                  case EXTRACTING_ARCHIVE -> "extracting project archive...";
                }));
  }

  static Duration headlessCatalogTimeout() {
    return durationFromProperty(
        HEADLESS_CATALOG_TIMEOUT_PROPERTY, DEFAULT_HEADLESS_CATALOG_TIMEOUT);
  }

  static Duration headlessGenerationTimeout() {
    return durationFromProperty(
        HEADLESS_GENERATION_TIMEOUT_PROPERTY, DEFAULT_HEADLESS_GENERATION_TIMEOUT);
  }

  private static Duration durationFromProperty(String propertyName, Duration fallback) {
    String rawValue = System.getProperty(propertyName);
    if (rawValue == null || rawValue.isBlank()) {
      return fallback;
    }
    try {
      long millis = Long.parseLong(rawValue.trim());
      if (millis <= 0) {
        return fallback;
      }
      return Duration.ofMillis(millis);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static DiagnosticField df(String name, Object value) {
    return DiagnosticField.of(name, value);
  }
}
