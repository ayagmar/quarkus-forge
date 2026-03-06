package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.Locale;

/**
 * Pure domain logic for building and transforming {@link ProjectRequest} instances. No CLI or TUI
 * dependencies.
 */
public final class ProjectRequestFactory {
  private ProjectRequestFactory() {}

  public static ProjectRequest fromCliPrefill(CliPrefill prefill) {
    return CliPrefillMapper.map(prefill);
  }

  public static ForgeUiState buildInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    ValidationReport fieldValidation = new ProjectRequestValidator().validate(request);
    ValidationReport compatibilityValidation = metadataCompatibility.validate(request);
    return new ForgeUiState(
        request, fieldValidation.merge(compatibilityValidation), metadataCompatibility);
  }

  public static ProjectRequest applyRecommendedPlatformStream(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibilityContext) {
    if (!request.platformStream().isBlank()) {
      return request;
    }
    if (metadataCompatibilityContext.loadError() != null) {
      return request;
    }
    MetadataDto metadata = metadataCompatibilityContext.metadataSnapshot();
    if (metadata == null) {
      return request;
    }
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

  public static String normalizePresetName(String presetName) {
    if (presetName == null) {
      return "";
    }
    return presetName.trim().toLowerCase(Locale.ROOT);
  }
}
