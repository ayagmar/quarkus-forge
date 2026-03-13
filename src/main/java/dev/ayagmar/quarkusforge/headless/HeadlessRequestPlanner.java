package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.application.InputResolutionService;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

final class HeadlessRequestPlanner {
  private final HeadlessExtensionResolutionService extensionResolutionService;
  private final HeadlessForgefilePersistenceService forgefilePersistenceService;

  HeadlessRequestPlanner(
      HeadlessExtensionResolutionService extensionResolutionService,
      HeadlessForgefilePersistenceService forgefilePersistenceService) {
    this.extensionResolutionService = Objects.requireNonNull(extensionResolutionService);
    this.forgefilePersistenceService = Objects.requireNonNull(forgefilePersistenceService);
  }

  HeadlessGenerationPlan plan(
      HeadlessGenerationInputs inputs, CatalogData catalogData, Duration catalogTimeout)
      throws Exception {
    ForgeUiState validatedState = resolveValidatedState(inputs, catalogData);
    if (!validatedState.canSubmit()) {
      throw new ValidationException(validatedState.validation().errors());
    }

    List<String> extensionIds =
        extensionResolutionService.resolveExtensionIds(
            validatedState.request().platformStream(),
            inputs.extensionInputs(),
            inputs.presetInputs(),
            HeadlessExtensionResolutionService.knownExtensionIds(catalogData),
            catalogTimeout);
    if (inputs.lockCheck()) {
      forgefilePersistenceService.validateLockDrift(inputs, validatedState.request(), extensionIds);
    }
    return new HeadlessGenerationPlan(validatedState.request(), extensionIds);
  }

  private static ForgeUiState resolveValidatedState(
      HeadlessGenerationInputs inputs, CatalogData catalogData) {
    MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.success(catalogData.metadata());
    return InputResolutionService.resolveInitialState(inputs.template(), metadataCompatibility);
  }

  record HeadlessGenerationPlan(ProjectRequest request, List<String> extensionIds) {
    HeadlessGenerationPlan {
      Objects.requireNonNull(request);
      extensionIds = List.copyOf(extensionIds);
    }
  }
}
