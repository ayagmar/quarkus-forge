package dev.ayagmar.quarkusforge.runtime;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.ui.ExtensionCatalogLoadResult;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;

public final class CatalogLoadDiagnostics {
  private CatalogLoadDiagnostics() {}

  public static BiFunction<CatalogData, Throwable, ExtensionCatalogLoadResult>
      catalogLoadDiagnostics(DiagnosticLogger diagnostics) {
    return (catalogData, throwable) -> {
      if (throwable == null) {
        diagnostics.info(
            "catalog.load.success",
            of("mode", "tui"),
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
      Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(throwable);
      if (cause instanceof CancellationException) {
        diagnostics.error("catalog.load.cancelled", of("mode", "tui"));
      } else {
        diagnostics.error(
            "catalog.load.failure",
            of("mode", "tui"),
            of("causeType", cause.getClass().getSimpleName()),
            of("message", ErrorMessageMapper.userFriendlyError(cause)));
      }
      throw new CompletionException(cause);
    };
  }
}
