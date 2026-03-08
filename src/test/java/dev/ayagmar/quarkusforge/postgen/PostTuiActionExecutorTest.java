package dev.ayagmar.quarkusforge.postgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.ui.GitHubVisibility;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitAction;
import dev.ayagmar.quarkusforge.ui.PostGenerationExitPlan;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostTuiActionExecutorTest {
  private final List<ShellInvocation> shellInvocations = new ArrayList<>();
  private final ShellExecutor capturingExecutor =
      new ShellExecutor(
          () -> false,
          (invocation, dir) -> {
            shellInvocations.add(new ShellInvocation(invocation, dir));
            return 0;
          });
  private final PostTuiActionExecutor executor = new PostTuiActionExecutor(capturingExecutor);
  private final DiagnosticLogger diagnostics = DiagnosticLogger.create(false);

  @Test
  void executeWithNullSummaryDoesNothing() {
    executor.execute(null, "echo test", diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void executeWithNullExitPlanDoesNothing() {
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), null);
    executor.execute(summary, "echo test", diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void executeWithNullProjectDirectoryDoesNothing() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(PostGenerationExitAction.QUIT, null, "");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "echo test", diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void executeRunsPostGenerateHookBeforeAction() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_IDE, Path.of("/tmp/project"), "", null, "idea .");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "npm install", diagnostics);
    assertThat(shellInvocations).hasSize(2);
    assertThat(shellInvocations.get(0).invocation()).contains("npm install");
    assertThat(shellInvocations.get(1).invocation()).contains("idea .");
  }

  @Test
  void executeSkipsBlankHookCommand() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_IDE, Path.of("/tmp/project"), "", null, "code .");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "   ", diagnostics);
    assertThat(shellInvocations).hasSize(1);
    assertThat(shellInvocations.get(0).invocation()).contains("code .");
  }

  @Test
  void openIdeUsesExitPlanIdeCommand() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_IDE, Path.of("/tmp/project"), "", null, "nvim .");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "", diagnostics);
    assertThat(shellInvocations).hasSize(1);
    assertThat(shellInvocations.get(0).invocation()).contains("nvim .");
    assertThat(shellInvocations.get(0).directory()).isEqualTo(Path.of("/tmp/project"));
  }

  @Test
  void quitActionDoesNotInvokeShell() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(PostGenerationExitAction.QUIT, Path.of("/tmp/project"), "");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "", diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void generateAgainActionDoesNotInvokeShell() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.GENERATE_AGAIN, Path.of("/tmp/project"), "");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "", diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void nullHookCommandDoesNotInvokeShell() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(PostGenerationExitAction.QUIT, Path.of("/tmp/project"), "");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, null, diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void openTerminalFallsBackToHandoffWithoutConsole() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_TERMINAL, Path.of("/tmp/project"), "mvn quarkus:dev");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      executor.execute(summary, "", diagnostics);
    } finally {
      System.setOut(originalOut);
    }
    String output = stdout.toString(StandardCharsets.UTF_8);
    // Without a console (CI/tests), it prints terminal handoff instructions
    assertThat(output).contains("Terminal handoff:");
  }

  @Test
  void githubPublishCommandContainsGitAndGhSteps() {
    String command = PostTuiActionExecutor.githubPublishCommand(GitHubVisibility.PRIVATE);
    assertThat(command).contains("git init").contains("gh repo create").contains("--private");
  }

  @Test
  void githubPublishCommandUsesPublicVisibility() {
    String command = PostTuiActionExecutor.githubPublishCommand(GitHubVisibility.PUBLIC);
    assertThat(command).contains("--public");
  }

  @Test
  void githubPublishCommandDefaultsToPrivateForNull() {
    String command = PostTuiActionExecutor.githubPublishCommand(null);
    assertThat(command).contains("--private");
  }

  @Test
  void resolveIdeCommandFallsBackToCode() {
    String envIde = System.getenv("QUARKUS_FORGE_IDE_COMMAND");
    String command = PostTuiActionExecutor.resolveIdeCommand();
    if (envIde != null && !envIde.isBlank()) {
      assertThat(command).isEqualTo(envIde.strip());
    } else {
      List<IdeDetector.DetectedIde> detected = IdeDetector.detect();
      String expected = detected.isEmpty() ? "code ." : detected.get(0).command();
      assertThat(command).isEqualTo(expected);
    }
  }

  @Test
  void isWindowsOsDetectsCorrectly() {
    String previous = System.getProperty("os.name");
    try {
      System.setProperty("os.name", "Windows 10");
      assertThat(PostTuiActionExecutor.isWindowsOs()).isTrue();

      System.setProperty("os.name", "Linux");
      assertThat(PostTuiActionExecutor.isWindowsOs()).isFalse();
    } finally {
      if (previous == null) {
        System.clearProperty("os.name");
      } else {
        System.setProperty("os.name", previous);
      }
    }
  }

  @Test
  void postHookDiagnosticFieldsRedactsCommand() {
    var fields =
        PostTuiActionExecutor.postHookDiagnosticFields(Path.of("/project"), "secret command");
    assertThat(fields).hasSize(3);
    // Command should be redacted
    boolean hasRedacted = false;
    boolean hasLength = false;
    for (var field : fields) {
      if ("command".equals(field.name())) {
        assertThat(field.value()).isEqualTo("<redacted>");
        hasRedacted = true;
      }
      if ("commandLength".equals(field.name())) {
        assertThat(field.value()).isEqualTo(14);
        hasLength = true;
      }
    }
    assertThat(hasRedacted).isTrue();
    assertThat(hasLength).isTrue();
  }

  @Test
  void exportRecipeLockActionDoesNotInvokeShell() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.EXPORT_RECIPE_LOCK, Path.of("/tmp/project"), "");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "", diagnostics);
    assertThat(shellInvocations).isEmpty();
  }

  @Test
  void openIdeWithNullIdeCommandUsesResolvedDefault() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_IDE, Path.of("/tmp/project"), "", null, null);
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    executor.execute(summary, "", diagnostics);

    assertThat(shellInvocations).hasSize(1);
    String expectedIdeCommand = PostTuiActionExecutor.resolveIdeCommand();
    assertThat(shellInvocations.get(0).invocation()).anyMatch(s -> s.contains(expectedIdeCommand));
  }

  @Test
  void openTerminalPrintsNextCommandInHandoffWhenPresent() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_TERMINAL, Path.of("/tmp/project"), "./mvnw quarkus:dev");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      executor.execute(summary, "", diagnostics);
    } finally {
      System.setOut(originalOut);
    }
    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("./mvnw quarkus:dev");
  }

  @Test
  void openTerminalHandoffOmitsBlankNextCommand() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.OPEN_TERMINAL, Path.of("/tmp/project"), "   ");
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    try {
      System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
      executor.execute(summary, "", diagnostics);
    } finally {
      System.setOut(originalOut);
    }
    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("Terminal handoff:");
    assertThat(output).contains("cd \"");
    long indentedLines = output.lines().filter(line -> line.startsWith("  ")).count();
    assertThat(indentedLines).isEqualTo(1);
  }

  @Test
  void publishGithubWithBothToolsAvailableExecutesCommand() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.PUBLISH_GITHUB,
            Path.of("/tmp/project"),
            "",
            GitHubVisibility.PRIVATE,
            null);
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    PostTuiActionExecutor deterministicExecutor =
        new PostTuiActionExecutor(capturingExecutor, command -> true);
    deterministicExecutor.execute(summary, "", diagnostics);

    assertThat(shellInvocations).hasSize(1);
    assertThat(shellInvocations.get(0).invocation()).anyMatch(s -> s.contains("gh repo create"));
  }

  @Test
  void publishGithubWithNullVisibilityDefaultsToPrivate() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.PUBLISH_GITHUB, Path.of("/tmp/project"), "", null, null);
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    PostTuiActionExecutor deterministicExecutor =
        new PostTuiActionExecutor(capturingExecutor, command -> true);
    deterministicExecutor.execute(summary, "", diagnostics);

    assertThat(shellInvocations).hasSize(1);
    assertThat(shellInvocations.get(0).invocation()).anyMatch(s -> s.contains("--private"));
  }

  @Test
  void publishGithubWithExplicitPublicVisibility() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.PUBLISH_GITHUB,
            Path.of("/tmp/project"),
            "",
            GitHubVisibility.PUBLIC,
            null);
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    PostTuiActionExecutor deterministicExecutor =
        new PostTuiActionExecutor(capturingExecutor, command -> true);
    deterministicExecutor.execute(summary, "", diagnostics);

    assertThat(shellInvocations).hasSize(1);
    assertThat(shellInvocations.get(0).invocation()).anyMatch(s -> s.contains("--public"));
  }

  @Test
  void publishGithubWithMissingToolsDoesNotExecuteCommand() {
    PostGenerationExitPlan plan =
        new PostGenerationExitPlan(
            PostGenerationExitAction.PUBLISH_GITHUB,
            Path.of("/tmp/project"),
            "",
            GitHubVisibility.PRIVATE,
            null);
    TuiSessionSummary summary = new TuiSessionSummary(dummyRequest(), plan);
    PostTuiActionExecutor deterministicExecutor =
        new PostTuiActionExecutor(capturingExecutor, command -> false);

    deterministicExecutor.execute(summary, "", diagnostics);

    assertThat(shellInvocations).isEmpty();
  }

  private static ProjectRequest dummyRequest() {
    return new ProjectRequest(
        "org.acme", "test-project", "1.0.0", "org.acme", ".", "", "maven", "25");
  }

  private record ShellInvocation(List<String> invocation, Path directory) {}
}
