package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests verifying that {@link QuarkusForgeCli} correctly detects explicitly-provided CLI options
 * and preserves that explicitness when routing startup inputs into the application-layer startup
 * service.
 *
 * <p>These tests exercise the explicit-default-equal bug shape, where {@code --group-id org.acme}
 * (the default) must remain explicit so the application-layer startup service can prefer it over
 * stored prefill.
 */
class QuarkusForgeCliPrefillTest {

  @TempDir Path tempDir;

  // ── Explicit-option detection from recorded Picocli parse results ─────────────────────

  @Test
  void picocliSpecDetectsOptionThatWasExplicitlyProvided() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    cli.requestOptions()
        .recordMatchedOptions(
            CliCommandLineFactory.create(cli).parseArgs("--dry-run", "--group-id", "com.example"));

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
    cli.requestOptions()
        .recordMatchedOptions(CliCommandLineFactory.create(cli).parseArgs("--dry-run"));

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
    cli.requestOptions()
        .recordMatchedOptions(
            CliCommandLineFactory.create(cli)
                .parseArgs("--dry-run", "--group-id", RequestOptions.DEFAULT_GROUP_ID));

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
    cli.requestOptions()
        .recordMatchedOptions(
            CliCommandLineFactory.create(cli)
                .parseArgs("--dry-run", "--group-id", "com.example", "--java-version", "21"));

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

  // ── Explicit startup prefill routing ───────────────────────────────────────

  @Test
  void explicitCliPrefillRetainsExplicitDefaultEqualGroupId() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    cli.requestOptions()
        .recordMatchedOptions(
            CliCommandLineFactory.create(cli)
                .parseArgs("--dry-run", "--group-id", RequestOptions.DEFAULT_GROUP_ID));

    assertThat(cli.requestOptions().toExplicitCliPrefill().groupId())
        .as("Explicit --group-id must remain explicit even when it equals the default")
        .isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
  }

  @Test
  void explicitCliPrefillLeavesOmittedDefaultsUnset() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    cli.requestOptions()
        .recordMatchedOptions(CliCommandLineFactory.create(cli).parseArgs("--dry-run"));

    CliPrefill explicitPrefill = cli.requestOptions().toExplicitCliPrefill();

    assertThat(explicitPrefill.groupId()).isNull();
    assertThat(explicitPrefill.artifactId()).isNull();
    assertThat(explicitPrefill.buildTool()).isNull();
    assertThat(explicitPrefill.javaVersion()).isNull();
  }

  @Test
  void explicitCliPrefillPreservesMultipleExplicitOptions() {
    QuarkusForgeCli cli = new QuarkusForgeCli();
    cli.requestOptions()
        .recordMatchedOptions(
            CliCommandLineFactory.create(cli)
                .parseArgs("--dry-run", "--group-id", "com.example", "--java-version", "21"));

    CliPrefill explicitPrefill = cli.requestOptions().toExplicitCliPrefill();

    assertThat(explicitPrefill.groupId()).isEqualTo("com.example");
    assertThat(explicitPrefill.javaVersion()).isEqualTo("21");
    assertThat(explicitPrefill.buildTool()).isNull();
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

    // --dry-run passes null stored prefill into the startup service; this verifies that the CLI
    // still boots cleanly even when a preferences file is present.
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {"--dry-run", "--group-id", "org.acme", "--artifact-id", "test-app"},
            runtimeConfig);

    assertThat(exitCode).isZero();
  }
}
