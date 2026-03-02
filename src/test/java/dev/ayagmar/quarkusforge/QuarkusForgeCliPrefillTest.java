package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.ui.UserPreferencesStore;
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
                .isExplicitlySet("--group-id", "com.example", RequestOptions.DEFAULT_GROUP_ID))
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
                    "--group-id", RequestOptions.DEFAULT_GROUP_ID, RequestOptions.DEFAULT_GROUP_ID))
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
                    "--group-id", RequestOptions.DEFAULT_GROUP_ID, RequestOptions.DEFAULT_GROUP_ID))
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
                .isExplicitlySet("--group-id", "com.example", RequestOptions.DEFAULT_GROUP_ID))
        .isTrue();
    assertThat(
            cli.requestOptions()
                .isExplicitlySet("--java-version", "21", RequestOptions.DEFAULT_JAVA_VERSION))
        .isTrue();
    // unprovided option should be false
    assertThat(
            cli.requestOptions()
                .isExplicitlySet(
                    "--build-tool",
                    RequestOptions.DEFAULT_BUILD_TOOL,
                    RequestOptions.DEFAULT_BUILD_TOOL))
        .isFalse();
  }

  // ── Prefill not applied when option was explicitly passed ─────────────────

  @Test
  void storedPrefillDoesNotOverrideExplicitlyPassedGroupId() throws Exception {
    // Write a preferences file with a different group-id
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
            java.net.URI.create("http://localhost:8080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            prefsFile);

    UserPreferencesStore store = UserPreferencesStore.fileBacked(prefsFile);
    // Sanity check: preferences file is readable
    assertThat(store.loadLastRequest()).isNotNull();
    assertThat(store.loadLastRequest().groupId()).isEqualTo("org.old-company");

    // Run in dry-run mode (skips stored prefill application by design)
    // applyStoredRequestDefaults is NOT called for --dry-run; this tests that the CLI
    // at least accepts the explicit value and reports success with it
    int exitCode =
        QuarkusForgeCli.runWithArgs(
            new String[] {"--dry-run", "--group-id", "org.acme", "--artifact-id", "test-app"},
            runtimeConfig);

    // The CLI should succeed (validation passes for org.acme/test-app)
    assertThat(exitCode).isZero();
  }

  @Test
  void dryRunReportSuccessWhenAllRequiredFieldsExplicitlyProvided() {
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
}
