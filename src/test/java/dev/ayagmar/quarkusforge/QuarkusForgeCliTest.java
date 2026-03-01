package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.diagnostics.DiagnosticField;
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
  void backendPreferenceFallsBackToJlineWhenJvmNativeAccessIsDisabled() {
    String preference = QuarkusForgeCli.defaultBackendPreference(false, false);
    assertThat(preference).isEqualTo("jline3");
  }

  @Test
  void backendPreferenceKeepsPanamaFirstWhenNativeAccessIsEnabled() {
    String preference = QuarkusForgeCli.defaultBackendPreference(false, true);
    assertThat(preference).isEqualTo("panama,jline3");
  }

  @Test
  void backendPreferenceKeepsPanamaFirstInNativeImageRuntime() {
    String preference = QuarkusForgeCli.defaultBackendPreference(true, false);
    assertThat(preference).isEqualTo("panama,jline3");
  }

  @Test
  void postHookDiagnosticsRedactsRawCommand() {
    String command = "QUARKUS_TOKEN=secret ./deploy.sh";
    var fields = QuarkusForgeCli.postHookDiagnosticFields(Path.of("/tmp/project"), command);
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
    assertThat(QuarkusForgeCli.isCommandAvailable(missing)).isFalse();
  }
}
