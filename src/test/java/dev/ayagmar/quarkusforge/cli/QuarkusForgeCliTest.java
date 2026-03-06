package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
  void applyStoredRequestDefaultsWithNullStoredPrefillDoesNothing() {
    RequestOptions options = new RequestOptions();
    String originalGroupId = options.groupId;

    QuarkusForgeCli.applyStoredRequestDefaults(options, null);

    assertThat(options.groupId).isEqualTo(originalGroupId);
  }

  @Test
  void applyStoredRequestDefaultsAppliesStoredValues() {
    RequestOptions options = RequestOptions.defaults();
    CliPrefill stored =
        new CliPrefill(
            "com.stored", "stored-app", "2.0.0", "com.stored.app", "/stored", "", "gradle", "21");

    QuarkusForgeCli.applyStoredRequestDefaults(options, stored);

    // Stored values should apply since current values match defaults
    assertThat(options.groupId).isEqualTo("com.stored");
    assertThat(options.artifactId).isEqualTo("stored-app");
    assertThat(options.version).isEqualTo("2.0.0");
    assertThat(options.buildTool).isEqualTo("gradle");
    assertThat(options.javaVersion).isEqualTo("21");
  }

  @Test
  void applyStoredRequestDefaultsPreservesExplicitValues() {
    RequestOptions options = RequestOptions.defaults();
    options.groupId = "com.explicit"; // explicitly set, different from default

    CliPrefill stored = new CliPrefill("com.stored", "stored-app", "", "", "", "", "", "");

    QuarkusForgeCli.applyStoredRequestDefaults(options, stored);

    // Explicitly set values should not be overridden
    assertThat(options.groupId).isEqualTo("com.explicit");
  }

  @Test
  void applyStoredRequestDefaultsTreatsPreviouslyAppliedStoredValuesAsNonExplicit() {
    RequestOptions options = RequestOptions.defaults();

    QuarkusForgeCli.applyStoredRequestDefaults(
        options, new CliPrefill("com.first", "first-app", "", "", "", "", "", ""));
    QuarkusForgeCli.applyStoredRequestDefaults(
        options, new CliPrefill("com.second", "second-app", "", "", "", "", "", ""));

    assertThat(options.groupId).isEqualTo("com.second");
    assertThat(options.artifactId).isEqualTo("second-app");
  }
}
