package dev.ayagmar.quarkusforge.postgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ShellExecutorTest {
  @Test
  void commandInvocationUsesPosixShellForNonWindows() {
    assertThat(ShellExecutor.commandInvocation("echo ok", false))
        .containsExactly("sh", "-lc", "echo ok");
  }

  @Test
  void commandInvocationUsesCmdForWindows() {
    assertThat(ShellExecutor.commandInvocation("echo ok", true))
        .containsExactly("cmd.exe", "/c", "echo ok");
  }

  @Test
  void executeReportsSuccessForZeroExitCode() {
    CapturingDiagnostics diagnostics = new CapturingDiagnostics();
    List<String> invocation = new ArrayList<>();
    ShellExecutor executor =
        new ShellExecutor(
            () -> false,
            (command, workingDirectory) -> {
              invocation.addAll(command);
              return 0;
            });

    executor.execute("echo ok", Path.of("."), "open-ide", diagnostics);

    assertThat(invocation).containsExactly("sh", "-lc", "echo ok");
    assertThat(diagnostics.successActions).containsExactly("open-ide");
    assertThat(diagnostics.errorMessages).isEmpty();
  }

  @Test
  void executeReportsFailureForNonZeroExitCode() {
    CapturingDiagnostics diagnostics = new CapturingDiagnostics();
    ShellExecutor executor = new ShellExecutor(() -> false, (command, workingDirectory) -> 42);

    executor.execute("echo ok", Path.of("."), "post-generate-hook", diagnostics);

    assertThat(diagnostics.successActions).isEmpty();
    assertThat(diagnostics.errorMessages)
        .containsExactly("post-generate-hook:Command exited with status 42");
  }

  @Test
  void executeReportsFailureForIoException() {
    CapturingDiagnostics diagnostics = new CapturingDiagnostics();
    ShellExecutor executor =
        new ShellExecutor(
            () -> false,
            (command, workingDirectory) -> {
              throw new IOException("broken pipe");
            });

    executor.execute("echo ok", Path.of("."), "open-terminal", diagnostics);

    assertThat(diagnostics.successActions).isEmpty();
    assertThat(diagnostics.errorMessages).containsExactly("open-terminal:broken pipe");
  }

  @Test
  void executeInterruptsThreadAndReportsFailureWhenInterrupted() {
    CapturingDiagnostics diagnostics = new CapturingDiagnostics();
    AtomicBoolean interruptedBefore = new AtomicBoolean(Thread.currentThread().isInterrupted());
    ShellExecutor executor =
        new ShellExecutor(
            () -> false,
            (command, workingDirectory) -> {
              interruptedBefore.set(Thread.currentThread().isInterrupted());
              throw new InterruptedException("interrupted");
            });

    executor.execute("echo ok", Path.of("."), "open-terminal", diagnostics);

    assertThat(interruptedBefore.get()).isFalse();
    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    Thread.interrupted();
    assertThat(diagnostics.errorMessages)
        .containsExactly("open-terminal:Interrupted while executing post action");
  }

  @Test
  void windowsCommandUsesCmd() {
    CapturingDiagnostics diagnostics = new CapturingDiagnostics();
    List<String> captured = new ArrayList<>();
    ShellExecutor executor =
        new ShellExecutor(
            () -> true,
            (command, workingDirectory) -> {
              captured.addAll(command);
              return 0;
            });

    executor.execute("dir", Path.of("."), "test-cmd", diagnostics);

    assertThat(captured).containsExactly("cmd.exe", "/c", "dir");
    assertThat(diagnostics.successActions).containsExactly("test-cmd");
  }

  private static final class CapturingDiagnostics implements ShellExecutorDiagnostics {
    private final List<String> successActions = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    @Override
    public void success(String actionName) {
      successActions.add(actionName);
    }

    @Override
    public void error(String actionName, String message) {
      errorMessages.add(actionName + ":" + message);
    }
  }
}
