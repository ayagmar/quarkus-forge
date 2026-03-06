package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_ERR)
class CatalogLoadDiagnosticsTest {
  private PrintStream originalErr;
  private ByteArrayOutputStream stderr;

  @BeforeEach
  void captureStderr() {
    originalErr = System.err;
    stderr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreStderr() {
    System.setErr(originalErr);
  }

  @Test
  void handlePresetLoadFailureUnwrapsAsyncCauseAndLogsFriendlyMessage() {
    Map<String, java.util.List<String>> presets =
        CatalogLoadDiagnostics.handlePresetLoadFailure(
            DiagnosticLogger.create(true),
            new CompletionException(new ApiClientException("connection refused")));

    assertThat(presets).isEmpty();
    assertThat(stderr.toString(StandardCharsets.UTF_8))
        .contains("\"event\":\"preset.load.failure\"")
        .contains("\"causeType\":\"ApiClientException\"")
        .contains("connection refused")
        .doesNotContain("CompletionException");
  }

  @Test
  void catalogLoadDiagnosticsReturnsLoadResultAndLogsSuccess() {
    CatalogData data =
        new CatalogData(
            new MetadataDto(java.util.List.of("25"), java.util.List.of("maven"), Map.of()),
            java.util.List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "web")),
            CatalogSource.LIVE,
            false,
            "fresh");

    var result =
        CatalogLoadDiagnostics.catalogLoadDiagnostics(DiagnosticLogger.create(true))
            .apply(data, null);

    assertThat(result.extensions()).hasSize(1);
    assertThat(result.source()).isEqualTo(CatalogSource.LIVE);
    assertThat(result.detailMessage()).isEqualTo("fresh");
    assertThat(stderr.toString(StandardCharsets.UTF_8))
        .contains("\"event\":\"catalog.load.success\"")
        .contains("\"source\":\"live\"");
  }

  @Test
  void catalogLoadDiagnosticsTreatsCancellationSeparately() {
    assertThatThrownBy(
            () ->
                CatalogLoadDiagnostics.catalogLoadDiagnostics(DiagnosticLogger.create(true))
                    .apply(null, new CompletionException(new CancellationException("cancelled"))))
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(CancellationException.class);

    assertThat(stderr.toString(StandardCharsets.UTF_8))
        .contains("\"event\":\"catalog.load.cancelled\"")
        .doesNotContain("\"event\":\"catalog.load.failure\"");
  }
}
