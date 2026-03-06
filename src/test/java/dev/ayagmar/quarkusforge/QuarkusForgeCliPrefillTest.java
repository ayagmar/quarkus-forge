package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * Tests verifying that {@link QuarkusForgeCli} correctly detects explicitly-provided CLI options
 * and does not allow stored prefill values to override options the user intentionally passed —
 * including options whose value happens to equal the compiled-in default.
 *
 * <p>These tests exercise the fix for the {@code applyIfDefault} value-equality bug, where {@code
 * --group-id org.acme} (the default) would silently get replaced by an older stored prefill value.
 */
class QuarkusForgeCliPrefillTest {

  @TempDir Path tempDir;

  // ── isExplicitlySet with real Picocli @Spec injection ─────────────────────

  @Test
  void picocliSpecDetectsOptionThatWasExplicitlyProvided() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run", "--group-id", "com.example");

    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    RequestOptions.OPT_GROUP_ID, "com.example", RequestOptions.DEFAULT_GROUP_ID))
        .as("--group-id was explicitly provided — should be detected as explicit")
        .isTrue();
  }

  @Test
  void picocliSpecDetectsOptionThatWasNotProvided() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run");

    // --group-id was NOT provided; value equals the default
    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    RequestOptions.OPT_GROUP_ID,
                    RequestOptions.DEFAULT_GROUP_ID,
                    RequestOptions.DEFAULT_GROUP_ID))
        .as("--group-id was not passed — should be detected as default")
        .isFalse();
  }

  @Test
  void picocliSpecDetectsExplicitDefaultEqualValue() {
    // This is the core bug scenario: user passes --group-id org.acme (same as default)
    // and the system must recognise it as explicit input, NOT as "not provided".
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run", "--group-id", RequestOptions.DEFAULT_GROUP_ID);

    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    RequestOptions.OPT_GROUP_ID,
                    RequestOptions.DEFAULT_GROUP_ID,
                    RequestOptions.DEFAULT_GROUP_ID))
        .as("--group-id org.acme is explicit even though it equals the default")
        .isTrue();
  }

  @Test
  void picocliSpecDetectsMultipleExplicitOptions() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli)
        .parseArgs("--dry-run", "--group-id", "com.example", "--java-version", "21");

    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    RequestOptions.OPT_GROUP_ID, "com.example", RequestOptions.DEFAULT_GROUP_ID))
        .isTrue();
    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    RequestOptions.OPT_JAVA_VERSION, "21", RequestOptions.DEFAULT_JAVA_VERSION))
        .isTrue();
    // unprovided option should be false
    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    RequestOptions.OPT_BUILD_TOOL,
                    RequestOptions.DEFAULT_BUILD_TOOL,
                    RequestOptions.DEFAULT_BUILD_TOOL))
        .isFalse();
  }

  // ── applyStoredRequestDefaults: stored prefill NOT applied when option was explicitly passed ──

  @Test
  void applyStoredDefaultsDoesNotOverrideExplicitGroupId() {
    // Simulate: user passes --group-id org.acme (equals the default) explicitly.
    // Stored prefill has a DIFFERENT group-id (org.old-company).
    // After applying stored defaults, group-id must remain org.acme — NOT org.old-company.
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run", "--group-id", RequestOptions.DEFAULT_GROUP_ID);

    CliPrefill storedPrefill =
        new CliPrefill("org.old-company", "old-app", "2.0.0", null, ".", "", "maven", "21");

    QuarkusForgeCli.applyStoredRequestDefaults(cli.requestOptions(), storedPrefill);

    assertThat(cli.requestOptions().groupId)
        .as(
            "Explicit --group-id (even when it equals the default) must not be replaced"
                + " by stored prefill")
        .isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
  }

  @Test
  void applyStoredDefaultsAppliesStoredValueWhenOptionWasNotExplicit() {
    // Simulate: user omits --group-id; stored prefill has a non-default group-id.
    // After applying stored defaults, group-id should be the stored value.
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run"); // --group-id not passed

    CliPrefill storedPrefill =
        new CliPrefill("org.stored-company", "stored-app", "3.0.0", null, ".", "", "gradle", "21");

    QuarkusForgeCli.applyStoredRequestDefaults(cli.requestOptions(), storedPrefill);

    assertThat(cli.requestOptions().groupId)
        .as("When --group-id was not provided, stored prefill should be applied")
        .isEqualTo("org.stored-company");
    assertThat(cli.requestOptions().buildTool).isEqualTo("gradle");
    assertThat(cli.requestOptions().javaVersion).isEqualTo("21");
  }

  @Test
  void applyStoredDefaultsIgnoresBlankStoredValues() {
    // Blank stored values must not overwrite the Picocli default.
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run");

    CliPrefill storedPrefillWithBlanks =
        new CliPrefill("", "  ", null, null, ".", "", "maven", "25");

    QuarkusForgeCli.applyStoredRequestDefaults(cli.requestOptions(), storedPrefillWithBlanks);

    assertThat(cli.requestOptions().groupId).isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
    assertThat(cli.requestOptions().artifactId).isEqualTo(RequestOptions.DEFAULT_ARTIFACT_ID);
  }

  @Test
  void applyStoredDefaultsDoesNothingWhenStoredPrefillIsNull() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    new CommandLine(cli).parseArgs("--dry-run");

    QuarkusForgeCli.applyStoredRequestDefaults(cli.requestOptions(), null);

    assertThat(cli.requestOptions().groupId).isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
  }

  // ── End-to-end dry-run validation ──────────────────────────────────────────

  @Test
  void dryRunSucceedsWhenAllRequiredFieldsExplicitlyProvided() {
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {
              "--dry-run",
              "--group-id",
              "com.example",
              "--artifact-id",
              "my-service",
              "--project-version",
              "0.1.0",
              "--java-version",
              "21",
              "--build-tool",
              "maven",
              "--output-dir",
              "."
            });

    assertThat(exitCode).isZero();
  }

  @Test
  void dryRunSucceedsWithPreferencesFilePresent() throws Exception {
    Path prefsFile = tempDir.resolve("preferences.json");
    Files.writeString(
        prefsFile,
        """
        {
          "schemaVersion": 1,
          "groupId": "org.old-company",
          "artifactId": "old-app",
          "version": "2.0.0",
          "packageName": null,
          "outputDirectory": ".",
          "platformStream": "",
          "buildTool": "maven",
          "javaVersion": "25"
        }
        """);

    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://localhost:18080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            prefsFile);

    // --dry-run skips applyStoredRequestDefaults; this verifies that the CLI boots cleanly
    // even when a preferences file is present and the dry-run validation path succeeds.
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {"--dry-run", "--group-id", "org.acme", "--artifact-id", "test-app"},
            runtimeConfig);

    assertThat(exitCode).isZero();
  }
}
