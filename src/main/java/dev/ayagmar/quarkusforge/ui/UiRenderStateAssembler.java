package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.util.OutputPathResolver;
import dev.tamboui.widgets.input.TextInputState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

final class UiRenderStateAssembler {
  private final EnumMap<FocusTarget, TextInputState> inputStates;
  private final MetadataSelectorManager metadataSelectors;
  private final ExtensionCatalogPreferences extensionCatalogPreferences;
  private final ExtensionCatalogNavigation extensionCatalogNavigation;
  private final ExtensionCatalogProjection extensionCatalogProjection;
  private final GenerationStateTracker generationStateTracker;
  private final UiStateSnapshotMapper uiStateSnapshotMapper;

  UiRenderStateAssembler(
      EnumMap<FocusTarget, TextInputState> inputStates,
      MetadataSelectorManager metadataSelectors,
      ExtensionCatalogPreferences extensionCatalogPreferences,
      ExtensionCatalogNavigation extensionCatalogNavigation,
      ExtensionCatalogProjection extensionCatalogProjection,
      GenerationStateTracker generationStateTracker,
      UiStateSnapshotMapper uiStateSnapshotMapper) {
    this.inputStates = Objects.requireNonNull(inputStates);
    this.metadataSelectors = Objects.requireNonNull(metadataSelectors);
    this.extensionCatalogPreferences = Objects.requireNonNull(extensionCatalogPreferences);
    this.extensionCatalogNavigation = Objects.requireNonNull(extensionCatalogNavigation);
    this.extensionCatalogProjection = Objects.requireNonNull(extensionCatalogProjection);
    this.generationStateTracker = Objects.requireNonNull(generationStateTracker);
    this.uiStateSnapshotMapper = Objects.requireNonNull(uiStateSnapshotMapper);
  }

  CoreUiRenderAdapter.RenderContext renderContext(
      UiState reducerState, MetadataCompatibilityContext metadataCompatibility) {
    Objects.requireNonNull(reducerState);
    Objects.requireNonNull(metadataCompatibility);
    return new CoreUiRenderAdapter.RenderContext(
        metadataRenderContext(reducerState, metadataCompatibility),
        inputStates.get(FocusTarget.EXTENSION_SEARCH),
        extensionCatalogNavigation.listState(),
        extensionCatalogNavigation::isSelected,
        extensionCatalogPreferences::isFavorite);
  }

  UiState uiState(
      UiState reducerState,
      String statusMessage,
      MetadataCompatibilityContext metadataCompatibility,
      boolean generationCancellationRequested) {
    return renderModel(
            reducerState, statusMessage, metadataCompatibility, generationCancellationRequested)
        .snapshotState();
  }

  UiRenderModel renderModel(
      UiState reducerState,
      String statusMessage,
      MetadataCompatibilityContext metadataCompatibility,
      boolean generationCancellationRequested) {
    Objects.requireNonNull(reducerState);
    Objects.requireNonNull(statusMessage);
    Objects.requireNonNull(metadataCompatibility);
    return uiStateSnapshotMapper.renderModel(
        reduceInputState(reducerState, metadataCompatibility, generationCancellationRequested),
        statusMessage,
        new UiStateSnapshotMapper.PanelState(
            extensionsPanelSnapshot(reducerState), footerSnapshot(reducerState, statusMessage)));
  }

  UiState reduceInputState(
      UiState reducerState,
      MetadataCompatibilityContext metadataCompatibility,
      boolean generationCancellationRequested) {
    return reducerState
        .withMetadataPanel(metadataPanelSnapshot(reducerState, metadataCompatibility))
        .withGeneration(
            new UiState.GenerationView(
                generationStateTracker.currentState(),
                generationStateTracker.progressRatio(),
                generationStateTracker.progressPhase(),
                generationCancellationRequested))
        .withStartupOverlayStatusLines(
            startupOverlayStatusLines(reducerState.catalogLoad().state(), metadataCompatibility));
  }

