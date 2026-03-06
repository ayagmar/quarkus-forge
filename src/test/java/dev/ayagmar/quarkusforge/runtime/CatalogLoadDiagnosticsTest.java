package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
}
