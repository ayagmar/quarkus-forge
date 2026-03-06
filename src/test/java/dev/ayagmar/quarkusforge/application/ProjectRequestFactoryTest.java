package dev.ayagmar.quarkusforge.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectRequestFactoryTest {
  private static final MetadataDto METADATA =
      new MetadataDto(
          List.of("21", "25"),
          List.of("maven", "gradle"),
          Map.of("maven", List.of("21", "25"), "gradle", List.of("21", "25")),
          List.of(
              new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"))));

  @Test
  void fromCliPrefillCreatesProjectRequest() {
    CliPrefill prefill =
        new CliPrefill("com.example", "demo", "2.0.0", null, "", "", "gradle", "21");

    ProjectRequest request = ProjectRequestFactory.fromCliPrefill(prefill);

    assertThat(request.groupId()).isEqualTo("com.example");
    assertThat(request.artifactId()).isEqualTo("demo");
    assertThat(request.version()).isEqualTo("2.0.0");
    assertThat(request.buildTool()).isEqualTo("gradle");
    assertThat(request.javaVersion()).isEqualTo("21");
  }

  @Test
  void buildInitialStateValidatesRequest() {
    ProjectRequest valid =
        new ProjectRequest(
            "com.example",
            "demo",
            "1.0.0",
            "com.example.demo",
            ".",
            "io.quarkus.platform:3.31",
            "maven",
            "21");
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = ProjectRequestFactory.buildInitialState(valid, ctx);

    assertThat(state.canSubmit()).isTrue();
    assertThat(state.validation().errors()).isEmpty();
  }

  @Test
  void buildInitialStateDetectsInvalidBuildTool() {
    ProjectRequest invalid =
        new ProjectRequest(
            "com.example",
            "demo",
            "1.0.0",
            "com.example.demo",
            ".",
            "io.quarkus.platform:3.31",
            "ant",
            "21");
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = ProjectRequestFactory.buildInitialState(invalid, ctx);

    assertThat(state.canSubmit()).isFalse();
    assertThat(state.validation().errors())
        .anyMatch(e -> e.field().equals("buildTool") && e.message().contains("ant"));
  }

  @Test
  void applyRecommendedPlatformStreamSetsStreamWhenBlank() {
    ProjectRequest request =
        new ProjectRequest("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(METADATA);

    ProjectRequest result = ProjectRequestFactory.applyRecommendedPlatformStream(request, ctx);

    assertThat(result.platformStream()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void applyRecommendedPlatformStreamPreservesExplicitStream() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "demo", "1.0.0", "", ".", "io.quarkus.platform:3.15", "maven", "21");
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(METADATA);

    ProjectRequest result = ProjectRequestFactory.applyRecommendedPlatformStream(request, ctx);

    assertThat(result.platformStream()).isEqualTo("io.quarkus.platform:3.15");
  }

  @Test
  void applyRecommendedPlatformStreamHandlesLoadError() {
    ProjectRequest request =
        new ProjectRequest("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.failure("network error");

    ProjectRequest result = ProjectRequestFactory.applyRecommendedPlatformStream(request, ctx);

    assertThat(result.platformStream()).isEmpty();
  }

  @Test
  void normalizePresetNameTrimsAndLowercases() {
    assertThat(ProjectRequestFactory.normalizePresetName("  Web  ")).isEqualTo("web");
    assertThat(ProjectRequestFactory.normalizePresetName("DATA")).isEqualTo("data");
    assertThat(ProjectRequestFactory.normalizePresetName(null)).isEmpty();
    assertThat(ProjectRequestFactory.normalizePresetName("")).isEmpty();
  }

  @Test
  void applyRecommendedPlatformStreamShortCircuitsOnLoadError() {
    ProjectRequest request =
        new ProjectRequest("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    // Failure context should short-circuit recommendation logic
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.failure("timeout");

    ProjectRequest result = ProjectRequestFactory.applyRecommendedPlatformStream(request, ctx);

    // Request remains unchanged because load error stops recommendation
    assertThat(result.platformStream()).isEmpty();
  }

  @Test
  void fromCliPrefillAppliesPackageNameOutputDirectoryAndPlatformStream() {
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

    ProjectRequest request = ProjectRequestFactory.fromCliPrefill(prefill);

    assertThat(request.packageName()).isEqualTo("io.acme.svc");
    assertThat(request.outputDirectory()).isEqualTo("/projects");
    assertThat(request.platformStream()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void buildInitialStateDetectsIncompatibleJavaVersion() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "demo",
            "1.0.0",
            "com.example.demo",
            ".",
            "io.quarkus.platform:3.31",
            "maven",
            "11");
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(METADATA);

    ForgeUiState state = ProjectRequestFactory.buildInitialState(request, ctx);

    assertThat(state.canSubmit()).isFalse();
    assertThat(state.validation().errors()).anyMatch(e -> e.field().equals("javaVersion"));
  }
}