  private MetadataPanelSnapshot metadataPanelSnapshot(
      UiState reducerState, MetadataCompatibilityContext metadataCompatibility) {
    ProjectRequest request = reducerState.request();
    ValidationReport validation = reducerState.validation();
    return new MetadataPanelSnapshot(
        metadataPanelTitle(validation),
        isMetadataFocused(reducerState.focusTarget()),
        !validation.isValid(),
        request.groupId(),
        request.artifactId(),
        request.version(),
        request.packageName(),
        OutputPathResolver.absoluteDisplayPath(request.outputDirectory()),
        MetadataSelectorManager.optionDisplayLabel(
            FocusTarget.PLATFORM_STREAM,
            selectorValue(request, FocusTarget.PLATFORM_STREAM),
            metadataCompatibility.metadataSnapshot()),
        selectorValue(request, FocusTarget.BUILD_TOOL),
        selectorValue(request, FocusTarget.JAVA_VERSION),
        metadataSelectors.selectorInfo(
            FocusTarget.PLATFORM_STREAM, selectorValue(request, FocusTarget.PLATFORM_STREAM)),
        metadataSelectors.selectorInfo(
            FocusTarget.BUILD_TOOL, selectorValue(request, FocusTarget.BUILD_TOOL)),
        metadataSelectors.selectorInfo(
            FocusTarget.JAVA_VERSION, selectorValue(request, FocusTarget.JAVA_VERSION)));
  }

  private ExtensionsPanelSnapshot extensionsPanelSnapshot(UiState reducerState) {
    ValidationReport validation = reducerState.validation();
    CatalogLoadState catalogLoadState = reducerState.catalogLoad().state();
    return new ExtensionsPanelSnapshot(
        isExtensionPanelFocused(reducerState.focusTarget()),
        reducerState.focusTarget() == FocusTarget.EXTENSION_LIST,
        reducerState.focusTarget() == FocusTarget.SUBMIT,
        reducerState.focusTarget() == FocusTarget.EXTENSION_SEARCH,
        catalogLoadState.isLoading(),
        catalogLoadState.errorMessage(),
        catalogLoadState.sourceLabel(),
        catalogLoadState.isStale(),
        extensionCatalogProjection.favoritesOnlyFilterEnabled(),
        extensionCatalogProjection.selectedOnlyFilterEnabled(),
        extensionCatalogPreferences.favoriteExtensionCount(),
        extensionCatalogProjection.activePresetFilterName(),
        extensionCatalogProjection.activeCategoryFilterTitle(),
        validation.errors().size(),
        extensionCatalogProjection.filteredExtensions().size(),
        extensionCatalogProjection.totalCatalogExtensionCount(),
        extensionCatalogProjection.filteredRows(),
        extensionCatalogNavigation.selectedExtensionIds(),
        inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
        focusedExtensionDescription());
  }

  private MetadataFieldRenderContext metadataRenderContext(
      UiState reducerState, MetadataCompatibilityContext metadataCompatibility) {
    ValidationReport validation = reducerState.validation();
    EnumMap<FocusTarget, List<String>> selectorDisplayOptions = new EnumMap<>(FocusTarget.class);
    selectorDisplayOptions.put(
        FocusTarget.PLATFORM_STREAM,
        selectorDisplayOptions(
            FocusTarget.PLATFORM_STREAM,
            metadataSelectors.optionsFor(FocusTarget.PLATFORM_STREAM),
            metadataCompatibility.metadataSnapshot()));
    selectorDisplayOptions.put(
        FocusTarget.BUILD_TOOL,
        selectorDisplayOptions(
            FocusTarget.BUILD_TOOL,
            metadataSelectors.optionsFor(FocusTarget.BUILD_TOOL),
            metadataCompatibility.metadataSnapshot()));
    selectorDisplayOptions.put(
        FocusTarget.JAVA_VERSION,
        selectorDisplayOptions(
            FocusTarget.JAVA_VERSION,
            metadataSelectors.optionsFor(FocusTarget.JAVA_VERSION),
            metadataCompatibility.metadataSnapshot()));
    return new MetadataFieldRenderContext(
        reducerState.focusTarget(), validation, inputStates, selectorDisplayOptions);
  }

