package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Path;
import java.util.List;

/**
 * Immutable UI read-model snapshot used by reducer logic and renderer orchestration.
 *
 * <p>Contains only serializable/view-model state and avoids direct mutable widget ownership.
 */
record UiState(
    ProjectRequest request,
    ValidationReport validation,
    FocusTarget focusTarget,
    String statusMessage,
    String errorMessage,
    String verboseErrorDetails,
    boolean showErrorDetails,
    boolean submitRequested,
    boolean submitBlockedByValidation,
    boolean submitBlockedByTargetConflict,
    int commandPaletteSelection,
    MetadataPanelSnapshot metadataPanel,
    ExtensionsPanelSnapshot extensionsPanel,
    FooterSnapshot footer,
    OverlayState overlays,
    GenerationView generation,
    CatalogLoadView catalogLoad,
    PostGenerationView postGeneration,
    StartupOverlayView startupOverlay,
    ExtensionView extensions) {

  UiState withStatusAndError(String nextStatusMessage, String nextErrorMessage) {
    return withFeedback(nextStatusMessage, nextErrorMessage, verboseErrorDetails, showErrorDetails);
  }

  UiState withStatusMessage(String nextStatusMessage) {
    return withFeedback(nextStatusMessage, errorMessage, verboseErrorDetails, showErrorDetails);
  }

  UiState withRequestAndValidation(ProjectRequest nextRequest, ValidationReport nextValidation) {
    return new UiState(
        nextRequest,
        nextValidation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withFeedback(
      String nextStatusMessage,
      String nextErrorMessage,
      String nextVerboseErrorDetails,
      boolean nextShowErrorDetails) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        nextErrorMessage,
        nextVerboseErrorDetails,
        nextShowErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withFocusAndValidationFeedback(FocusTarget nextFocusTarget, String nextStatusMessage) {
    return new UiState(
        request,
        validation,
        nextFocusTarget,
        nextStatusMessage,
        "",
        verboseErrorDetails,
        false,
        submitRequested,
        false,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withFocusAndStatus(FocusTarget nextFocusTarget, String nextStatusMessage) {
    return new UiState(
        request,
        validation,
        nextFocusTarget,
        nextStatusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withMetadataPanel(MetadataPanelSnapshot nextMetadataPanel) {
    return new UiState(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        nextMetadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withRenderSnapshot(
      String nextStatusMessage,
      ExtensionsPanelSnapshot nextExtensionsPanel,
      FooterSnapshot nextFooter) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        nextExtensionsPanel,
        nextFooter,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withSubmitFeedback(
      FocusTarget nextFocusTarget,
      String nextStatusMessage,
      String nextErrorMessage,
      boolean nextSubmitBlockedByValidation,
      boolean nextSubmitBlockedByTargetConflict) {
    return new UiState(
        request,
        validation,
        nextFocusTarget,
        nextStatusMessage,
        nextErrorMessage,
        verboseErrorDetails,
        false,
        true,
        nextSubmitBlockedByValidation,
        nextSubmitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withSubmissionState(
      String nextStatusMessage,
      String nextErrorMessage,
      String nextVerboseErrorDetails,
      boolean nextSubmitRequested,
      boolean nextSubmitBlockedByValidation,
      boolean nextSubmitBlockedByTargetConflict,
      boolean nextShowErrorDetails) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        nextErrorMessage,
        nextVerboseErrorDetails,
        nextShowErrorDetails,
        nextSubmitRequested,
        nextSubmitBlockedByValidation,
        nextSubmitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withShowErrorDetails(boolean nextShowErrorDetails, String nextStatusMessage) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        errorMessage,
        verboseErrorDetails,
        nextShowErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withPostGeneration(PostGenerationView nextPostGeneration) {
    return new UiState(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        new OverlayState(
            overlays.generationVisible(),
            overlays.commandPaletteVisible(),
            overlays.helpOverlayVisible(),
            nextPostGeneration.visible(),
            overlays.startupOverlayVisible()),
        generation,
        catalogLoad,
        nextPostGeneration,
        startupOverlay,
        extensions);
  }

  UiState withCatalogLoad(
      CatalogLoadView nextCatalogLoad,
      StartupOverlayView nextStartupOverlay,
      String nextStatusMessage,
      String nextErrorMessage,
      String nextVerboseErrorDetails,
      boolean nextShowErrorDetails) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        nextErrorMessage,
        nextVerboseErrorDetails,
        nextShowErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        new OverlayState(
            overlays.generationVisible(),
            overlays.commandPaletteVisible(),
            overlays.helpOverlayVisible(),
            overlays.postGenerationVisible(),
            nextStartupOverlay.visible()),
        generation,
        nextCatalogLoad,
        postGeneration,
        nextStartupOverlay,
        extensions);
  }

  UiState withStartupOverlayStatusLines(List<String> nextStatusLines) {
    return withStartupOverlay(new StartupOverlayView(startupOverlay.visible(), nextStatusLines));
  }

  UiState withStartupOverlay(StartupOverlayView nextStartupOverlay) {
    return new UiState(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        new OverlayState(
            overlays.generationVisible(),
            overlays.commandPaletteVisible(),
            overlays.helpOverlayVisible(),
            overlays.postGenerationVisible(),
            nextStartupOverlay.visible()),
        generation,
        catalogLoad,
        postGeneration,
        nextStartupOverlay,
        extensions);
  }

  UiState withOverlayState(
      boolean nextCommandPaletteVisible,
      boolean nextHelpOverlayVisible,
      int nextCommandPaletteSelection,
      String nextStatusMessage) {
    return new UiState(
        request,
        validation,
        focusTarget,
        nextStatusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        nextCommandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        new OverlayState(
            overlays.generationVisible(),
            nextCommandPaletteVisible,
            nextHelpOverlayVisible,
            overlays.postGenerationVisible(),
            overlays.startupOverlayVisible()),
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withGeneration(GenerationView nextGeneration) {
    return new UiState(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        new OverlayState(
            isGenerationVisible(nextGeneration),
            overlays.commandPaletteVisible(),
            overlays.helpOverlayVisible(),
            overlays.postGenerationVisible(),
            overlays.startupOverlayVisible()),
        nextGeneration,
        catalogLoad,
        postGeneration,
        startupOverlay,
        extensions);
  }

  UiState withExtensions(ExtensionView nextExtensions) {
    return new UiState(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        showErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanel,
        extensionsPanel,
        footer,
        overlays,
        generation,
        catalogLoad,
        postGeneration,
        startupOverlay,
        nextExtensions);
  }

  private static boolean isGenerationVisible(GenerationView generationView) {
    return generationView.state() == CoreTuiController.GenerationState.VALIDATING
        || generationView.state() == CoreTuiController.GenerationState.LOADING;
  }

  record OverlayState(
      boolean generationVisible,
      boolean commandPaletteVisible,
      boolean helpOverlayVisible,
      boolean postGenerationVisible,
      boolean startupOverlayVisible) {}

  record GenerationView(
      CoreTuiController.GenerationState state,
      double progressRatio,
      String progressPhase,
      boolean cancellationRequested) {}

  record CatalogLoadView(CatalogLoadState state) {
    CatalogLoadView {
      state = state == null ? CatalogLoadState.initial() : state;
    }

    boolean loading() {
      return state.isLoading();
    }

    String sourceLabel() {
      return state.sourceLabel();
    }

    boolean stale() {
      return state.isStale();
    }

    String errorMessage() {
      return state.errorMessage();
    }
  }

  record PostGenerationView(
      boolean visible,
      boolean githubVisibilityVisible,
      int actionSelection,
      int githubVisibilitySelection,
      List<UiTextConstants.PostGenerationAction> actions,
      Path lastGeneratedProjectPath,
      String lastGeneratedNextCommand,
      PostGenerationExitPlan exitPlan) {
    PostGenerationView {
      actions = List.copyOf(actions);
      lastGeneratedNextCommand = lastGeneratedNextCommand == null ? "" : lastGeneratedNextCommand;
    }

    PostGenerationView withActionSelection(int nextActionSelection) {
      return new PostGenerationView(
          visible,
          githubVisibilityVisible,
          nextActionSelection,
          githubVisibilitySelection,
          actions,
          lastGeneratedProjectPath,
          lastGeneratedNextCommand,
          exitPlan);
    }

    PostGenerationView withGithubVisibilitySelection(int nextGithubVisibilitySelection) {
      return new PostGenerationView(
          visible,
          githubVisibilityVisible,
          actionSelection,
          nextGithubVisibilitySelection,
          actions,
          lastGeneratedProjectPath,
          lastGeneratedNextCommand,
          exitPlan);
    }

    PostGenerationView showGithubVisibilityMenu() {
      return new PostGenerationView(
          true,
          true,
          actionSelection,
          0,
          actions,
          lastGeneratedProjectPath,
          lastGeneratedNextCommand,
          null);
    }

    PostGenerationView hideGithubVisibilityMenu() {
      return new PostGenerationView(
          visible,
          false,
          actionSelection,
          0,
          actions,
          lastGeneratedProjectPath,
          lastGeneratedNextCommand,
          exitPlan);
    }

    PostGenerationView afterSuccess(Path generatedPath, String nextCommand) {
      return new PostGenerationView(true, false, 0, 0, actions, generatedPath, nextCommand, null);
    }

    PostGenerationView hidden() {
      return new PostGenerationView(
          false,
          false,
          actionSelection,
          0,
          actions,
          lastGeneratedProjectPath,
          lastGeneratedNextCommand,
          null);
    }

    PostGenerationView reset() {
      return new PostGenerationView(false, false, 0, 0, actions, null, "", null);
    }

    PostGenerationView closeWithExitPlan(PostGenerationExitPlan nextExitPlan) {
      return new PostGenerationView(
          false,
          false,
          0,
          0,
          actions,
          lastGeneratedProjectPath,
          lastGeneratedNextCommand,
          nextExitPlan);
    }

    List<String> actionLabels() {
      return actions.stream().map(UiTextConstants.PostGenerationAction::label).toList();
    }

    String successHint() {
      if (lastGeneratedProjectPath == null || lastGeneratedNextCommand.isEmpty()) {
        return "";
      }
      String path = lastGeneratedProjectPath.toString();
      String quotedPath = path.contains(" ") ? "\"" + path + "\"" : path;
      return "cd " + quotedPath + " && " + lastGeneratedNextCommand;
    }

    GitHubVisibility selectedGithubVisibility() {
      return switch (githubVisibilitySelection) {
        case 1 -> GitHubVisibility.PUBLIC;
        case 2 -> GitHubVisibility.INTERNAL;
        default -> GitHubVisibility.PRIVATE;
      };
    }
  }

  record StartupOverlayView(boolean visible, List<String> statusLines) {
    StartupOverlayView {
      statusLines = List.copyOf(statusLines);
    }
  }

  record ExtensionView(
      int filteredCount,
      int totalCount,
      int selectedCount,
      boolean favoritesOnlyEnabled,
      boolean selectedOnlyEnabled,
      String activePresetFilterName,
      String activeCategoryFilterTitle,
      String searchQuery,
      String focusedExtensionId) {}
}
