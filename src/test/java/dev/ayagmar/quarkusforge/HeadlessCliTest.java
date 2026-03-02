package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HeadlessCliTest {
  @TempDir Path tempDir;

  @Test
  void noSubcommandPrintsUsageAndExitsWithValidation() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("http://unused"));
    CliCommandTestSupport.CommandResult result = CliCommandTestSupport.runHeadlessCommand(config);

    assertThat(result.exitCode()).isEqualTo(ExitCodes.VALIDATION);
    assertThat(result.standardError()).contains("No subcommand specified");
  }

  @Test
  void helpFlagExitsCleanly() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("http://unused"));
    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(config, "--help");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
    assertThat(result.standardOut()).contains("generate");
    assertThat(result.standardOut()).contains("headless");
  }

  @Test
  void versionFlagExitsCleanly() {
    RuntimeConfig config =
        CliCommandTestSupport.runtimeConfig(tempDir, URI.create("http://unused"));
    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.runHeadlessCommand(config, "--version");

    assertThat(result.exitCode()).isEqualTo(ExitCodes.OK);
  }
}