  private List<String> startupOverlayStatusLines(
      CatalogLoadState catalogLoadState, MetadataCompatibilityContext metadataCompatibility) {
    String metadataLabel =
        metadataCompatibility.loadError() == null ? "done" : "done (snapshot fallback)";
    String catalogLabel = catalogLoadState.isLoading() ? "in progress" : "done";
    String readyLabel = catalogLoadState.isLoading() ? "waiting" : "ready";
    String spinner = catalogLoadState.isLoading() ? "|" : "-";
    List<String> lines = new ArrayList<>();
    lines.addAll(UiTextConstants.STARTUP_SPLASH_ART);
    lines.add("");
    lines.add("  metadata fetch   : " + metadataLabel);
    lines.add("  catalog load     : " + catalogLabel);
    lines.add("  ready            : " + readyLabel);
    lines.add("");
    lines.add("  " + spinner + " Please wait...");
    return List.copyOf(lines);
  }

  private String focusedExtensionDescription() {
    Integer selectedRow = extensionCatalogNavigation.selectedRow();
    if (selectedRow == null) {
      return "";
    }
    ExtensionCatalogItem selected = extensionCatalogProjection.itemAtRow(selectedRow);
    return selected == null ? "" : selected.description();
  }

  private FooterSnapshot footerSnapshot(UiState reducerState, String statusMessage) {
    UiState.OverlayState overlays = reducerState.overlays();
    UiState.PostGenerationView postGeneration = reducerState.postGeneration();
    return new FooterSnapshot(
        generationStateTracker.isInProgress(),
        reducerState.focusTarget(),
        overlays.commandPaletteVisible(),
        overlays.helpOverlayVisible(),
        postGeneration.visible(),
        statusMessage,
        activeErrorDetails(reducerState),
        verboseErrorDetails(reducerState),
        reducerState.showErrorDetails(),
        postGeneration.successHint(),
        preGeneratePlan(reducerState),
        resolvedTargetPathForFooter(reducerState),
        focusedFieldValueForFooter(reducerState),
        focusedFieldIssueForFooter(reducerState));
  }

  private String resolvedTargetPathForFooter(UiState reducerState) {
    if (reducerState.overlays().helpOverlayVisible()
        || reducerState.overlays().commandPaletteVisible()
        || reducerState.postGeneration().visible()) {
      return "";
    }
    try {
      return OutputPathResolver.resolveGeneratedProjectDirectory(reducerState.request()).toString();
    } catch (RuntimeException pathError) {
      return "";
    }
  }

  private String focusedFieldValueForFooter(UiState reducerState) {
    if (reducerState.overlays().helpOverlayVisible()
        || reducerState.overlays().commandPaletteVisible()
        || reducerState.postGeneration().visible()) {
      return "";
    }
    return switch (reducerState.focusTarget()) {
      case GROUP_ID, ARTIFACT_ID, VERSION, PACKAGE_NAME, OUTPUT_DIR ->
          inputStates.get(reducerState.focusTarget()).text();
      default -> "";
    };
  }

  private String focusedFieldIssueForFooter(UiState reducerState) {
    if (reducerState.overlays().helpOverlayVisible()
        || reducerState.overlays().commandPaletteVisible()
        || reducerState.postGeneration().visible()) {
      return "";
    }
    if (!hasValidationErrorFor(reducerState.validation(), reducerState.focusTarget())) {
      return "";
    }
    String fieldName = UiFocusTargets.nameOf(reducerState.focusTarget());
    return reducerState.validation().errors().stream()
        .filter(error -> error.field().equalsIgnoreCase(fieldName))
        .findFirst()
        .map(error -> error.message())
        .orElse("");
  }

