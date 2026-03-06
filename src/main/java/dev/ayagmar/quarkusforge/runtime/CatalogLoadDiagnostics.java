package dev.ayagmar.quarkusforge.runtime;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.diagnostics.BoundaryFailure;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

final class CatalogLoadDiagnostics {
  private static final String TUI_MODE = "tui";

  private CatalogLoadDiagnostics() {}

  static BiFunction<CatalogData, Throwable, ExtensionCatalogLoadResult> catalogLoadDiagnostics(
      DiagnosticLogger diagnostics) {
    return catalogLoadDiagnostics(diagnostics, TUI_MODE);
  }

  static BiFunction<CatalogData, Throwable, ExtensionCatalogLoadResult> catalogLoadDiagnostics(
      DiagnosticLogger diagnostics, String mode) {
    return (catalogData, throwable) -> {
      if (throwable == null) {
        diagnostics.info(
            "catalog.load.success",
            of("mode", mode),
            of("source", catalogData.source().label()),
            of("stale", catalogData.stale()),
            of("detail", catalogData.detailMessage()));
        return new ExtensionCatalogLoadResult(
            catalogData.extensions(),
            catalogData.source(),
            catalogData.stale(),
            catalogData.detailMessage(),
            catalogData.metadata());
      }
      BoundaryFailure.Details failure = BoundaryFailure.fromThrowable(throwable);
      switch (failure.kind()) {
        case CANCELLED ->
            diagnostics.error(
                "catalog.load.cancelled",
                of("mode", mode),
                of("phase", failure.cancellationPhase()));
        case TIMEOUT ->
            diagnostics.error(
                "catalog.load.timeout", of("mode", mode), of("message", failure.userMessage()));
        case FAILURE ->
            diagnostics.error(
                "catalog.load.failure",
                of("mode", mode),
                of("causeType", failure.causeType()),
                of("message", failure.userMessage()));
      }
      throw new CompletionException(ThrowableUnwrapper.unwrapAsyncFailure(throwable));
    };
  }

  static Map<String, List<String>> handlePresetLoadFailure(
      DiagnosticLogger diagnostics, Throwable throwable) {
    return handlePresetLoadFailure(diagnostics, throwable, TUI_MODE);
  }

  static Map<String, List<String>> handlePresetLoadFailure(
      DiagnosticLogger diagnostics, Throwable throwable, String mode) {
    BoundaryFailure.Details failure = BoundaryFailure.fromThrowable(throwable);
    switch (failure.kind()) {
      case CANCELLED ->
          diagnostics.error(
              "preset.load.cancelled", of("mode", mode), of("phase", failure.cancellationPhase()));
      case TIMEOUT ->
          diagnostics.error(
              "preset.load.timeout", of("mode", mode), of("message", failure.userMessage()));
      case FAILURE ->
          diagnostics.error(
              "preset.load.failure",
              of("mode", mode),
              of("causeType", failure.causeType()),
              of("message", failure.userMessage()));
    }
    return Map.of();
  }
}
