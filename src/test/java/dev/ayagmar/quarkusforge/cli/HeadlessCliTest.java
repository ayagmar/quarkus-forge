package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HeadlessCliTest {
  @TempDir Path tempDir;

  @Test
  void noSubcommandPrintsUsageAndExitsOk() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("https://unused"));
    CliCommandTestSupport.CommandResult result = CliCommandTestSupport.runHeadlessCommand(config);

    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).contains("Usage:");
  }

  @Test
  void helpFlagExitsCleanly() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("https://unused"));
    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(config, "--help");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).contains("generate");
    assertThat(result.standardOut()).contains("headless");
  }

  @Test
  void versionFlagExitsCleanly() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("https://unused"));
    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(config, "--version");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).containsPattern("\\d+\\.\\d+");
  }

  @Test
  void generateHelpFlagExitsCleanly() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("https://unused"));
    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(config, "generate", "--help");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).contains("--artifact-id").contains("--group-id");
  }

  @Test
  void unknownOptionReturnsUsageError() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("https://unused"));
    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(config, "--nonexistent-flag");

    assertThat(result.exitCode()).isNotEqualTo(ExitCodes.OK);
  }
}