  private String activeErrorDetails(UiState reducerState) {
    if (!reducerState.errorMessage().isBlank()) {
      return reducerState.errorMessage();
    }
    if (!reducerState.catalogLoad().state().errorMessage().isBlank()) {
      return reducerState.catalogLoad().state().errorMessage();
    }
    return "";
  }

  private String verboseErrorDetails(UiState reducerState) {
    if (!reducerState.verboseErrorDetails().isBlank()) {
      return reducerState.verboseErrorDetails();
    }
    if (!reducerState.catalogLoad().state().errorMessage().isBlank()) {
      return reducerState.catalogLoad().state().errorMessage();
    }
    return "";
  }

  private String preGeneratePlan(UiState reducerState) {
    if (generationStateTracker.isInProgress() || reducerState.postGeneration().visible()) {
      return "";
    }
    ProjectRequest request = reducerState.request();
    String targetPathDisplay;
    try {
      targetPathDisplay = OutputPathResolver.resolveGeneratedProjectDirectory(request).toString();
    } catch (RuntimeException pathError) {
      targetPathDisplay = request.outputDirectory() + "/" + request.artifactId();
    }
    return targetPathDisplay
        + " | "
        + request.buildTool()
        + " | Java "
        + request.javaVersion()
        + " | "
        + extensionCatalogNavigation.selectedExtensionCount()
        + " ext";
  }

  private static boolean isMetadataFocused(FocusTarget focusTarget) {
    return focusTarget == FocusTarget.GROUP_ID
        || focusTarget == FocusTarget.ARTIFACT_ID
        || focusTarget == FocusTarget.VERSION
        || focusTarget == FocusTarget.PACKAGE_NAME
        || focusTarget == FocusTarget.OUTPUT_DIR
        || focusTarget == FocusTarget.PLATFORM_STREAM
        || focusTarget == FocusTarget.BUILD_TOOL
        || focusTarget == FocusTarget.JAVA_VERSION;
  }

  private static boolean isExtensionPanelFocused(FocusTarget focusTarget) {
    return focusTarget == FocusTarget.EXTENSION_SEARCH
        || focusTarget == FocusTarget.EXTENSION_LIST
        || focusTarget == FocusTarget.SUBMIT;
  }

  private static String metadataPanelTitle(ValidationReport validation) {
    return validation.isValid() ? "Project Metadata" : "Project Metadata [invalid]";
  }

  private static boolean hasValidationErrorFor(ValidationReport validation, FocusTarget target) {
    if ((!UiFocusPredicates.isTextInputFocus(target)
            && !MetadataSelectorManager.isSelectorFocus(target))
        || target == FocusTarget.EXTENSION_SEARCH) {
      return false;
    }
    String fieldName = UiFocusTargets.nameOf(target);
    return validation.errors().stream()
        .anyMatch(error -> error.field().equalsIgnoreCase(fieldName));
  }

  private static String selectorValue(ProjectRequest request, FocusTarget target) {
    return switch (target) {
      case PLATFORM_STREAM -> request.platformStream();
      case BUILD_TOOL -> request.buildTool();
      case JAVA_VERSION -> request.javaVersion();
      default -> "";
    };
  }

  private static List<String> selectorDisplayOptions(
      FocusTarget target, List<String> options, MetadataDto metadataSnapshot) {
    if (target != FocusTarget.PLATFORM_STREAM) {
      return options;
    }
    List<String> displayOptions = new ArrayList<>(options.size());
    for (String option : options) {
      displayOptions.add(
          MetadataSelectorManager.optionDisplayLabel(target, option, metadataSnapshot));
    }
    return List.copyOf(displayOptions);
  }
}
