package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
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
   * @param failureExitCodeMapper maps execution failure root cause to exit code
   */
  static int handleFailure(
      Exception exception,
      Duration timeout,
      String diagnosticPrefix,
      String userMessagePrefix,
      DiagnosticLogger diagnostics,
      java.util.function.Function<Throwable, Integer> failureExitCodeMapper) {
    return switch (exception) {
      case CancellationException _ -> {
        diagnostics.error(diagnosticPrefix + ".cancelled", df("phase", "execution"));
        System.err.println(userMessagePrefix + ": cancelled.");
        yield ExitCodes.CANCELLED;
      }
      case InterruptedException _ -> {
        Thread.currentThread().interrupt();
        diagnostics.error(diagnosticPrefix + ".cancelled", df("phase", "interrupted"));
        System.err.println(userMessagePrefix + ": cancelled.");
        yield ExitCodes.CANCELLED;
      }
      case TimeoutException _ -> {
        diagnostics.error(diagnosticPrefix + ".timeout", df("timeoutMs", timeout.toMillis()));
        System.err.println(
            userMessagePrefix + ": request timed out after " + timeout.toMillis() + "ms");
        yield ExitCodes.NETWORK;
      }
      case ExecutionException executionException -> {
        Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(executionException);
        diagnostics.error(
            diagnosticPrefix + ".failure",
            df("causeType", cause.getClass().getSimpleName()),
            df("message", ErrorMessageMapper.userFriendlyError(cause)));
        System.err.println(userMessagePrefix + ": " + ErrorMessageMapper.userFriendlyError(cause));
        yield failureExitCodeMapper.apply(cause);
      }
      default ->
          throw new IllegalArgumentException("Unexpected exception type: " + exception.getClass());
    };
  }

  private static DiagnosticField df(String name, Object value) {
    return DiagnosticField.of(name, value);
  }
}
