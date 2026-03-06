package dev.ayagmar.quarkusforge.runtime;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.IdeDetector;
import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.ui.CoreTuiController;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import dev.ayagmar.quarkusforge.ui.GenerationProgressUpdate;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
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

public final class TuiBootstrapService {
  private static final String BACKEND_PROPERTY_NAME = "tamboui.backend";
  private static final String BACKEND_ENV_NAME = "TAMBOUI_BACKEND";
  private static final String PANAMA_BACKEND = "panama";
  public static final Duration STARTUP_SPLASH_MIN_DURATION = Duration.ofMillis(450);
  private static final Duration TUI_TICK_RATE = Duration.ofMillis(40);

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
    if (isBackendPreferenceExplicitlyConfigured()) {
      return;
    }
    System.setProperty(BACKEND_PROPERTY_NAME, defaultBackendPreference());
  }

  private static boolean isBackendPreferenceExplicitlyConfigured() {
    String propertyValue = System.getProperty(BACKEND_PROPERTY_NAME);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return true;
    }
    String envValue = System.getenv(BACKEND_ENV_NAME);
    return envValue != null && !envValue.isBlank();
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
    configureTerminalBackendPreference();
    TuiConfig tuiConfig = appTuiConfig();
    try (var tui = TuiRunner.create(tuiConfig);
        QuarkusApiClient apiClient = new QuarkusApiClient(runtimeConfig.apiBaseUri())) {
      CatalogDataService catalogDataService =
          new CatalogDataService(
              apiClient, new CatalogSnapshotCache(runtimeConfig.catalogCacheFile()));
      AtomicBoolean firstCatalogLoad = new AtomicBoolean(true);
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
                                case REQUESTING_ARCHIVE ->
                                    GenerationProgressUpdate.requestingArchive(
                                        "requesting project archive from Quarkus API...");
                                case EXTRACTING_ARCHIVE ->
                                    GenerationProgressUpdate.extractingArchive(
                                        "extracting project archive...");
                              })),
              ExtensionFavoritesStore.fileBacked(runtimeConfig.favoritesFile()),
              CoreTuiController.defaultFavoritesPersistenceExecutor(),
              IdeDetector.detect());
      controller.setStartupOverlayMinDuration(STARTUP_SPLASH_MIN_DURATION);
      controller.loadExtensionCatalogAsync(
          () -> {
            diagnostics.info("catalog.load.start", of("mode", "tui"));
            String presetStreamKey = controller.request().platformStream();
            CompletableFuture<CatalogData> catalogLoadFuture =
                firstCatalogLoad.getAndSet(false)
                    ? catalogDataService.loadForStartup()
                    : catalogDataService.load();
            return catalogLoadFuture
                .handle(CatalogLoadDiagnostics.catalogLoadDiagnostics(diagnostics))
                .thenCompose(
                    loadResult ->
                        apiClient
                            .fetchPresets(presetStreamKey)
                            .handle(
                                (presets, throwable) -> {
                                  if (throwable != null) {
                                    diagnostics.error(
                                        "preset.load.failure",
                                        of("mode", "tui"),
                                        of("message", throwable.getClass().getSimpleName()));
                                    return Map.<String, List<String>>of();
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

      tui.run(
          (event, runner) -> {
            UiAction action = controller.onEvent(event);
            if (action.shouldQuit()) {
              diagnostics.info("tui.session.quit.requested", of("reason", "user"));
              runner.quit();
            }
            return action.handled();
          },
          controller::render);
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
}
