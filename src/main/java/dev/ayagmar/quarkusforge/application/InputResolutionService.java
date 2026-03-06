package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectInputDefaults;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.forge.Forgefile;

/**
 * Shared pure pipeline for resolving CLI and headless inputs into the validated initial request
 * state used by the rest of the application.
 */
public final class InputResolutionService {
  private static final ProjectRequestValidator PROJECT_REQUEST_VALIDATOR =
      new ProjectRequestValidator();

  private InputResolutionService() {}

  public static ForgeUiState resolveInitialState(
      CliPrefill prefill, MetadataCompatibilityContext metadataCompatibility) {
    ProjectRequest request = CliPrefillMapper.map(prefill);
    return resolveInitialState(request, metadataCompatibility);
  }

  public static ForgeUiState resolveInitialState(
      Forgefile forgefile, MetadataCompatibilityContext metadataCompatibility) {
    CliPrefill prefill =
        forgefile == null
            ? new CliPrefill(
                ProjectInputDefaults.GROUP_ID,
                ProjectInputDefaults.ARTIFACT_ID,
                ProjectInputDefaults.VERSION,
                null,
                ProjectInputDefaults.OUTPUT_DIRECTORY,
                ProjectInputDefaults.PLATFORM_STREAM,
                ProjectInputDefaults.BUILD_TOOL,
                ProjectInputDefaults.JAVA_VERSION)
            : new CliPrefill(
                valueOrDefault(forgefile.groupId(), ProjectInputDefaults.GROUP_ID),
                valueOrDefault(forgefile.artifactId(), ProjectInputDefaults.ARTIFACT_ID),
                valueOrDefault(forgefile.version(), ProjectInputDefaults.VERSION),
                blankToNull(forgefile.packageName()),
                valueOrDefault(forgefile.outputDirectory(), ProjectInputDefaults.OUTPUT_DIRECTORY),
                valueOrDefault(forgefile.platformStream(), ProjectInputDefaults.PLATFORM_STREAM),
                valueOrDefault(forgefile.buildTool(), ProjectInputDefaults.BUILD_TOOL),
                valueOrDefault(forgefile.javaVersion(), ProjectInputDefaults.JAVA_VERSION));
    return resolveInitialState(prefill, metadataCompatibility);
  }

  private static ForgeUiState resolveInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    ProjectRequest resolvedRequest = applyRecommendedPlatformStream(request, metadataCompatibility);
    ValidationReport fieldValidation = PROJECT_REQUEST_VALIDATOR.validate(resolvedRequest);
    ValidationReport compatibilityValidation = metadataCompatibility.validate(resolvedRequest);
    return new ForgeUiState(
        resolvedRequest, fieldValidation.merge(compatibilityValidation), metadataCompatibility);
  }

  private static ProjectRequest applyRecommendedPlatformStream(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    if (!request.platformStream().isBlank()) {
      return request;
    }
    if (metadataCompatibility.loadError() != null) {
      return request;
    }
    MetadataDto metadata = metadataCompatibility.metadataSnapshot();
    String recommendedStream = metadata.recommendedPlatformStreamKey();
    if (recommendedStream.isBlank()) {
      return request;
    }
    return new ProjectRequest(
        request.groupId(),
        request.artifactId(),
        request.version(),
        request.packageName(),
        request.outputDirectory(),
        recommendedStream,
        request.buildTool(),
        request.javaVersion());
  }

  private static String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
