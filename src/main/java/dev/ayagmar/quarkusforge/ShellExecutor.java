package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

final class ShellExecutor {
  private final BooleanSupplier windowsOs;
  private final ProcessRunner processRunner;

  ShellExecutor() {
    this(QuarkusForgeCli::isWindowsOs, ShellExecutor::runWithProcessBuilder);
  }

  ShellExecutor(BooleanSupplier windowsOs, ProcessRunner processRunner) {
    this.windowsOs = windowsOs;
    this.processRunner = processRunner;
  }

  void execute(String command, Path workingDirectory, String actionName, Diagnostics diagnostics) {
    int exitCode;
    try {
      List<String> invocation = commandInvocation(command, windowsOs.getAsBoolean());
      exitCode = processRunner.run(invocation, workingDirectory);
    } catch (IOException ioException) {
      diagnostics.error(actionName, ErrorMessageMapper.userFriendlyError(ioException));
      return;
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      diagnostics.error(actionName, "Interrupted while executing post action");
      return;
    }

    if (exitCode != 0) {
      diagnostics.error(actionName, "Command exited with status " + exitCode);
      return;
    }
    diagnostics.success(actionName);
  }

  static List<String> commandInvocation(String command, boolean windowsOs) {
    return windowsOs ? List.of("cmd.exe", "/c", command) : List.of("sh", "-lc", command);
  }

  private static int runWithProcessBuilder(List<String> invocation, Path workingDirectory)
      throws IOException, InterruptedException {
    ProcessBuilder processBuilder = new ProcessBuilder(invocation);
    processBuilder.directory(workingDirectory.toFile());
    processBuilder.inheritIO();
    return processBuilder.start().waitFor();
  }

  interface Diagnostics {
    void success(String actionName);

    void error(String actionName, String message);
  }

  @FunctionalInterface
  interface ProcessRunner {
    int run(List<String> invocation, Path workingDirectory)
        throws IOException, InterruptedException;
  }
}
