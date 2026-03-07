package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.application.InputResolutionService;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.ui.UiScheduler;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_ERR)
class TuiBootstrapServiceRunTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21", "25"),
          List.of("maven"),
          Map.of("maven", List.of("21", "25")),
          List.of(
              new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"))));

  @Test
  void runSessionUsesStartupCatalogLoadAndReturnsSummary() throws Exception {
    TuiBootstrapService service = new TuiBootstrapService();
    ForgeUiState initialState = defaultState();
    CatalogData catalogData =
        new CatalogData(
            METADATA,
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "web")),
            CatalogSource.LIVE,
            false,
            "live catalog ready");
    AtomicInteger startupLoads = new AtomicInteger();
    AtomicInteger reloadLoads = new AtomicInteger();
    AtomicReference<String> presetRequest = new AtomicReference<>();

    String stderr =
        captureStandardError(
            () -> {
              TuiSessionSummary summary =
                  service.runSession(
                      initialState,
                      25,
                      DiagnosticLogger.create(true),
                      new TuiBootstrapService.CatalogLoader() {
                        @Override
                        public CompletableFuture<CatalogData> load() {
                          reloadLoads.incrementAndGet();
                          return CompletableFuture.completedFuture(catalogData);
                        }

                        @Override
                        public CompletableFuture<CatalogData> loadForStartup() {
                          startupLoads.incrementAndGet();
                          return CompletableFuture.completedFuture(catalogData);
                        }
                      },
                      streamKey -> {
                        presetRequest.set(streamKey);
                        return CompletableFuture.completedFuture(
                            Map.of("rest", List.of("io.quarkus:quarkus-rest")));
                      },
                      (generationRequest, outputDirectory, cancelled, progressListener) ->
                          CompletableFuture.failedFuture(new IllegalStateException("unused")),
                      ExtensionFavoritesStore.inMemory(),
                      UiScheduler.immediate(),
                      List.of(),
                      (controller, diagnostics) -> {});

              assertThat(summary.finalRequest()).isEqualTo(initialState.request());
              assertThat(summary.exitPlan()).isNull();
            });

    assertThat(startupLoads).hasValue(1);
    assertThat(reloadLoads).hasValue(0);
    assertThat(presetRequest).hasValue("io.quarkus.platform:3.31");
    assertThat(stderr)
        .contains("\"event\":\"catalog.load.start\"")
        .contains("\"event\":\"preset.load.success\"")
        .contains("\"event\":\"tui.session.exit\"");
  }

  @Test
  void runSessionUsesLiveRecommendedStreamForPresetsWhenStartupMetadataWasUnavailable()
      throws Exception {
    TuiBootstrapService service = new TuiBootstrapService();
    ForgeUiState initialState =
        InputResolutionService.resolveInitialState(
            new CliPrefill("com.example", "forge-app", "1.0.0", "", ".", "", "maven", "25"),
            MetadataCompatibilityContext.failure("startup metadata unavailable"));
    CatalogData catalogData =
        new CatalogData(
            METADATA,
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "web")),
            CatalogSource.LIVE,
            false,
            "live catalog ready");
    AtomicReference<String> presetRequest = new AtomicReference<>();

    service.runSession(
        initialState,
        0,
        DiagnosticLogger.create(false),
        completedCatalogLoader(catalogData),
        streamKey -> {
          presetRequest.set(streamKey);
          return CompletableFuture.completedFuture(Map.of());
        },
        (generationRequest, outputDirectory, cancelled, progressListener) ->
            CompletableFuture.failedFuture(new IllegalStateException("unused")),
        ExtensionFavoritesStore.inMemory(),
        UiScheduler.immediate(),
        List.of(),
        (controller, diagnostics) -> {});

    assertThat(presetRequest).hasValue("io.quarkus.platform:3.31");
  }

  @Test
  void runSessionLogsFailureAndRethrowsSessionLoopException() {
    TuiBootstrapService service = new TuiBootstrapService();
    ForgeUiState initialState = defaultState();
    CatalogData catalogData =
        new CatalogData(
            METADATA,
            List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "web")),
            CatalogSource.LIVE,
            false,
            "");
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));

      assertThatThrownBy(
              () ->
                  service.runSession(
                      initialState,
                      0,
                      DiagnosticLogger.create(true),
                      completedCatalogLoader(catalogData),
                      streamKey -> CompletableFuture.completedFuture(Map.of()),
                      (generationRequest, outputDirectory, cancelled, progressListener) ->
                          CompletableFuture.failedFuture(new IllegalStateException("unused")),
                      ExtensionFavoritesStore.inMemory(),
                      UiScheduler.immediate(),
                      List.of(),
                      (controller, diagnostics) -> {
                        throw new IllegalStateException("boom");
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("boom");
    } finally {
      System.setErr(originalErr);
    }

    assertThat(stderr.toString(StandardCharsets.UTF_8))
        .contains("\"event\":\"tui.session.failure\"")
        .contains("boom");
  }

  private static ForgeUiState defaultState() {
    return InputResolutionService.resolveInitialState(
        new CliPrefill("com.example", "forge-app", "1.0.0", "", ".", "", "maven", "25"),
        MetadataCompatibilityContext.success(METADATA));
  }

  private static TuiBootstrapService.CatalogLoader completedCatalogLoader(CatalogData catalogData) {
    return new TuiBootstrapService.CatalogLoader() {
      @Override
      public CompletableFuture<CatalogData> load() {
        return CompletableFuture.completedFuture(catalogData);
      }

      @Override
      public CompletableFuture<CatalogData> loadForStartup() {
        return CompletableFuture.completedFuture(catalogData);
      }
    };
  }

  private static String captureStandardError(ThrowingRunnable action) throws Exception {
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      action.run();
      return stderr.toString(StandardCharsets.UTF_8);
    } finally {
      System.setErr(originalErr);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
