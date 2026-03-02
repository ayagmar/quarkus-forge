package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
import dev.ayagmar.quarkusforge.ui.GitHubVisibility;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuarkusForgeCliTest {
  @Test
  void helpCommandReturnsSuccessExitCode() {
    int exitCode = QuarkusForgeCli.runWithArgs(new String[] {"--help"});
    assertThat(exitCode).isZero();
  }

  @Test
  void versionCommandUsesResolvedBuildVersion() {
    PrintStream originalOut = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
      int exitCode = QuarkusForgeCli.runWithArgs(new String[] {"--version"});
      assertThat(exitCode).isZero();
    } finally {
      System.setOut(originalOut);
    }

    String versionOutput = output.toString(StandardCharsets.UTF_8).trim();
    assertThat(versionOutput).contains(CliVersionProvider.resolveVersion());
    assertThat(versionOutput).doesNotContain("0.1.0-SNAPSHOT");
  }

  @Test
  void dryRunWithValidPrefillReturnsSuccess() {
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {
              "--dry-run",
              "--group-id",
              "com.example",
              "--artifact-id",
              "forge-app",
              "--output-dir",
              "./tmp/output"
            });

    assertThat(exitCode).isZero();
  }

  @Test
  void dryRunWithNumericArtifactReturnsUsageCode() {
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {
              "--dry-run",
              "--group-id",
              "com.example",
              "--artifact-id",
              "123app",
              "--output-dir",
              "./tmp/output"
            });

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void dryRunWithInvalidPrefillReturnsUsageCode() {
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {
              "--dry-run", "--group-id", "1bad", "--artifact-id", "forge-app", "--output-dir", "CON"
            });

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void dryRunBlocksUnsupportedMetadataCombination() {
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {
              "--dry-run",
              "--group-id",
              "com.example",
              "--artifact-id",
              "forge-app",
              "--build-tool",
              "gradle",
              "--java-version",
              "11",
              "--output-dir",
              "./tmp/output"
            });

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void startupValidationBlockingAppliesOnlyToDryRunMode() {
    assertThat(QuarkusForgeCli.shouldBlockOnStartupValidation(true)).isTrue();
    assertThat(QuarkusForgeCli.shouldBlockOnStartupValidation(false)).isFalse();
  }

  @Test
  void backendPreferenceUsesPanamaOnly() {
    String preference = TuiBootstrapService.defaultBackendPreference();
    assertThat(preference).isEqualTo("panama");
  }

  @Test
  void postHookDiagnosticsRedactsRawCommand() {
    String command = "QUARKUS_TOKEN=secret ./deploy.sh";
    var fields = PostTuiActionExecutor.postHookDiagnosticFields(Path.of("/tmp/project"), command);
    var valuesByName =
        java.util.Arrays.stream(fields)
            .collect(
                java.util.stream.Collectors.toMap(DiagnosticField::name, DiagnosticField::value));

    assertThat(valuesByName).containsEntry("directory", Path.of("/tmp/project").toString());
    assertThat(valuesByName).containsEntry("command", "<redacted>");
    assertThat(valuesByName).containsEntry("commandLength", command.length());
    assertThat(valuesByName).doesNotContainValue(command);
  }

  @Test
  void shellCommandInvocationUsesPosixShellForNonWindows() {
    assertThat(ShellExecutor.commandInvocation("echo ok", false))
        .containsExactly("sh", "-lc", "echo ok");
  }

  @Test
  void shellCommandInvocationUsesCmdForWindows() {
    assertThat(ShellExecutor.commandInvocation("echo ok", true))
        .containsExactly("cmd.exe", "/c", "echo ok");
  }

  @Test
  void isCommandAvailableReturnsFalseForMissingBinary() {
    String missing = "missing-" + UUID.randomUUID();
    assertThat(CommandUtils.isCommandAvailable(missing)).isFalse();
  }

  @Test
  void isCommandAvailableIgnoresInvalidPathEntries() {
    String missing = "missing-" + UUID.randomUUID();
    String pathWithInvalidEntry = "\0invalid";

    assertThat(CommandUtils.isCommandAvailable(missing, pathWithInvalidEntry)).isFalse();
  }

  @Test
  void githubPublishCommandIncludesVisibilityFlag() {
    assertThat(PostTuiActionExecutor.githubPublishCommand(GitHubVisibility.PRIVATE))
        .contains("git init")
        .contains("git add .")
        .contains("git commit")
        .endsWith("gh repo create --source . --push --private");
    assertThat(PostTuiActionExecutor.githubPublishCommand(GitHubVisibility.PUBLIC))
        .endsWith("gh repo create --source . --push --public");
    assertThat(PostTuiActionExecutor.githubPublishCommand(GitHubVisibility.INTERNAL))
        .endsWith("gh repo create --source . --push --internal");
  }
}
