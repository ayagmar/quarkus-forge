package dev.ayagmar.quarkusforge.headless;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.archive.ArchiveException;
import dev.ayagmar.quarkusforge.cli.ExitCodes;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Maps the 4 standard async exceptions to exit codes with consistent diagnostics and stderr output.
 * Used by {@link HeadlessGenerationService} to eliminate repeated catch blocks.
 */
final class AsyncFailureHandler {
  private AsyncFailureHandler() {}

  /**
   * Handles an async operation failure. Returns the appropriate exit code.
   *
   * @param exception the caught exception (one of CancellationException, InterruptedException,
   *     TimeoutException, ExecutionException)
   * @param timeout the timeout duration (for timeout messages)
   * @param diagnosticPrefix the diagnostic event prefix (e.g. "catalog.load")
   * @param userMessagePrefix the user-facing error prefix (e.g. "Failed to load extension catalog")
   * @param diagnostics the diagnostic logger
   */
  static int handleFailure(
      Exception exception,
      Duration timeout,
      String diagnosticPrefix,
      String userMessagePrefix,
      DiagnosticLogger diagnostics) {
    return switch (exception) {
      case CancellationException _ -> {
        diagnostics.error(diagnosticPrefix + ".cancelled", of("phase", "execution"));
        System.err.println(userMessagePrefix + ": cancelled.");
        yield ExitCodes.CANCELLED;
      }
      case InterruptedException _ -> {
        Thread.currentThread().interrupt();
        diagnostics.error(diagnosticPrefix + ".cancelled", of("phase", "interrupted"));
        System.err.println(userMessagePrefix + ": cancelled.");
        yield ExitCodes.CANCELLED;
      }
      case TimeoutException _ -> {
        diagnostics.error(diagnosticPrefix + ".timeout", of("timeoutMs", timeout.toMillis()));
        System.err.println(
            userMessagePrefix + ": request timed out after " + timeout.toMillis() + "ms");
        yield ExitCodes.NETWORK;
      }
      case ExecutionException executionException -> {
        Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(executionException);
        diagnostics.error(
            diagnosticPrefix + ".failure",
            of("causeType", cause.getClass().getSimpleName()),
            of("message", ErrorMessageMapper.userFriendlyError(cause)));
        System.err.println(userMessagePrefix + ": " + ErrorMessageMapper.userFriendlyError(cause));
        yield mapFailureToExitCode(cause);
      }
      default ->
          throw new IllegalArgumentException("Unexpected exception type: " + exception.getClass());
    };
  }

  static int mapFailureToExitCode(Throwable throwable) {
    Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(throwable);
    return switch (cause) {
      case CancellationException ignored -> ExitCodes.CANCELLED;
      case ApiClientException ignored -> ExitCodes.NETWORK;
      case ArchiveException ignored -> ExitCodes.ARCHIVE;
      default -> ExitCodes.INTERNAL;
    };
  }
}
