package dev.ayagmar.quarkusforge.headless;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.diagnostics.BoundaryFailure;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import java.time.Duration;

/**
 * Maps async and transport failures to exit codes with consistent diagnostics and stderr output.
 * Used by {@link HeadlessGenerationService} to eliminate repeated catch blocks.
 */
final class AsyncFailureHandler {
  private AsyncFailureHandler() {}

  /**
   * Handles an async operation failure. Returns the appropriate exit code.
   *
   * @param exception the caught exception
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
    BoundaryFailure.Details failure = BoundaryFailure.fromException(exception, timeout);
    switch (failure.kind()) {
      case CANCELLED -> {
        if ("interrupted".equals(failure.cancellationPhase())) {
          Thread.currentThread().interrupt();
        }
        diagnostics.error(
            diagnosticPrefix + ".cancelled", of("phase", failure.cancellationPhase()));
      }
      case TIMEOUT ->
          diagnostics.error(diagnosticPrefix + ".timeout", of("timeoutMs", timeout.toMillis()));
      case FAILURE ->
          diagnostics.error(
              diagnosticPrefix + ".failure",
              of("causeType", failure.causeType()),
              of("message", failure.userMessage()));
    }
    System.err.println(userMessagePrefix + ": " + failure.userMessage());
    return failure.exitCode();
  }

  static int mapFailureToExitCode(Throwable throwable) {
    return BoundaryFailure.fromThrowable(throwable).exitCode();
  }
}
