package dev.ayagmar.quarkusforge.runtime;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.postgen.IdeDetector;
import dev.ayagmar.quarkusforge.ui.CoreTuiController;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import dev.ayagmar.quarkusforge.ui.GenerationProgressUpdate;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import dev.ayagmar.quarkusforge.ui.ProjectGenerationRunner;
import dev.ayagmar.quarkusforge.ui.UiAction;
import dev.ayagmar.quarkusforge.ui.UiScheduler;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.bindings.Bindings;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public final class TuiBootstrapService {
  private static final String BACKEND_PROPERTY_NAME = "tamboui.backend";
  private static final String BACKEND_ENV_NAME = "TAMBOUI_BACKEND";
  private static final String PANAMA_BACKEND = "panama";
  private static final ReentrantLock BACKEND_PROPERTY_LOCK = new ReentrantLock();
  public static final Duration STARTUP_SPLASH_MIN_DURATION = Duration.ofMillis(450);
  private static final Duration TUI_TICK_RATE = Duration.ofMillis(40);

  interface CatalogLoader {
    CompletableFuture<CatalogData> load();

    CompletableFuture<CatalogData> loadForStartup();
  }

  @FunctionalInterface
  interface PresetLoader {
    CompletableFuture<Map<String, List<String>>> load(String streamKey);
  }

  @FunctionalInterface
  interface TuiSessionLoop {
    void run(CoreTuiController controller, DiagnosticLogger diagnostics) throws Exception;
  }

  public static Bindings appBindingsProfile() {
    return AppBindingsProfile.bindings();
  }

  public static TuiConfig appTuiConfig() {
    return TuiConfig.builder().tickRate(TUI_TICK_RATE).bindings(appBindingsProfile()).build();
  }

  public static String defaultBackendPreference() {
    return PANAMA_BACKEND;
  }

  private static void configureTerminalBackendPreference() {
    configureTerminalBackendPreference(
        System.getProperty(BACKEND_PROPERTY_NAME), System.getenv(BACKEND_ENV_NAME));
  }

  private static void configureTerminalBackendPreference(String propertyValue, String envValue) {
    if (isBackendPreferenceExplicitlyConfigured(propertyValue, envValue)) {
      return;
    }
    System.setProperty(BACKEND_PROPERTY_NAME, defaultBackendPreference());
  }

  private static boolean isBackendPreferenceExplicitlyConfigured(
      String propertyValue, String envValue) {
    if (propertyValue != null && !propertyValue.isBlank()) {
      return true;
    }
    return envValue != null && !envValue.isBlank();
  }

  public static void runHeadlessSmoke(RuntimeConfig runtimeConfig, DiagnosticLogger diagnostics) {
    diagnostics.info("tui.session.start", of("smokeMode", true), of("mode", "headless-smoke"));
    try (QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
      CatalogDataService catalogDataService =
          RuntimeWiring.catalogDataService(apiClient, runtimeConfig);
      diagnostics.info("catalog.load.start", of("mode", "headless-smoke"));
      catalogDataService
          .load()
          .handle(CatalogLoadDiagnostics.catalogLoadDiagnostics(diagnostics, "headless-smoke"))
          .join();
      diagnostics.info("tui.session.exit", of("outcome", "completed"));
    }
  }

  public TuiSessionSummary run(
      ForgeUiState initialState,
      int searchDebounceMs,
      RuntimeConfig runtimeConfig,
      DiagnosticLogger diagnostics)
      throws Exception {
    diagnostics.info(
        "tui.session.start",
        of("smokeMode", false),
        of("searchDebounceMs", Math.max(0, searchDebounceMs)));
    BACKEND_PROPERTY_LOCK.lock();
    String previousBackendPreference = System.getProperty(BACKEND_PROPERTY_NAME);
    try {
      configureTerminalBackendPreference();
      TuiConfig tuiConfig = appTuiConfig();
      try (var tui = TuiRunner.create(tuiConfig);
          QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
        CatalogDataService catalogDataService =
            RuntimeWiring.catalogDataService(apiClient, runtimeConfig);
        ProjectArchiveService projectArchiveService =
            RuntimeWiring.projectArchiveService(apiClient);
        return runSession(
            initialState,
            searchDebounceMs,
            diagnostics,
            new CatalogLoader() {
              @Override
              public CompletableFuture<CatalogData> load() {
                return catalogDataService.load();
              }

              @Override
              public CompletableFuture<CatalogData> loadForStartup() {
                return catalogDataService.loadForStartup();
              }
            },
            apiClient::fetchPresets,
            (generationRequest, outputDirectory, cancelled, progressListener) ->
                projectArchiveService.downloadAndExtract(
                    generationRequest,
                    outputDirectory,
                    OverwritePolicy.FAIL_IF_EXISTS,
                    cancelled,
                    progress ->
                        progressListener.accept(
                            switch (progress) {
                              case REQUESTING_ARCHIVE ->
                                  GenerationProgressUpdate.requestingArchive(
                                      "requesting project archive from Quarkus API...");
                              case EXTRACTING_ARCHIVE ->
                                  GenerationProgressUpdate.extractingArchive(
                                      "extracting project archive...");
                            })),
            RuntimeWiring.favoritesStore(runtimeConfig),
            UiScheduler.fromScheduledExecutor(tui.scheduler(), tui::runOnRenderThread),
            IdeDetector.detect(),
            (controller, sessionDiagnostics) ->
                tui.run(
                    (event, runner) -> {
                      UiAction action = controller.onEvent(event);
                      if (action.shouldQuit()) {
                        sessionDiagnostics.info("tui.session.quit.requested", of("reason", "user"));
                        runner.quit();
                      }
                      return action.handled();
                    },
                    controller::render));
      }
    } finally {
      try {
        restoreTerminalBackendPreference(previousBackendPreference);
      } finally {
        BACKEND_PROPERTY_LOCK.unlock();
      }
    }
  }

  TuiSessionSummary runSession(
      ForgeUiState initialState,
      int searchDebounceMs,
      DiagnosticLogger diagnostics,
      CatalogLoader catalogLoader,
      PresetLoader presetLoader,
      ProjectGenerationRunner projectGenerationRunner,
      ExtensionFavoritesStore favoritesStore,
      UiScheduler scheduler,
      List<IdeDetector.DetectedIde> detectedIdes,
      TuiSessionLoop sessionLoop)
      throws Exception {
    AtomicBoolean firstCatalogLoad = new AtomicBoolean(true);
    CoreTuiController controller =
        CoreTuiController.from(
            initialState,
            scheduler,
            Duration.ofMillis(Math.max(0, searchDebounceMs)),
            projectGenerationRunner,
            favoritesStore,
            CoreTuiController.defaultFavoritesPersistenceExecutor(),
            detectedIdes);
    controller.setStartupOverlayMinDuration(STARTUP_SPLASH_MIN_DURATION);
    controller.loadExtensionCatalogAsync(
        () -> {
          diagnostics.info("catalog.load.start", of("mode", "tui"));
          String presetStreamKey = controller.request().platformStream();
          CompletableFuture<CatalogData> catalogLoadFuture =
              firstCatalogLoad.getAndSet(false)
                  ? catalogLoader.loadForStartup()
                  : catalogLoader.load();
          return catalogLoadFuture
              .handle(CatalogLoadDiagnostics.catalogLoadDiagnostics(diagnostics))
              .thenCompose(
                  loadResult ->
                      presetLoader
                          .load(presetStreamKey)
                          .handle(
                              (presets, throwable) -> {
                                if (throwable != null) {
                                  return CatalogLoadDiagnostics.handlePresetLoadFailure(
                                      diagnostics, throwable, "tui");
                                }
                                diagnostics.info(
                                    "preset.load.success",
                                    of("mode", "tui"),
                                    of("presetCount", presets.size()));
                                return presets;
                              })
                          .thenApply(
                              presets ->
                                  new ExtensionCatalogLoadResult(
                                      loadResult.extensions(),
                                      loadResult.source(),
                                      loadResult.stale(),
                                      loadResult.detailMessage(),
                                      loadResult.metadata(),
                                      presets)));
        });
    try {
      sessionLoop.run(controller, diagnostics);
      diagnostics.info("tui.session.exit", of("outcome", "completed"));
      PostGenerationExitPlan exitPlan = controller.postGenerationExitPlan().orElse(null);
      return new TuiSessionSummary(controller.request(), exitPlan);
    } catch (Exception exception) {
      diagnostics.error(
          "tui.session.failure",
          of("causeType", exception.getClass().getSimpleName()),
          of("message", ErrorMessageMapper.userFriendlyError(exception)));
      throw exception;
    }
  }

  private static void restoreTerminalBackendPreference(String previousBackendPreference) {
    if (previousBackendPreference == null) {
      System.clearProperty(BACKEND_PROPERTY_NAME);
      return;
    }
    System.setProperty(BACKEND_PROPERTY_NAME, previousBackendPreference);
  }
}
