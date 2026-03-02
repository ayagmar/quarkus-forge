package dev.ayagmar.quarkusforge;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.ui.GitHubVisibility;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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
    if (summary == null) {
      return;
    }
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

    if (exitPlan.action() == null) {
      return;
    }

    switch (exitPlan.action()) {
      case OPEN_IDE -> {
        String ideCommand =
            exitPlan.ideCommand() != null ? exitPlan.ideCommand() : resolveIdeCommand();
        diagnostics.info(
            "tui.post-action.start",
            of("action", "open-ide"),
            of("directory", generatedProjectDir.toString()));
        executeShellCommand(ideCommand, generatedProjectDir, diagnostics, "open-ide");
      }
      case PUBLISH_GITHUB -> {
        GitHubVisibility visibility =
            exitPlan.githubVisibility() == null
                ? GitHubVisibility.PRIVATE
                : exitPlan.githubVisibility();
        diagnostics.info(
            "tui.post-action.start",
            of("action", "publish-github"),
            of("directory", generatedProjectDir.toString()),
            of("visibility", visibility.cliFlag()));
        if (!isCommandAvailable("git")) {
          String message = "Publish to GitHub requires 'git' on PATH. Install it and rerun.";
          diagnostics.error(
              "tui.post-action.failure", of("action", "publish-github"), of("message", message));
          System.err.println(message);
          break;
        }
        if (!isCommandAvailable("gh")) {
          String message =
              "Publish to GitHub requires GitHub CLI ('gh') on PATH. Install it and rerun.";
          diagnostics.error(
              "tui.post-action.failure", of("action", "publish-github"), of("message", message));
          System.err.println(message);
          break;
        }
        executeShellCommand(
            githubPublishCommand(visibility), generatedProjectDir, diagnostics, "publish-github");
      }
      case OPEN_TERMINAL -> {
        diagnostics.info(
            "tui.post-action.start",
            of("action", "open-terminal"),
            of("directory", generatedProjectDir.toString()));
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
            diagnostics.info("tui.post-action.success", of("action", action));
          }

          @Override
          public void error(String action, String message) {
            diagnostics.error(
                "tui.post-action.failure", of("action", action), of("message", message));
          }
        });
  }

  // ── Static helpers ────────────────────────────────────────────────────

  static String resolveIdeCommand() {
    String custom = System.getenv("QUARKUS_FORGE_IDE_COMMAND");
    if (custom != null && !custom.isBlank()) {
      return custom.strip();
    }
    return "code .";
  }

  static boolean isWindowsOs() {
    String osName = System.getProperty("os.name", "");
    return osName.toLowerCase(Locale.ROOT).contains("win");
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
    String[] pathEntries = path.split(File.pathSeparator);
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
    return "git init && git add . && git commit -m 'Initial commit from quarkus-forge' && "
        + "gh repo create --source . --push --"
        + resolvedVisibility.cliFlag();
  }

  static DiagnosticField[] postHookDiagnosticFields(Path generatedProjectDir, String command) {
    return new DiagnosticField[] {
      of("directory", generatedProjectDir.toString()),
      of("command", "<redacted>"),
      of("commandLength", command.length())
    };
  }

  private static void printTerminalHandoff(Path generatedProjectDir, String nextCommand) {
    System.out.println();
    System.out.println("Terminal handoff:");
    System.out.println("  cd \"" + generatedProjectDir + "\"");
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
}
