package dev.ayagmar.quarkusforge.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InputResolutionServiceTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21", "25"),
          List.of("maven", "gradle"),
          Map.of("maven", List.of("21", "25"), "gradle", List.of("21", "25")),
          List.of(
              new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"))));

  @Test
  void resolveInitialStateMapsPrefillIntoRequestFields() {
    CliPrefill prefill =
        new CliPrefill(
            "io.acme",
            "svc",
            "3.0.0",
            "io.acme.svc",
            "/projects",
            "io.quarkus.platform:3.31",
            "gradle",
            "25");
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState(prefill, context);

    assertThat(state.request().groupId()).isEqualTo("io.acme");
    assertThat(state.request().artifactId()).isEqualTo("svc");
    assertThat(state.request().version()).isEqualTo("3.0.0");
    assertThat(state.request().packageName()).isEqualTo("io.acme.svc");
    assertThat(state.request().outputDirectory()).isEqualTo("/projects");
    assertThat(state.request().platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(state.request().buildTool()).isEqualTo("gradle");
    assertThat(state.request().javaVersion()).isEqualTo("25");
  }

  @Test
  void resolveInitialStateAppliesRecommendedPlatformStreamWhenBlank() {
    CliPrefill prefill = new CliPrefill("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState(prefill, context);

    assertThat(state.request().platformStream()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void resolveInitialStatePreservesExplicitPlatformStream() {
    CliPrefill prefill =
        new CliPrefill(
            "com.example", "demo", "1.0.0", "", ".", "io.quarkus.platform:3.15", "maven", "21");
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState(prefill, context);

    assertThat(state.request().platformStream()).isEqualTo("io.quarkus.platform:3.15");
  }

  @Test
  void resolveInitialStateLeavesBlankStreamWhenMetadataLoadFailed() {
    CliPrefill prefill = new CliPrefill("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    MetadataCompatibilityContext context = MetadataCompatibilityContext.failure("timeout");

    ForgeUiState state = InputResolutionService.resolveInitialState(prefill, context);

    assertThat(state.request().platformStream()).isBlank();
  }

  @Test
  void resolveInitialStateAppliesRuntimeDefaultsForOmittedForgefileFields() {
    Forgefile forgefile =
        new Forgefile(null, "demo", null, null, null, null, null, null, null, null);
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState(forgefile, context);

    assertThat(state.request().groupId()).isEqualTo("org.acme");
    assertThat(state.request().artifactId()).isEqualTo("demo");
    assertThat(state.request().version()).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(state.request().outputDirectory()).isEqualTo(".");
    assertThat(state.request().buildTool()).isEqualTo("maven");
    assertThat(state.request().javaVersion()).isEqualTo("25");
    assertThat(state.request().platformStream()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void resolveInitialStateAppliesRuntimeDefaultsWhenForgefileIsMissing() {
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState((Forgefile) null, context);

    assertThat(state.request().groupId()).isEqualTo("org.acme");
    assertThat(state.request().artifactId()).isEqualTo("quarkus-app");
    assertThat(state.request().version()).isEqualTo("1.0.0-SNAPSHOT");
    assertThat(state.request().packageName()).isEqualTo("org.acme.quarkus.app");
    assertThat(state.request().outputDirectory()).isEqualTo(".");
    assertThat(state.request().buildTool()).isEqualTo("maven");
    assertThat(state.request().javaVersion()).isEqualTo("25");
  }

  @Test
  void resolveInitialStateLeavesBlankRecommendedStreamUnchanged() {
    CliPrefill prefill = new CliPrefill("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    MetadataDto metadataWithoutRecommendation =
        new MetadataDto(List.of("21"), List.of("maven"), Map.of("maven", List.of("21")), List.of());
    MetadataCompatibilityContext context =
        MetadataCompatibilityContext.success(metadataWithoutRecommendation);

    ForgeUiState state = InputResolutionService.resolveInitialState(prefill, context);

    assertThat(state.request().platformStream()).isBlank();
  }

  @Test
  void resolveInitialStateTreatsBlankForgefilePackageNameAsUnset() {
    Forgefile forgefile =
        new Forgefile("com.example", "demo", "1.0.0", "   ", ".", "", "maven", "21", null, null);
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState(forgefile, context);

    assertThat(state.request().packageName()).isEqualTo("com.example.demo");
  }

  @Test
  void resolveInitialStateMergesFieldAndCompatibilityValidation() {
    CliPrefill prefill =
        new CliPrefill("com.example", "demo", "1.0.0", "com.example.demo", ".", "", "ant", "11");
    MetadataCompatibilityContext context = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = InputResolutionService.resolveInitialState(prefill, context);

    assertThat(state.canSubmit()).isFalse();
    assertThat(state.validation().errors()).anyMatch(error -> error.field().equals("buildTool"));
    assertThat(state.validation().errors()).anyMatch(error -> error.field().equals("javaVersion"));
  }
}
