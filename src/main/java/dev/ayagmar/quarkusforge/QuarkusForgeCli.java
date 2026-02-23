package dev.ayagmar.quarkusforge;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.ApiErrorMessages;
import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.ObjectMapperProvider;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.ArchiveException;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.ui.CoreTuiController;
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.ui.UiScheduler;
import dev.tamboui.tui.TuiRunner;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
import picocli.CommandLine.ParentCommand;

@Command(
    name = "quarkus-forge",
    version = "0.1.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    subcommands = {QuarkusForgeCli.GenerateCommand.class},
    description = "Quarkus forge terminal UI")
public final class QuarkusForgeCli implements Callable<Integer> {
  static final int EXIT_CODE_VALIDATION = 2;
  static final int EXIT_CODE_NETWORK = 3;
  static final int EXIT_CODE_ARCHIVE = 4;
  static final int EXIT_CODE_CANCELLED = 130;
  static final String NATIVE_ACCESS_FLAG = "--enable-native-access=ALL-UNNAMED";

  private static final String BACKEND_PROPERTY_NAME = "tamboui.backend";
  private static final String BACKEND_ENV_NAME = "TAMBOUI_BACKEND";
  private static final String PANAMA_BACKEND = "panama";
  private static final String JLINE_BACKEND = "jline3";

  private static final Map<String, List<String>> BUILTIN_PRESETS = builtInPresets();
  private static final String PRESET_FAVORITES = "favorites";
  private static final Duration STARTUP_METADATA_REFRESH_TIMEOUT = Duration.ofSeconds(2);

  @Mixin private RequestOptions requestOptions = new RequestOptions();

  private final RuntimeConfig runtimeConfig;

  @Option(
      names = "--dry-run",
      defaultValue = "false",
      description = "Validate CLI prefill and print summary without starting TUI")
  private boolean dryRun;

  @Option(
      names = "--smoke",
      defaultValue = "false",
      description = "Start the TUI and auto-exit after a short delay")
  private boolean smokeMode;

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

  public QuarkusForgeCli() {
    this(RuntimeConfig.defaults());
  }

  QuarkusForgeCli(RuntimeConfig runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
  }

