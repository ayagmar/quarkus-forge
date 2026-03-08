package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable reducer-owned UI state. */
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
    OverlayState overlays,
    CatalogLoadView catalogLoad,
    PostGenerationView postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        nextPostGeneration,
        extensions);
  }

  UiState withCatalogLoad(
      CatalogLoadView nextCatalogLoad,
      boolean nextStartupOverlayVisible,
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
        overlays.withStartupOverlayVisible(nextStartupOverlayVisible),
        nextCatalogLoad,
        postGeneration,
        extensions);
  }

  UiState withStartupOverlayVisibility(boolean nextStartupOverlayVisible) {
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
        overlays.withStartupOverlayVisible(nextStartupOverlayVisible),
        catalogLoad,
        postGeneration,
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
        overlays.withCommandPaletteAndHelp(nextCommandPaletteVisible, nextHelpOverlayVisible),
        catalogLoad,
        postGeneration,
        extensions);
  }

  UiState withGenerationOverlayVisible(boolean nextGenerationVisible) {
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
        overlays.withGenerationVisible(nextGenerationVisible),
        catalogLoad,
        postGeneration,
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
        overlays,
        catalogLoad,
        postGeneration,
        nextExtensions);
  }

  record OverlayState(
      boolean generationVisible,
      boolean commandPaletteVisible,
      boolean helpOverlayVisible,
      boolean startupOverlayVisible) {

    OverlayState withGenerationVisible(boolean nextGenerationVisible) {
      return new OverlayState(
          nextGenerationVisible, commandPaletteVisible, helpOverlayVisible, startupOverlayVisible);
    }

    OverlayState withCommandPaletteAndHelp(
        boolean nextCommandPaletteVisible, boolean nextHelpOverlayVisible) {
      return new OverlayState(
          generationVisible,
          nextCommandPaletteVisible,
          nextHelpOverlayVisible,
          startupOverlayVisible);
    }

    OverlayState withStartupOverlayVisible(boolean nextStartupOverlayVisible) {
      return new OverlayState(
          generationVisible, commandPaletteVisible, helpOverlayVisible, nextStartupOverlayVisible);
    }
  }

  record GenerationView(
      GenerationState state,
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
      actionSelection = clampActionSelection(actionSelection, actions.size());
      githubVisibilitySelection =
          clampSelection(
              githubVisibilitySelection, UiTextConstants.GITHUB_VISIBILITY_LABELS.size());
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

    private static int clampActionSelection(int selection, int size) {
      return clampSelection(selection, size);
    }

    private static int clampSelection(int selection, int size) {
      if (size <= 0) {
        return 0;
      }
      return Math.max(0, Math.min(selection, size - 1));
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
      SelectionView selection) {
    ExtensionView {
      selection = Objects.requireNonNull(selection);
    }

    static ExtensionView snapshot(
        int filteredCount,
        int totalCount,
        int selectedCount,
        boolean favoritesOnlyEnabled,
        boolean selectedOnlyEnabled,
        String activePresetFilterName,
        String activeCategoryFilterTitle,
        String searchQuery,
        String focusedExtensionId,
        boolean listSelectionAtTop,
        boolean categoryHeaderSelected) {
      return new ExtensionView(
          filteredCount,
          totalCount,
          selectedCount,
          favoritesOnlyEnabled,
          selectedOnlyEnabled,
          activePresetFilterName,
          activeCategoryFilterTitle,
          searchQuery,
          new SelectionView(focusedExtensionId, listSelectionAtTop, categoryHeaderSelected));
    }

    String focusedExtensionId() {
      return selection.focusedExtensionId();
    }

    boolean listSelectionAtTop() {
      return selection.listSelectionAtTop();
    }

    boolean categoryHeaderSelected() {
      return selection.categoryHeaderSelected();
    }

    record SelectionView(
        String focusedExtensionId, boolean listSelectionAtTop, boolean categoryHeaderSelected) {
      SelectionView {
        focusedExtensionId = focusedExtensionId == null ? "" : focusedExtensionId;
      }
    }
  }
}
