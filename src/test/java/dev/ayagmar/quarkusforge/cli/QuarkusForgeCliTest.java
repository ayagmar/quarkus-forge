package dev.ayagmar.quarkusforge.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.application.InputResolutionService;
import dev.ayagmar.quarkusforge.application.StartupMetadataSelection;
import dev.ayagmar.quarkusforge.application.StartupState;
import dev.ayagmar.quarkusforge.application.StartupStateService;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class QuarkusForgeCliTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21", "25"),
          List.of("maven", "gradle"),
          Map.of("maven", List.of("21", "25"), "gradle", List.of("21", "25")),
          List.of(
              new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"))));

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
  void dryRunDelegatesExplicitOverridesToStartupService() throws Exception {
    AtomicReference<CliPrefill> requestedPrefill = new AtomicReference<>();
    AtomicReference<CliPrefill> storedPrefill = new AtomicReference<>();
    StartupStateService startupStateService =
        request -> {
          requestedPrefill.set(request.requestedPrefill());
          storedPrefill.set(request.storedPrefill());
          return validStartupState();
        };
    QuarkusForgeCli cli =
        new QuarkusForgeCli(
            dev.ayagmar.quarkusforge.runtime.RuntimeConfig.defaults(),
            uri -> {
              throw new AssertionError("startup service stub should own startup resolution");
            },
            startupStateService);

    CliCommandTestSupport.CommandResult result =
        CliCommandTestSupport.captureCommandOutput(
            () ->
                CliCommandLineFactory.create(cli)
                    .execute(
                        "--dry-run",
                        "--group-id",
                        RequestOptions.DEFAULT_GROUP_ID,
                        "--artifact-id",
                        "forge-app"));

    assertThat(result.exitCode()).isZero();
    assertThat(requestedPrefill.get().groupId()).isEqualTo(RequestOptions.DEFAULT_GROUP_ID);
    assertThat(requestedPrefill.get().artifactId()).isEqualTo("forge-app");
    assertThat(requestedPrefill.get().version()).isNull();
    assertThat(requestedPrefill.get().buildTool()).isNull();
    assertThat(storedPrefill.get()).isNull();
  }

  private static StartupState validStartupState() {
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(METADATA);
    ForgeUiState initialState =
        InputResolutionService.resolveInitialState(
            new CliPrefill("com.example", "forge-app", "1.0.0", null, ".", "", "maven", "21"),
            metadataCompatibility);
    return new StartupState(
        initialState, new StartupMetadataSelection(metadataCompatibility, "live", ""));
  }
}
