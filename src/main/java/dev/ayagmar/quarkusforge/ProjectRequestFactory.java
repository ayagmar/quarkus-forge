package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.ApiClientException;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.archive.ArchiveException;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * Pure domain logic for building and transforming {@link ProjectRequest} instances. No CLI or TUI
 * dependencies.
 */
final class ProjectRequestFactory {
  private ProjectRequestFactory() {}

  static ProjectRequest fromOptions(RequestOptions options) {
    CliPrefill prefill =
        new CliPrefill(
            options.groupId,
            options.artifactId,
            options.version,
            options.packageName,
            options.outputDirectory,
            options.platformStream,
            options.buildTool,
            options.javaVersion);
    return CliPrefillMapper.map(prefill);
  }

  static ForgeUiState buildInitialState(
      ProjectRequest request, MetadataCompatibilityContext metadataCompatibility) {
    ValidationReport fieldValidation = new ProjectRequestValidator().validate(request);
    ValidationReport compatibilityValidation = metadataCompatibility.validate(request);
    return new ForgeUiState(
        request, fieldValidation.merge(compatibilityValidation), metadataCompatibility);
  }

  static ProjectRequest applyRecommendedPlatformStream(
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

  static String normalizePresetName(String presetName) {
    if (presetName == null) {
      return "";
    }
    return presetName.trim().toLowerCase(Locale.ROOT);
  }

  static int mapFailureToExitCode(Throwable throwable) {
    Throwable cause = ThrowableUnwrapper.unwrapAsyncFailure(throwable);
    return switch (cause) {
      case CancellationException ignored -> ExitCodes.CANCELLED;
      case ApiClientException ignored -> ExitCodes.NETWORK;
      case ArchiveException ignored -> ExitCodes.ARCHIVE;
      default -> ExitCodes.INTERNAL;
    };
  }
}
