package dev.ayagmar.quarkusforge.application;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.diagnostics.BoundaryFailure;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class LiveStartupMetadataLoader implements StartupMetadataLoader {
  public static final Duration DEFAULT_REFRESH_TIMEOUT = Duration.ofSeconds(2);

  private final URI apiBaseUri;
  private final Function<URI, QuarkusApiClient> apiClientFactory;
  private final Duration refreshTimeout;
  private final DiagnosticLogger diagnostics;

  public LiveStartupMetadataLoader(
      URI apiBaseUri,
      Function<URI, QuarkusApiClient> apiClientFactory,
      DiagnosticLogger diagnostics) {
    this(apiBaseUri, apiClientFactory, DEFAULT_REFRESH_TIMEOUT, diagnostics);
  }

  LiveStartupMetadataLoader(
      URI apiBaseUri,
      Function<URI, QuarkusApiClient> apiClientFactory,
      Duration refreshTimeout,
      DiagnosticLogger diagnostics) {
    this.apiBaseUri = Objects.requireNonNull(apiBaseUri);
    this.apiClientFactory = Objects.requireNonNull(apiClientFactory);
    this.refreshTimeout = Objects.requireNonNull(refreshTimeout);
    this.diagnostics = Objects.requireNonNull(diagnostics);
  }

  @Override
  public StartupMetadataSelection load() {
    CompletableFuture<MetadataDto> metadataFuture = null;
    try (QuarkusApiClient apiClient = apiClientFactory.apply(apiBaseUri)) {
      metadataFuture = apiClient.fetchMetadata();
      MetadataDto metadata = metadataFuture.get(refreshTimeout.toMillis(), TimeUnit.MILLISECONDS);
      diagnostics.info("metadata.load.success", of("source", "live"));
      return new StartupMetadataSelection(
          MetadataCompatibilityContext.success(metadata), "live", "");
    } catch (InterruptedException interruptedException) {
      if (metadataFuture != null) {
        metadataFuture.cancel(true);
      }
      return fallbackSelection(interruptedException);
    } catch (TimeoutException timeoutException) {
      if (metadataFuture != null) {
        metadataFuture.cancel(true);
      }
      return fallbackSelection(timeoutException);
    } catch (ExecutionException executionException) {
      return fallbackSelection(executionException);
    } catch (RuntimeException runtimeException) {
      return fallbackSelection(runtimeException);
    }
  }

  StartupMetadataSelection fallbackSelection(Throwable throwable) {
    BoundaryFailure.Details failure = BoundaryFailure.fromThrowable(throwable);
    if ("interrupted".equals(failure.cancellationPhase())) {
      Thread.currentThread().interrupt();
    }
    StartupMetadataSelection selection = snapshotFallbackSelection(fallbackReason(failure));
    diagnostics.warn(
        "metadata.load.fallback",
        of("source", selection.sourceLabel()),
        of("detail", selection.detailMessage()),
        of("causeType", failure.causeType()));
    return selection;
  }

  private String fallbackReason(BoundaryFailure.Details failure) {
    return switch (failure.kind()) {
      case CANCELLED ->
          "Live metadata refresh "
              + ("interrupted".equals(failure.cancellationPhase()) ? "interrupted" : "cancelled");
      case TIMEOUT -> "Live metadata refresh timed out after " + refreshTimeout.toMillis() + "ms";
      case FAILURE -> "Live metadata unavailable (%s)".formatted(failure.userMessage());
    };
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
}
