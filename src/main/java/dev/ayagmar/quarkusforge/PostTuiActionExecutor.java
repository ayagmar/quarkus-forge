package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.ui.GitHubVisibility;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Executes post-TUI actions (IDE open, GitHub publish, terminal open, shell hooks) after the TUI
 * session ends with a successful generation.
 */
final class PostTuiActionExecutor {
  private final ShellExecutor shellExecutor;

  PostTuiActionExecutor(ShellExecutor shellExecutor) {
    this.shellExecutor = shellExecutor;
  }

  void execute(
      TuiSessionSummary summary, String postGenerateHookCommand, DiagnosticLogger diagnostics) {
    PostGenerationExitPlan exitPlan = summary.exitPlan();
    if (exitPlan == null || exitPlan.projectDirectory() == null) {
      return;
    }
    Path generatedProjectDir = exitPlan.projectDirectory();

    if (postGenerateHookCommand != null && !postGenerateHookCommand.isBlank()) {
      String hookCommand = postGenerateHookCommand.strip();
      diagnostics.info(
          "tui.post-hook.start", postHookDiagnosticFields(generatedProjectDir, hookCommand));
      executeShellCommand(hookCommand, generatedProjectDir, diagnostics, "post-generate-hook");
    }

    switch (exitPlan.action()) {
      case OPEN_IDE -> {
        diagnostics.info(
            "tui.post-action.start",
            df("action", "open-ide"),
            df("directory", generatedProjectDir.toString()));
        executeShellCommand("code .", generatedProjectDir, diagnostics, "open-ide");
      }
      case PUBLISH_GITHUB -> {
        GitHubVisibility visibility = exitPlan.githubVisibility();
        diagnostics.info(
            "tui.post-action.start",
            df("action", "publish-github"),
            df("directory", generatedProjectDir.toString()),
            df("visibility", visibility.cliFlag()));
        if (!isCommandAvailable("gh")) {
          String message =
              "Publish to GitHub requires GitHub CLI ('gh') on PATH. Install it and rerun.";
          diagnostics.error(
              "tui.post-action.failure", df("action", "publish-github"), df("message", message));
          System.err.println(message);
          break;
        }
        executeShellCommand(
            githubPublishCommand(visibility), generatedProjectDir, diagnostics, "publish-github");
      }
      case OPEN_TERMINAL -> {
        diagnostics.info(
            "tui.post-action.start",
            df("action", "open-terminal"),
            df("directory", generatedProjectDir.toString()));
        openInteractiveShell(generatedProjectDir, exitPlan.nextCommand(), diagnostics);
      }
      case EXPORT_RECIPE_LOCK -> {
        // Export is handled inside the TUI and does not exit the session.
      }
      case QUIT, GENERATE_AGAIN -> {
        // No direct action.
      }
    }
  }

  private void openInteractiveShell(
      Path generatedProjectDir, String nextCommand, DiagnosticLogger diagnostics) {
    if (System.console() == null || isWindowsOs()) {
      printTerminalHandoff(generatedProjectDir, nextCommand);
      return;
    }

    System.out.println();
    System.out.println("Opening shell in: " + generatedProjectDir);
    if (nextCommand != null && !nextCommand.isBlank()) {
      System.out.println("Tip: " + nextCommand);
    }
    System.out.println("(Type 'exit' to return)");
    System.out.println();

    executeShellCommand("exec ${SHELL:-sh} -i", generatedProjectDir, diagnostics, "open-terminal");
  }

  private void executeShellCommand(
      String command, Path workingDirectory, DiagnosticLogger diagnostics, String actionName) {
    shellExecutor.execute(
        command,
        workingDirectory,
        actionName,
        new ShellExecutorDiagnostics() {
          @Override
          public void success(String action) {
            diagnostics.info("tui.post-action.success", df("action", action));
          }

          @Override
          public void error(String action, String message) {
            diagnostics.error(
                "tui.post-action.failure", df("action", action), df("message", message));
          }
        });
  }

  // ── Static helpers ────────────────────────────────────────────────────

  static boolean isWindowsOs() {
    String osName = System.getProperty("os.name", "");
    return osName.toLowerCase(java.util.Locale.ROOT).contains("win");
  }

  static boolean isCommandAvailable(String command) {
    if (command == null || command.isBlank()) {
      return false;
    }
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) {
      return false;
    }

    String executable = command.strip();
    String[] pathEntries = path.split(java.io.File.pathSeparator);
    boolean windows = isWindowsOs();
    for (String pathEntry : pathEntries) {
      if (pathEntry == null || pathEntry.isBlank()) {
        continue;
      }
      Path directory = Path.of(pathEntry);
      if (isExecutableFile(directory.resolve(executable), windows)) {
        return true;
      }
      if (windows) {
        if (isExecutableFile(directory.resolve(executable + ".exe"), true)
            || isExecutableFile(directory.resolve(executable + ".cmd"), true)
            || isExecutableFile(directory.resolve(executable + ".bat"), true)) {
          return true;
        }
      }
    }
    return false;
  }

  static String githubPublishCommand(GitHubVisibility visibility) {
    GitHubVisibility resolvedVisibility =
        visibility == null ? GitHubVisibility.PRIVATE : visibility;
    return "gh repo create --source . --push --" + resolvedVisibility.cliFlag();
  }

  static DiagnosticField[] postHookDiagnosticFields(Path generatedProjectDir, String command) {
    return new DiagnosticField[] {
      df("directory", generatedProjectDir.toString()),
      df("command", "<redacted>"),
      df("commandLength", command.length())
    };
  }

  private static void printTerminalHandoff(Path generatedProjectDir, String nextCommand) {
    System.out.println();
    System.out.println("Terminal handoff:");
    System.out.println("  cd " + generatedProjectDir);
    if (nextCommand != null && !nextCommand.isBlank()) {
      System.out.println("  " + nextCommand);
    }
    System.out.println();
  }

  private static boolean isExecutableFile(Path path, boolean windows) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    return windows || Files.isExecutable(path);
  }

  private static DiagnosticField df(String name, Object value) {
    return DiagnosticField.of(name, value);
  }
}
