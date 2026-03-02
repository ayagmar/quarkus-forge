package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
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

  private static ProjectRequest dummyRequest() {
    return new ProjectRequest(
        "org.acme", "test-project", "1.0.0", "org.acme", ".", "", "maven", "25");
  }

  private record ShellInvocation(List<String> invocation, Path directory) {}
}