  @Override
  public Integer call() throws Exception {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    diagnostics.info("cli.start", Map.of("mode", dryRun ? "dry-run" : "tui"));

    ProjectRequest request = toProjectRequest(requestOptions);
    StartupMetadataSelection startupMetadataSelection = loadStartupMetadataSelection(diagnostics);
    ProjectRequest requestWithResolvedStream =
        applyRecommendedPlatformStream(request, startupMetadataSelection.metadataCompatibility());
    ForgeUiState initialState =
        buildInitialState(
            requestWithResolvedStream, startupMetadataSelection.metadataCompatibility());
    if (!initialState.canSubmit()) {
      diagnostics.error(
          "cli.validation.failed", Map.of("errorCount", initialState.validation().errors().size()));
      printValidationErrors(
          initialState.validation(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return CommandLine.ExitCode.USAGE;
    }

    if (dryRun) {
      diagnostics.info(
          "cli.dry-run.validated",
          Map.of("metadataSource", startupMetadataSelection.sourceLabel()));
      printPrefillSummary(
          initialState.request(),
          startupMetadataSelection.sourceLabel(),
          startupMetadataSelection.detailMessage());
      return CommandLine.ExitCode.OK;
    }

    diagnostics.info("cli.tui.launch", Map.of("searchDebounceMs", searchDebounceMs));
    runTui(smokeMode, initialState, searchDebounceMs, runtimeConfig);
    return CommandLine.ExitCode.OK;
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

  static void runTui(
      boolean smokeMode,
      ForgeUiState initialState,
      int searchDebounceMs,
      RuntimeConfig runtimeConfig)
      throws Exception {
    configureTerminalBackendPreference();
    try (var tui = TuiRunner.create()) {
      QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
      CatalogDataService catalogDataService =
          new CatalogDataService(
              apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
      ProjectArchiveService projectArchiveService =
          new ProjectArchiveService(apiClient, new SafeZipExtractor());
      CoreTuiController controller =
          CoreTuiController.from(
              initialState,
              UiScheduler.fromScheduledExecutor(tui.scheduler(), tui::runOnRenderThread),
              Duration.ofMillis(Math.max(0, searchDebounceMs)),
              (generationRequest, outputDirectory, cancelled, progressListener) ->
                  projectArchiveService.downloadAndExtract(
                      generationRequest,
                      outputDirectory,
                      OverwritePolicy.FAIL_IF_EXISTS,
                      cancelled,
                      progress ->
                          progressListener.accept(
                              switch (progress) {
                                case DOWNLOADING_ARCHIVE -> "downloading project archive...";
                                case EXTRACTING_ARCHIVE -> "extracting project archive...";
                              })),
              ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile()),
              CoreTuiController.defaultFavoritesPersistenceExecutor());
      controller.loadExtensionCatalogAsync(
          () -> catalogDataService.load().thenApply(QuarkusForgeCli::toExtensionCatalogLoadResult));
      if (smokeMode) {
        tui.scheduler().schedule(tui::quit, 350, TimeUnit.MILLISECONDS);
      }

      tui.run(
          (event, runner) -> {
            CoreTuiController.UiAction action = controller.onEvent(event);
            if (action.shouldQuit()) {
              runner.quit();
            }
            return action.handled();
          },
          controller::render);
    }
  }

  private static CoreTuiController.ExtensionCatalogLoadResult toExtensionCatalogLoadResult(
      CatalogData catalogData) {
    return new CoreTuiController.ExtensionCatalogLoadResult(
        catalogData.extensions(),
        catalogData.source(),
        catalogData.stale(),
        catalogData.detailMessage(),
        catalogData.metadata());
  }

  private static void configureTerminalBackendPreference() {
    if (isBackendPreferenceExplicitlyConfigured()) {
      return;
    }
    String backendPreference =
        defaultBackendPreference(
            isNativeImageRuntime(), QuarkusForgeCli.class.getModule().isNativeAccessEnabled());
    System.setProperty(BACKEND_PROPERTY_NAME, backendPreference);
    if (JLINE_BACKEND.equals(backendPreference)) {
      System.err.println(
          "JVM started without "
              + NATIVE_ACCESS_FLAG
              + "; preferring jline backend (terminal-native warnings may still appear).");
    }
  }

  static String defaultBackendPreference(boolean nativeImageRuntime, boolean nativeAccessEnabled) {
    if (nativeImageRuntime || nativeAccessEnabled) {
      return PANAMA_BACKEND + "," + JLINE_BACKEND;
    }
    return JLINE_BACKEND;
  }

  private static boolean isBackendPreferenceExplicitlyConfigured() {
    String propertyValue = System.getProperty(BACKEND_PROPERTY_NAME);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return true;
    }
    String envValue = System.getenv(BACKEND_ENV_NAME);
    return envValue != null && !envValue.isBlank();
  }

  private static boolean isNativeImageRuntime() {
    return "runtime".equalsIgnoreCase(System.getProperty("org.graalvm.nativeimage.imagecode"));
  }

  private static ForgeUiState buildInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    ValidationReport fieldValidation = new ProjectRequestValidator().validate(request);
    ValidationReport compatibilityValidation = metadataCompatibility.validate(request);
    return new ForgeUiState(
        request, fieldValidation.merge(compatibilityValidation), metadataCompatibility);
  }

  private static ProjectRequest toProjectRequest(RequestOptions options) {
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

  private static ProjectRequest applyRecommendedPlatformStream(
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

  private static void printValidationErrors(ValidationReport validation) {
    printValidationErrors(validation, "", "");
  }

  private static void printValidationErrors(
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

  private static void printDryRunSummary(
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

  private static String userFriendlyError(Throwable throwable) {
    return ApiErrorMessages.userFriendlyMessage(throwable);
  }

  private static Throwable unwrapCompletionFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current instanceof ExecutionException || current instanceof CompletionException) {
      Throwable cause = current.getCause();
      if (cause == null) {
        break;
      }
      current = cause;
    }
    return current;
  }

  private static int mapHeadlessFailureToExitCode(Throwable throwable) {
    Throwable cause = unwrapCompletionFailure(throwable);
    if (cause instanceof CancellationException) {
      return EXIT_CODE_CANCELLED;
    }
    if (cause instanceof ArchiveException) {
      return EXIT_CODE_ARCHIVE;
    }
    if (cause instanceof ApiClientException) {
      return EXIT_CODE_NETWORK;
    }
    return EXIT_CODE_ARCHIVE;
  }

  private static Map<String, List<String>> builtInPresets() {
    Map<String, List<String>> presets = new LinkedHashMap<>();
    presets.put("web", List.of("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc"));
    presets.put(
        "data",
        List.of("io.quarkus:quarkus-hibernate-orm-panache", "io.quarkus:quarkus-jdbc-postgresql"));
    presets.put(
        "messaging", List.of("io.quarkus:quarkus-messaging", "io.quarkus:quarkus-smallrye-health"));
    return Collections.unmodifiableMap(presets);
  }

  private static String normalizePresetName(String presetName) {
    if (presetName == null) {
      return "";
    }
    return presetName.trim().toLowerCase(Locale.ROOT);
  }

  private CatalogData loadCatalogData() throws ExecutionException, InterruptedException {
    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    CatalogDataService catalogDataService =
        new CatalogDataService(
            apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
    return catalogDataService.load().get();
  }

  private List<String> resolveRequestedExtensions(
      List<String> extensionInputs, List<String> presetInputs, Set<String> knownExtensionIds) {
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
      List<String> presetExtensions = BUILTIN_PRESETS.get(preset);
      if (presetExtensions == null) {
        errors.add(
            new ValidationError(
                "preset",
                "unknown preset '"
                    + presetInput
                    + "'. Allowed: "
                    + String.join(", ", BUILTIN_PRESETS.keySet())
                    + ", "
                    + PRESET_FAVORITES));
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
      diagnostics.info("metadata.load.success", Map.of("source", "live"));
      return new StartupMetadataSelection(
          MetadataCompatibilityContext.success(metadata), "live", "");
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      StartupMetadataSelection selection =
          snapshotFallbackSelection("Live metadata refresh interrupted");
      diagnostics.error(
          "metadata.load.fallback",
          Map.of("source", selection.sourceLabel(), "detail", selection.detailMessage()));
      return selection;
    } catch (TimeoutException timeoutException) {
      StartupMetadataSelection selection =
          snapshotFallbackSelection(
              "Live metadata refresh timed out after "
                  + STARTUP_METADATA_REFRESH_TIMEOUT.toMillis()
                  + "ms");
      diagnostics.error(
          "metadata.load.fallback",
          Map.of("source", selection.sourceLabel(), "detail", selection.detailMessage()));
      return selection;
    } catch (ExecutionException executionException) {
      Throwable cause = unwrapCompletionFailure(executionException);
      StartupMetadataSelection selection =
          snapshotFallbackSelection(
              "Live metadata unavailable (%s)".formatted(userFriendlyError(cause)));
      diagnostics.error(
          "metadata.load.fallback",
          Map.of(
              "source", selection.sourceLabel(),
              "detail", selection.detailMessage(),
              "causeType", cause.getClass().getSimpleName()));
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

  private Integer runHeadlessGenerate(GenerateCommand command) {
    DiagnosticLogger diagnostics = DiagnosticLogger.create(verbose);
    diagnostics.info(
        "generate.start", Map.of("mode", command.dryRun || dryRun ? "dry-run" : "apply"));

    CatalogData catalogData;
    try {
      catalogData = loadCatalogData();
      diagnostics.info(
          "catalog.load.success",
          Map.of(
              "source", catalogData.source().label(),
              "stale", catalogData.stale(),
              "detail", catalogData.detailMessage()));
    } catch (CancellationException cancellationException) {
      diagnostics.error("catalog.load.cancelled", Map.of("phase", "before-start"));
      System.err.println("Generation cancelled before start.");
      return EXIT_CODE_CANCELLED;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      diagnostics.error("catalog.load.cancelled", Map.of("phase", "interrupted"));
      System.err.println("Generation cancelled before start.");
      return EXIT_CODE_CANCELLED;
    } catch (ExecutionException executionException) {
      Throwable cause = unwrapCompletionFailure(executionException);
      diagnostics.error(
          "catalog.load.failure",
          Map.of(
              "causeType", cause.getClass().getSimpleName(), "message", userFriendlyError(cause)));
      System.err.println("Failed to load extension catalog: " + userFriendlyError(cause));
      return mapHeadlessFailureToExitCode(cause);
    }

    ProjectRequest request = toProjectRequest(command.requestOptions);
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(catalogData.metadata());
    ProjectRequest requestWithResolvedStream =
        applyRecommendedPlatformStream(request, metadataCompatibility);
    ForgeUiState validatedState =
        buildInitialState(requestWithResolvedStream, metadataCompatibility);
    if (!validatedState.canSubmit()) {
      diagnostics.error(
          "generate.validation.failed",
          Map.of(
              "errorCount", validatedState.validation().errors().size(),
              "catalogSource", catalogData.source().label()));
      printValidationErrors(
          validatedState.validation(),
          catalogData.source().label() + (catalogData.stale() ? " [stale]" : ""),
          catalogData.detailMessage());
      return EXIT_CODE_VALIDATION;
    }

    Set<String> knownExtensionIds = new LinkedHashSet<>();
    for (ExtensionDto extension : catalogData.extensions()) {
      knownExtensionIds.add(extension.id());
    }

    List<String> extensionIds;
    try {
      extensionIds =
          resolveRequestedExtensions(command.extensions, command.presets, knownExtensionIds);
    } catch (ValidationException validationException) {
      diagnostics.error(
          "generate.extension-validation.failed",
          Map.of("errorCount", validationException.errors().size()));
      printValidationErrors(
          new ValidationReport(validationException.errors()),
          catalogData.source().label() + (catalogData.stale() ? " [stale]" : ""),
          catalogData.detailMessage());
      return EXIT_CODE_VALIDATION;
    }

    boolean dryRunRequested = command.dryRun || dryRun;
    if (dryRunRequested) {
      diagnostics.info(
          "generate.dry-run.validated",
          Map.of(
              "extensionCount", extensionIds.size(),
              "catalogSource", catalogData.source().label(),
              "stale", catalogData.stale()));
      printDryRunSummary(
          validatedState.request(),
          extensionIds,
          catalogData.source().label(),
          catalogData.stale());
      return CommandLine.ExitCode.OK;
    }

    Path outputPath =
        Path.of(validatedState.request().outputDirectory())
            .resolve(validatedState.request().artifactId())
            .normalize();
    GenerationRequest generationRequest =
        new GenerationRequest(
            validatedState.request().groupId(),
            validatedState.request().artifactId(),
            validatedState.request().version(),
            validatedState.request().platformStream(),
            validatedState.request().buildTool(),
            validatedState.request().javaVersion(),
            extensionIds);

    QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri());
    ProjectArchiveService archiveService =
        new ProjectArchiveService(apiClient, new SafeZipExtractor());
    try {
      diagnostics.info(
          "generate.execute.start",
          Map.of("outputPath", outputPath.toString(), "extensionCount", extensionIds.size()));
      Path generatedProjectRoot =
          archiveService
              .downloadAndExtract(
                  generationRequest,
                  outputPath,
                  OverwritePolicy.FAIL_IF_EXISTS,
                  () -> Thread.currentThread().isInterrupted(),
                  progress ->
                      System.out.println(
                          switch (progress) {
                            case DOWNLOADING_ARCHIVE -> "downloading project archive...";
                            case EXTRACTING_ARCHIVE -> "extracting project archive...";
                          }))
              .get();
      diagnostics.info(
          "generate.execute.success", Map.of("projectRoot", generatedProjectRoot.toString()));
      System.out.println(
          "Generation succeeded: " + generatedProjectRoot.toAbsolutePath().normalize());
      return CommandLine.ExitCode.OK;
    } catch (CancellationException cancellationException) {
      diagnostics.error("generate.execute.cancelled", Map.of("phase", "execution"));
      System.err.println("Generation cancelled.");
      return EXIT_CODE_CANCELLED;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      diagnostics.error("generate.execute.cancelled", Map.of("phase", "interrupted"));
      System.err.println("Generation cancelled.");
      return EXIT_CODE_CANCELLED;
    } catch (ExecutionException executionException) {
      Throwable cause = unwrapCompletionFailure(executionException);
      int exitCode = mapHeadlessFailureToExitCode(cause);
      diagnostics.error(
          "generate.execute.failure",
          Map.of(
              "causeType", cause.getClass().getSimpleName(),
              "message", userFriendlyError(cause),
              "exitCode", exitCode));
      System.err.println("Generation failed: " + userFriendlyError(cause));
      return exitCode;
    }
  }

  static final class RequestOptions {
    @Option(
        names = {"-g", "--group-id"},
        defaultValue = "org.acme",
        description = "Maven group id")
    private String groupId;

    @Option(
        names = {"-a", "--artifact-id"},
        defaultValue = "quarkus-app",
        description = "Maven artifact id")
    private String artifactId;

    @Option(
        names = {"-v", "--project-version"},
        defaultValue = "1.0.0-SNAPSHOT",
        description = "Project version")
    private String version;

    @Option(
        names = {"-p", "--package-name"},
        description = "Base package name (defaults from group/artifact)")
    private String packageName;

    @Option(
        names = {"-o", "--output-dir"},
        defaultValue = ".",
        description =
            "Output parent directory (project path resolves to <output-dir>/<artifact-id>)")
    private String outputDirectory;

    @Option(
        names = {"-S", "--platform-stream"},
        defaultValue = "",
        description = "Quarkus platform stream key (metadata-driven, optional)")
    private String platformStream;

    @Option(
        names = {"-b", "--build-tool"},
        defaultValue = "maven",
        description = "Build tool (metadata-driven)")
    private String buildTool;

    @Option(
        names = {"-j", "--java-version"},
        defaultValue = "25",
        description = "Java version for generated project (metadata-driven)")
    private String javaVersion;
  }

  @Command(name = "generate", description = "Generate a Quarkus project without starting the TUI")
  static final class GenerateCommand implements Callable<Integer> {
    @ParentCommand private QuarkusForgeCli rootCommand;

    @Mixin private RequestOptions requestOptions = new RequestOptions();

    @Option(
        names = {"-e", "--extension"},
        split = ",",
        description = "Extension id to include (repeatable and comma-separated)")
    private List<String> extensions = new ArrayList<>();

    @Option(
        names = "--preset",
        split = ",",
        description = "Extension preset(s): web, data, messaging, favorites")
    private List<String> presets = new ArrayList<>();

    @Option(
        names = "--dry-run",
        defaultValue = "false",
        description = "Validate full generation request without writing files")
    private boolean dryRun;

    @Override
    public Integer call() {
      return rootCommand.runHeadlessGenerate(this);
    }
  }

  record RuntimeConfig(URI apiBaseUri, Path catalogCacheFile, Path favoritesFile) {
    RuntimeConfig {
      Objects.requireNonNull(apiBaseUri);
      Objects.requireNonNull(catalogCacheFile);
      Objects.requireNonNull(favoritesFile);
    }

    static RuntimeConfig defaults() {
      return new RuntimeConfig(
          URI.create("https://code.quarkus.io"),
          CatalogSnapshotCache.defaultCacheFile(),
          ExtensionFavoritesStore.defaultFile());
    }
  }

  private static final class ValidationException extends RuntimeException {
    private final List<ValidationError> errors;

    private ValidationException(List<ValidationError> errors) {
      this.errors = List.copyOf(errors);
    }

    private List<ValidationError> errors() {
      return errors;
    }
  }

  private record StartupMetadataSelection(
      MetadataCompatibilityContext metadataCompatibility,
      String sourceLabel,
      String detailMessage) {
    private StartupMetadataSelection {
      Objects.requireNonNull(metadataCompatibility);
      sourceLabel = sourceLabel == null ? "" : sourceLabel.strip();
      detailMessage = detailMessage == null ? "" : detailMessage.strip();
    }
  }

  private static final class DiagnosticLogger {
    private final boolean enabled;
    private final String traceId;

    private DiagnosticLogger(boolean enabled, String traceId) {
      this.enabled = enabled;
      this.traceId = traceId;
    }

    static DiagnosticLogger create(boolean enabled) {
      if (!enabled) {
        return new DiagnosticLogger(false, "");
      }
      return new DiagnosticLogger(true, UUID.randomUUID().toString());
    }

    private void info(String event, Map<String, Object> fields) {
      log("INFO", event, fields);
    }

    private void error(String event, Map<String, Object> fields) {
      log("ERROR", event, fields);
    }

    private void log(String level, String event, Map<String, Object> fields) {
      if (!enabled) {
        return;
      }
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("ts", Instant.now().toString());
      payload.put("level", level);
      payload.put("event", event);
      payload.put("traceId", traceId);
      payload.putAll(fields);
      try {
        System.err.println(ObjectMapperProvider.shared().writeValueAsString(payload));
      } catch (JsonProcessingException jsonProcessingException) {
        System.err.println(
            "{\"event\":\"diagnostic.encoding.failure\",\"traceId\":\""
                + traceId
                + "\",\"message\":\""
                + jsonProcessingException.getMessage().replace("\"", "'")
                + "\"}");
      }
    }
  }
}
