package dev.ayagmar.quarkusforge;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogDataService;
import dev.ayagmar.quarkusforge.api.CatalogSnapshotCache;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.archive.OverwritePolicy;
import dev.ayagmar.quarkusforge.archive.ProjectArchiveService;
import dev.ayagmar.quarkusforge.archive.SafeZipExtractor;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.ui.CoreTuiController;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import dev.ayagmar.quarkusforge.ui.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.ui.GenerationProgressUpdate;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import dev.ayagmar.quarkusforge.ui.UiAction;
import dev.ayagmar.quarkusforge.ui.UiScheduler;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class TuiBootstrapService {
  TuiSessionSummary run(
      ForgeUiState initialState,
      int searchDebounceMs,
      RuntimeConfig runtimeConfig,
      DiagnosticLogger diagnostics)
      throws Exception {
    diagnostics.info(
        "tui.session.start",
        of("smokeMode", false),
        of("searchDebounceMs", Math.max(0, searchDebounceMs)));
    QuarkusForgeCli.configureTerminalBackendPreference();
    TuiConfig tuiConfig = QuarkusForgeCli.appTuiConfig();
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
              java.time.Duration.ofMillis(Math.max(0, searchDebounceMs)),
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
      controller.setStartupOverlayMinDuration(QuarkusForgeCli.STARTUP_SPLASH_MIN_DURATION);
      controller.loadExtensionCatalogAsync(
          () -> {
            diagnostics.info("catalog.load.start", of("mode", "tui"));
            String presetStreamKey = initialState.request().platformStream();
            CompletableFuture<CatalogData> catalogLoadFuture =
                firstCatalogLoad.getAndSet(false)
                    ? catalogDataService.loadForStartup()
                    : catalogDataService.load();
            return catalogLoadFuture
                .handle(QuarkusForgeCli.catalogLoadDiagnostics(diagnostics))
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
          of(
              "message",
              dev.ayagmar.quarkusforge.api.ErrorMessageMapper.userFriendlyError(exception)));
      throw exception;
    }
  }
}
