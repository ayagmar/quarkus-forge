package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AsyncFailureHandlerTest {
  private final DiagnosticLogger diagnostics = DiagnosticLogger.create(true);
  private final Duration timeout = Duration.ofSeconds(5);
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
  void cancellationExceptionReturnsCancelledExitCode() {
    int exitCode =
        AsyncFailureHandler.handleFailure(
            new CancellationException(),
            timeout,
            "test.op",
            "Operation failed",
            diagnostics,
            _ -> ExitCodes.INTERNAL);

    assertThat(exitCode).isEqualTo(ExitCodes.CANCELLED);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("cancelled");
  }

  @Test
  void interruptedExceptionReturnsCancelledAndSetsInterruptFlag() {
    try {
      int exitCode =
          AsyncFailureHandler.handleFailure(
              new InterruptedException(),
              timeout,
              "test.op",
              "Operation failed",
              diagnostics,
              _ -> ExitCodes.INTERNAL);

      assertThat(exitCode).isEqualTo(ExitCodes.CANCELLED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("cancelled");
    } finally {
      Thread.interrupted(); // clear flag
    }
  }

  @Test
  void timeoutExceptionReturnsNetworkExitCode() {
    int exitCode =
        AsyncFailureHandler.handleFailure(
            new TimeoutException("timed out"),
            timeout,
            "test.op",
            "Operation failed",
            diagnostics,
            _ -> ExitCodes.INTERNAL);

    assertThat(exitCode).isEqualTo(ExitCodes.NETWORK);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("timed out after 5000ms");
  }

  @Test
  void executionExceptionUnwrapsCauseAndDelegatesExitCode() {
    RuntimeException rootCause = new RuntimeException("root failure");
    ExecutionException executionException = new ExecutionException(rootCause);

    int exitCode =
        AsyncFailureHandler.handleFailure(
            executionException,
            timeout,
            "test.op",
            "Operation failed",
            diagnostics,
            cause -> {
              assertThat(cause).isSameAs(rootCause);
              return ExitCodes.ARCHIVE;
            });

    assertThat(exitCode).isEqualTo(ExitCodes.ARCHIVE);
    assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("root failure");
  }

  @Test
  void unexpectedExceptionTypeThrowsIllegalArgument() {
    assertThatThrownBy(
            () ->
                AsyncFailureHandler.handleFailure(
                    new IllegalStateException("unexpected"),
                    timeout,
                    "test.op",
                    "Op failed",
                    diagnostics,
                    _ -> ExitCodes.INTERNAL))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unexpected exception type");
  }
}
