package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
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
  void fromOptionsCreatesProjectRequest() {
    RequestOptions options = new RequestOptions();
    options.groupId = "com.example";
    options.artifactId = "demo";
    options.version = "2.0.0";
    options.buildTool = "gradle";
    options.javaVersion = "21";

    ProjectRequest request = ProjectRequestFactory.fromOptions(options);

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
  void mapFailureToExitCodeCategorizes() {
    assertThat(
            ProjectRequestFactory.mapFailureToExitCode(
                new java.util.concurrent.CancellationException()))
        .isEqualTo(ExitCodes.CANCELLED);

    assertThat(
            ProjectRequestFactory.mapFailureToExitCode(
                new dev.ayagmar.quarkusforge.api.ApiClientException("fail", null)))
        .isEqualTo(ExitCodes.NETWORK);

    assertThat(
            ProjectRequestFactory.mapFailureToExitCode(
                new dev.ayagmar.quarkusforge.archive.ArchiveException("fail")))
        .isEqualTo(ExitCodes.ARCHIVE);

    assertThat(ProjectRequestFactory.mapFailureToExitCode(new RuntimeException("unknown")))
        .isEqualTo(ExitCodes.INTERNAL);
  }

  @Test
  void applyRecommendedPlatformStreamHandlesNullMetadataSnapshot() {
    ProjectRequest request =
        new ProjectRequest("com.example", "demo", "1.0.0", "", ".", "", "maven", "21");
    // Create a context with failure that has null metadata snapshot
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.failure("timeout");

    ProjectRequest result = ProjectRequestFactory.applyRecommendedPlatformStream(request, ctx);

    // Should return request unchanged since loadError stops it
    assertThat(result.platformStream()).isEmpty();
  }

  @Test
  void fromOptionsAppliesAllFieldsIncludingPackageNameAndOutputStream() {
    RequestOptions options = new RequestOptions();
    options.groupId = "io.acme";
    options.artifactId = "svc";
    options.version = "3.0.0";
    options.packageName = "io.acme.svc";
    options.outputDirectory = "/projects";
    options.platformStream = "io.quarkus.platform:3.31";
    options.buildTool = "gradle";
    options.javaVersion = "25";

    ProjectRequest request = ProjectRequestFactory.fromOptions(options);

    assertThat(request.packageName()).isEqualTo("io.acme.svc");
    assertThat(request.outputDirectory()).isEqualTo("/projects");
    assertThat(request.platformStream()).isEqualTo("io.quarkus.platform:3.31");
  }

  @Test
  void mapFailureToExitCodeUnwrapsCompletionException() {
    // Wrap in CompletionException to test the unwrapper
    assertThat(
            ProjectRequestFactory.mapFailureToExitCode(
                new java.util.concurrent.CompletionException(
                    new dev.ayagmar.quarkusforge.api.ApiClientException("wrapped", null))))
        .isEqualTo(ExitCodes.NETWORK);
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
