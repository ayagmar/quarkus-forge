package dev.ayagmar.quarkusforge;

import static dev.ayagmar.quarkusforge.diagnostics.DiagnosticField.of;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.ui.GitHubVisibility;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Executes post-TUI actions (IDE open, GitHub publish, terminal open, shell hooks) after the TUI
 * session ends with a successful generation.
 */
final class PostTuiActionExecutor {
  @FunctionalInterface
  interface CommandAvailabilityProvider {
    boolean isAvailable(String command);
  }

  private final ShellExecutor shellExecutor;
  private final CommandAvailabilityProvider commandAvailabilityProvider;

  PostTuiActionExecutor(ShellExecutor shellExecutor) {
    this(shellExecutor, CommandUtils::isCommandAvailable);
  }

  PostTuiActionExecutor(
      ShellExecutor shellExecutor, CommandAvailabilityProvider commandAvailabilityProvider) {
    this.shellExecutor = shellExecutor;
    this.commandAvailabilityProvider = commandAvailabilityProvider;
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
        if (!commandAvailabilityProvider.isAvailable("git")) {
          String message = "Publish to GitHub requires 'git' on PATH. Install it and rerun.";
          diagnostics.error(
              "tui.post-action.failure", of("action", "publish-github"), of("message", message));
          System.err.println(message);
          break;
        }
        if (!commandAvailabilityProvider.isAvailable("gh")) {
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
    List<IdeDetector.DetectedIde> detected = IdeDetector.detect();
    return detected.isEmpty() ? "code ." : detected.get(0).command();
  }

  static boolean isWindowsOs() {
    String osName = System.getProperty("os.name", "");
    return osName.toLowerCase(Locale.ROOT).contains("win");
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
}
