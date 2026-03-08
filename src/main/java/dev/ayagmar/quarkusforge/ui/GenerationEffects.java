package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

final class GenerationEffects {
  private final GenerationFlowCoordinator generationFlowCoordinator;
  private final GenerationStateTracker generationStateTracker;
  private final ProjectGenerationRunner projectGenerationRunner;

  GenerationEffects(
      GenerationFlowCoordinator generationFlowCoordinator,
      GenerationStateTracker generationStateTracker,
      ProjectGenerationRunner projectGenerationRunner) {
    this.generationFlowCoordinator = Objects.requireNonNull(generationFlowCoordinator);
    this.generationStateTracker = Objects.requireNonNull(generationStateTracker);
    this.projectGenerationRunner = Objects.requireNonNull(projectGenerationRunner);
  }

  void prepareForGeneration() {
    generationStateTracker.resetAfterTerminalOutcome();
  }

  void startGeneration(
      ProjectRequest request,
      List<String> selectedExtensionIds,
      Path outputDirectory,
      GenerationFlowCallbacks callbacks) {
    generationFlowCoordinator.startFlow(
        projectGenerationRunner,
        toGenerationRequest(request, selectedExtensionIds),
        outputDirectory,
        callbacks);
  }

  boolean transitionGenerationState(CoreTuiController.GenerationState targetState) {
    return generationStateTracker.transitionTo(targetState);
  }

  void requestCancellation(GenerationFlowCallbacks callbacks) {
    generationFlowCoordinator.requestCancellation(callbacks);
  }

  private static GenerationRequest toGenerationRequest(
      ProjectRequest request, List<String> selectedExtensionIds) {
    return new GenerationRequest(
        request.groupId(),
        request.artifactId(),
        request.version(),
        request.platformStream(),
        request.buildTool(),
        request.javaVersion(),
        selectedExtensionIds);
  }
}
