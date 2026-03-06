package dev.ayagmar.quarkusforge.diagnostics;

import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.archive.ArchiveException;
import dev.ayagmar.quarkusforge.cli.ExitCodes;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Shared failure classification for runtime and headless boundaries. */
public final class BoundaryFailure {
  private BoundaryFailure() {}

  public static Details fromException(Exception exception, Duration timeout) {
    return switch (exception) {
      case CancellationException _ -> cancelled("execution");
      case InterruptedException _ -> cancelled("interrupted");
      case TimeoutException _ -> timeout(timeout);
      case ExecutionException executionException -> fromAsyncWrapper(executionException, timeout);
      case CompletionException completionException ->
          fromAsyncWrapper(completionException, timeout);
      default -> fromThrowable(exception);
    };
  }

  public static Details fromThrowable(Throwable throwable) {
    Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(throwable);
    return switch (cause) {
      case CancellationException _ -> cancelled("execution");
      case InterruptedException _ -> cancelled("interrupted");
      case TimeoutException _ -> timeout(null);
      case ApiClientException _ ->
          failure(ExitCodes.NETWORK, cause, ErrorMessageMapper.userFriendlyError(cause));
      case ArchiveException _ ->
          failure(ExitCodes.ARCHIVE, cause, ErrorMessageMapper.userFriendlyError(cause));
      default -> failure(ExitCodes.INTERNAL, cause, ErrorMessageMapper.userFriendlyError(cause));
    };
  }

  private static Details cancelled(String phase) {
    return new Details(
        Kind.CANCELLED,
        ExitCodes.CANCELLED,
        phase.equals("interrupted")
            ? InterruptedException.class.getSimpleName()
            : CancellationException.class.getSimpleName(),
        "cancelled.",
        phase);
  }

  private static Details timeout(Duration timeout) {
    long timeoutMs = timeout == null ? 0L : timeout.toMillis();
    return new Details(
        Kind.TIMEOUT,
        ExitCodes.NETWORK,
        TimeoutException.class.getSimpleName(),
        "request timed out after " + timeoutMs + "ms",
        "");
  }

  private static Details failure(int exitCode, Throwable cause, String userMessage) {
    return new Details(
        Kind.FAILURE,
        exitCode,
        cause == null ? "UnknownFailure" : cause.getClass().getSimpleName(),
        userMessage,
        "");
  }

  private static Details fromAsyncWrapper(Throwable wrapper, Duration timeout) {
    Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(wrapper);
    if (cause instanceof TimeoutException) {
      return timeout(timeout);
    }
    return fromThrowable(cause);
  }

  public enum Kind {
    CANCELLED,
    TIMEOUT,
    FAILURE
  }

  public record Details(
      Kind kind, int exitCode, String causeType, String userMessage, String cancellationPhase) {}
}
