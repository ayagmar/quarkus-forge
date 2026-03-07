package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.BuildToolCodec;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileLock;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import dev.ayagmar.quarkusforge.postgen.IdeDetector;
import dev.ayagmar.quarkusforge.util.OutputPathResolver;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.input.TextInputState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Main TUI orchestration shell.
 *
 * <p>Coordinates input routing, reducer/effects dispatch, async lifecycle callbacks, and
 * state-driven rendering while delegating domain-specific behavior to extracted collaborators.
 */
public final class CoreTuiController implements UiRoutingContext, GenerationFlowCallbacks {
  private static final ProjectGenerationRunner NOOP_PROJECT_GENERATION_RUNNER =
      (generationRequest, outputDirectory, cancelled, progressListener) ->
          CompletableFuture.failedFuture(
              new IllegalStateException("Generation flow is not configured in this runtime"));
  private final EnumMap<FocusTarget, TextInputState> inputStates = new EnumMap<>(FocusTarget.class);
  private final ProjectRequestValidator requestValidator = new ProjectRequestValidator();
  private MetadataCompatibilityContext metadataCompatibility;
  private final UiScheduler scheduler;
  private final ExtensionCatalogPreferences extensionCatalogPreferences;
  private final ExtensionCatalogNavigation extensionCatalogNavigation;
  private final ExtensionCatalogProjection extensionCatalogProjection;
  private final ExtensionInteractionHandler extensionInteraction;
  private final ProjectGenerationRunner projectGenerationRunner;
  private final MetadataSelectorManager metadataSelectors = new MetadataSelectorManager();

  private ProjectRequest request;
  private ValidationReport validation;
  private FocusTarget focusTarget;
  private String statusMessage;
  private String errorMessage;
  private String verboseErrorDetails;
  private String generationStatusNotice;
  private boolean submitRequested;
  private boolean submitBlockedByValidation;
  private boolean submitBlockedByTargetConflict;
  private final GenerationStateTracker generationStateTracker;
  private final AsyncRepaintSignal asyncRepaintSignal;
  private final UiEventRouter uiEventRouter;
  private final GenerationFlowCoordinator generationFlowCoordinator;
  private final CatalogLoadCoordinator catalogLoadCoordinator;
  private final UiReducer uiReducer;
  private final UiEffectsRunner uiEffectsRunner;
  private final UiEffectsPort uiEffectsPort;
  private final UiStateSnapshotMapper uiStateSnapshotMapper;
  private final UiRenderer uiRenderer;
  private final CoreUiRenderAdapter uiRenderAdapter;
  private final CatalogLoadIntentPort catalogLoadIntentPort;
  private CatalogLoadState catalogLoadState = CatalogLoadState.initial();
  private boolean startupOverlayVisible;
  private boolean showErrorDetails;
  private boolean commandPaletteVisible;
  private boolean helpOverlayVisible;
  private int commandPaletteSelection;
  private final PostGenerationMenuState postGenerationMenu = new PostGenerationMenuState();

  private CoreTuiController(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner,
      ExtensionFavoritesStore favoritesStore,
      Executor favoritesPersistenceExecutor,
      List<IdeDetector.DetectedIde> detectedIdes) {
    Objects.requireNonNull(initialState);
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(debounceDelay);
    Objects.requireNonNull(projectGenerationRunner);
    Objects.requireNonNull(favoritesStore);
    Objects.requireNonNull(favoritesPersistenceExecutor);
    request = initialState.request();
    validation = initialState.validation();
    focusTarget = FocusTarget.GROUP_ID;
    statusMessage = "Ready";
    errorMessage = "";
    verboseErrorDetails = "";
    generationStatusNotice = "";
    submitRequested = false;
    submitBlockedByValidation = false;
    submitBlockedByTargetConflict = false;
    metadataCompatibility = initialState.metadataCompatibility();
    this.scheduler = scheduler;
    this.projectGenerationRunner = projectGenerationRunner;
    UiTheme theme = UiTheme.loadDefault();
    BodyPanelRenderer bodyPanelRenderer = new BodyPanelRenderer(theme);
    generationStateTracker = new GenerationStateTracker();
    FooterLinesComposer footerLinesComposer = new FooterLinesComposer();
    asyncRepaintSignal = new AsyncRepaintSignal();
    uiEventRouter = new UiEventRouter();
    generationFlowCoordinator = new GenerationFlowCoordinator();
    catalogLoadCoordinator = new CatalogLoadCoordinator();
    uiReducer = new CoreUiReducer();
    uiEffectsRunner = new UiEffectsRunner();
    uiEffectsPort =
        new UiEffectsPort() {
          @Override
          public void startCatalogLoad(ExtensionCatalogLoader loader) {
            runCatalogLoadEffect(loader);
          }

          @Override
          public void requestCatalogReload() {
            runCatalogReloadEffect();
          }

          @Override
          public void prepareForGeneration() {
            prepareForGenerationEffect();
          }

          @Override
          public void cancelPendingAsync() {
            cancelPendingAsyncEffect();
          }

          @Override
          public void exportRecipeAndLock() {
            exportRecipeAndLockEffect();
          }

          @Override
          public void executeCommandPaletteAction(CommandPaletteAction action) {
            executeCommandPaletteActionEffect(action);
          }

          @Override
          public void applyCatalogLoadSuccess(CatalogLoadSuccess success) {
            applyCatalogLoadSuccessEffect(success);
          }

          @Override
          public void startGeneration() {
            startGenerationEffect();
          }

          @Override
          public void transitionGenerationState(GenerationState targetState) {
            transitionGenerationStateEffect(targetState);
          }

          @Override
          public void requestGenerationCancellation() {
            requestGenerationCancellationEffect();
          }

          @Override
          public void requestAsyncRepaint() {
            requestAsyncRepaintEffect();
          }

          @Override
          public void applyMetadataSelectorKey(FocusTarget target, KeyEvent keyEvent) {
            applyMetadataSelectorKeyEffect(target, keyEvent);
          }

          @Override
          public void applyTextInputKey(FocusTarget target, KeyEvent keyEvent) {
            applyTextInputKeyEffect(target, keyEvent);
          }
        };
    uiStateSnapshotMapper = new UiStateSnapshotMapper();
    uiRenderer = new UiRenderer();
    uiRenderAdapter =
        new CoreUiRenderAdapter(
            theme, bodyPanelRenderer, footerLinesComposer, new CompactFieldRenderer(theme));
    catalogLoadIntentPort =
        new CatalogLoadIntentPort() {
          @Override
          public CatalogLoadState currentCatalogLoadState() {
            return catalogLoadState;
          }

          @Override
          public void scheduleOnRenderThread(Runnable task) {
            scheduler.schedule(Duration.ZERO, task);
          }

          @Override
          public void dispatchIntent(UiIntent intent) {
            CoreTuiController.this.dispatchIntent(intent);
          }
        };
    startupOverlayVisible = false;
    showErrorDetails = false;

    for (FocusTarget target : FocusTarget.values()) {
      inputStates.put(target, new TextInputState(""));
    }
    inputStates.get(FocusTarget.GROUP_ID).setText(request.groupId());
    inputStates.get(FocusTarget.ARTIFACT_ID).setText(request.artifactId());
    inputStates.get(FocusTarget.VERSION).setText(request.version());
    inputStates.get(FocusTarget.PACKAGE_NAME).setText(request.packageName());
    inputStates.get(FocusTarget.OUTPUT_DIR).setText(request.outputDirectory());
    syncMetadataSelectors();
    postGenerationMenu.setActions(UiTextConstants.postGenerationActions(detectedIdes));
    for (FocusTarget target : UiFocusTargets.ordered()) {
      if (isTextInputFocus(target)) {
        inputStates.get(target).moveCursorToEnd();
      }
    }
    extensionCatalogPreferences =
        new ExtensionCatalogPreferences(
            Objects.requireNonNull(favoritesStore),
            Objects.requireNonNull(favoritesPersistenceExecutor));
    extensionCatalogNavigation = new ExtensionCatalogNavigation();
    extensionCatalogProjection =
        new ExtensionCatalogProjection(
            scheduler, debounceDelay, inputStates.get(FocusTarget.EXTENSION_SEARCH).text());
    extensionCatalogProjection.initialize(extensionCatalogNavigation, extensionCatalogPreferences);
    extensionInteraction =
        new ExtensionInteractionHandler(
            extensionCatalogPreferences, extensionCatalogNavigation, extensionCatalogProjection);

    revalidate();
  }

  public static CoreTuiController from(ForgeUiState initialState) {
    return from(
        initialState,
        UiScheduler.immediate(),
        Duration.ZERO,
        NOOP_PROJECT_GENERATION_RUNNER,
        ExtensionFavoritesStore.inMemory(),
        Runnable::run);
  }

  public static CoreTuiController from(
      ForgeUiState initialState, UiScheduler scheduler, Duration debounceDelay) {
    return from(
        initialState,
        scheduler,
        debounceDelay,
        NOOP_PROJECT_GENERATION_RUNNER,
        ExtensionFavoritesStore.inMemory(),
        Runnable::run);
  }

  public static CoreTuiController from(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner) {
    return from(
        initialState,
        scheduler,
        debounceDelay,
        projectGenerationRunner,
        ExtensionFavoritesStore.inMemory(),
        Runnable::run);
  }

  public static CoreTuiController from(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner,
      ExtensionFavoritesStore favoritesStore,
      Executor favoritesPersistenceExecutor) {
    return new CoreTuiController(
        initialState,
        scheduler,
        debounceDelay,
        projectGenerationRunner,
        favoritesStore,
        favoritesPersistenceExecutor,
        List.of());
  }

  public static CoreTuiController from(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner,
      ExtensionFavoritesStore favoritesStore,
      Executor favoritesPersistenceExecutor,
      List<IdeDetector.DetectedIde> detectedIdes) {
    return new CoreTuiController(
        initialState,
        scheduler,
        debounceDelay,
        projectGenerationRunner,
        favoritesStore,
        favoritesPersistenceExecutor,
        detectedIdes);
  }

  public static Executor defaultFavoritesPersistenceExecutor() {
    return ForkJoinPool.commonPool();
  }

  public void loadExtensionCatalogAsync(ExtensionCatalogLoader loader) {
    dispatchIntent(new UiIntent.CatalogLoadRequestedIntent(loader));
  }

  public UiAction onEvent(Event event) {
    reconcileGenerationCompletionIfDone();
    if (event instanceof TickEvent) {
      return handleTickEvent();
    }
    if (event instanceof ResizeEvent resizeEvent) {
      statusMessage = "Terminal resized to " + resizeEvent.width() + "x" + resizeEvent.height();
      return UiAction.handled(false);
    }
    if (!(event instanceof KeyEvent keyEvent)) {
      return UiAction.ignored();
    }
    return uiEventRouter.routeKeyEvent(keyEvent, this);
  }

  @Override
  public boolean shouldToggleHelpOverlay(KeyEvent keyEvent) {
    return CoreTuiController.shouldToggleHelpOverlay(keyEvent, focusTarget);
  }

  @Override
  public boolean isCommandPaletteToggleKey(KeyEvent keyEvent) {
    return AppKeyActions.isCommandPaletteToggleKey(keyEvent);
  }

  @Override
  public UiAction handleExtensionCancelFlow(KeyEvent keyEvent) {
    if (!keyEvent.isCancel() || isGenerationInProgress()) {
      return null;
    }
    boolean isExtensionFocus =
        focusTarget == FocusTarget.EXTENSION_SEARCH || focusTarget == FocusTarget.EXTENSION_LIST;
    if (!isExtensionFocus) {
      return null;
    }
    // Priority: clear search > disable favorites > disable selected-only > disable preset >
    // disable category > exit search
    if (!inputStates.get(FocusTarget.EXTENSION_SEARCH).text().isBlank()) {
      clearExtensionSearchFilter();
      return UiAction.handled(false);
    }
    if (extensionCatalogProjection.favoritesOnlyFilterEnabled()) {
      toggleFavoritesOnlyFilter();
      return UiAction.handled(false);
    }
    if (extensionCatalogProjection.selectedOnlyFilterEnabled()) {
      toggleSelectedOnlyFilter();
      return UiAction.handled(false);
    }
    if (!extensionCatalogProjection.activePresetFilterName().isBlank()) {
      clearPresetFilter();
      return UiAction.handled(false);
    }
    if (!extensionCatalogProjection.activeCategoryFilterTitle().isBlank()) {
      clearCategoryFilter();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_SEARCH) {
      focusExtensionList();
      return UiAction.handled(false);
    }
    return null;
  }

  @Override
  public UiAction handleQuitFlow(KeyEvent keyEvent) {
    if (!(keyEvent.isCancel() || keyEvent.isCtrlC())) {
      return null;
    }
    if (isGenerationInProgress()) {
      return routeIntent(new UiIntent.CancelGenerationIntent());
    }
    cancelPendingAsyncOperations();
    return UiAction.handled(true);
  }

  @Override
  public UiAction handleWhileGenerationInProgress(KeyEvent keyEvent) {
    if (keyEvent.isConfirm()) {
      generationStatusNotice = "Generation already in progress. Press Esc to cancel.";
      return UiAction.handled(false);
    }
    generationStatusNotice = "Generation in progress. Press Esc to cancel.";
    return UiAction.handled(false);
  }

  @Override
  public UiAction handleGlobalShortcutFlow(KeyEvent keyEvent) {
    if (AppKeyActions.isNextInvalidFieldKey(keyEvent)) {
      moveFocusToAdjacentInvalidField(true);
      return UiAction.handled(false);
    }
    if (AppKeyActions.isPreviousInvalidFieldKey(keyEvent)) {
      moveFocusToAdjacentInvalidField(false);
      return UiAction.handled(false);
    }
    if (shouldFocusExtensionSearch(keyEvent, focusTarget)) {
      focusExtensionSearch();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isFocusExtensionListKey(keyEvent)) {
      focusExtensionList();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isCatalogReloadKey(keyEvent)) {
      requestCatalogReload();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isFavoritesFilterToggleKey(keyEvent)) {
      toggleFavoritesOnlyFilter();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isSelectedOnlyFilterToggleKey(keyEvent)) {
      toggleSelectedOnlyFilter();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isPresetFilterCycleKey(keyEvent)) {
      cyclePresetFilter();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isJumpToFavoriteKey(keyEvent)) {
      jumpToFavorite();
      return UiAction.handled(false);
    }
    if (AppKeyActions.isErrorDetailsToggleKey(keyEvent)) {
      toggleErrorDetails();
      return UiAction.handled(false);
    }
    return null;
  }

  @Override
  public UiAction handleFocusNavigationFlow(KeyEvent keyEvent) {
    return routeIntent(new UiIntent.FocusNavigationIntent(keyEvent, focusTarget));
  }

  @Override
  public UiAction handleSubmitFlow(KeyEvent keyEvent) {
    if (keyEvent.isConfirm() || AppKeyActions.isGenerateShortcutKey(keyEvent)) {
      UiAction action =
          routeIntent(
              new UiIntent.SubmitRequestedIntent(
                  UiIntent.SubmitRequestContext.from(
                      projectGenerationRunner != NOOP_PROJECT_GENERATION_RUNNER,
                      extensionCatalogNavigation.selectedExtensionCount(),
                      currentTargetConflictErrorMessage())));
      moveFocusedInputCursorToEndAfterSubmitFeedback();
      return action != null ? action : UiAction.handled(false);
    }
    return null;
  }

  @Override
  public UiAction handleExtensionFocusFlow(KeyEvent keyEvent) {
    if (focusTarget == FocusTarget.EXTENSION_SEARCH && keyEvent.code() == KeyCode.DOWN) {
      focusExtensionList();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && isUpNavigation(keyEvent)
        && extensionCatalogNavigation.isSelectionAtTop(extensionCatalogProjection.rows())) {
      focusExtensionSearch();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && (keyEvent.isLeft() || UiKeyMatchers.isVimLeftKey(keyEvent))) {
      handleExtensionListHierarchyLeft();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && (keyEvent.isRight() || UiKeyMatchers.isVimRightKey(keyEvent))) {
      handleExtensionListHierarchyRight();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && keyEvent.isPageDown()) {
      jumpToAdjacentCategorySection(true);
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && keyEvent.isPageUp()) {
      jumpToAdjacentCategorySection(false);
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && keyEvent.isSelect()
        && extensionCatalogProjection.isCategorySectionHeaderSelected(
            extensionCatalogNavigation.selectedRow())) {
      toggleCategoryCollapseAtSelection();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && AppKeyActions.isFavoriteToggleKey(keyEvent)) {
      toggleFavoriteAtSelection();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && AppKeyActions.isCategoryFilterCycleKey(keyEvent)) {
      cycleCategoryFilter();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && AppKeyActions.isPresetFilterCycleKey(keyEvent)) {
      cyclePresetFilter();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && AppKeyActions.isClearSelectedExtensionsKey(keyEvent)) {
      clearSelectedExtensions();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && AppKeyActions.isCategoryCollapseToggleKey(keyEvent)) {
      toggleCategoryCollapseAtSelection();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && AppKeyActions.isExpandAllCategoriesKey(keyEvent)) {
      expandAllCategories();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && handleExtensionListKeys(
            keyEvent, toggledName -> statusMessage = "Toggled extension: " + toggledName)) {
      return UiAction.handled(false);
    }
    return null;
  }

  @Override
  public UiAction handleMetadataSelectorFlow(KeyEvent keyEvent) {
    return routeIntent(new UiIntent.MetadataInputIntent(keyEvent, focusTarget));
  }

  @Override
  public UiAction handleTextInputFlow(KeyEvent keyEvent) {
    return routeIntent(new UiIntent.TextInputIntent(keyEvent, focusTarget));
  }

  @Override
  public void beforeGenerationStart() {
    extensionCatalogProjection.cancelPendingAsync();
  }

  @Override
  public boolean transitionTo(GenerationState targetState) {
    return transitionGenerationState(targetState);
  }

  @Override
  public GenerationState currentState() {
    return generationStateTracker.currentState();
  }

  @Override
  public void onSubmitIgnored(String stateLabel) {
    generationStatusNotice = "Submit ignored in state: " + stateLabel;
  }

  @Override
  public void onProgress(GenerationProgressUpdate progressUpdate) {
    generationStateTracker.updateProgress(progressUpdate);
    generationStatusNotice = "";
    dispatchIntent(new UiIntent.GenerationProgressIntent());
  }

  @Override
  public void onGenerationSuccess(Path generatedPath) {
    generationStatusNotice = "";
    dispatchIntent(
        new UiIntent.GenerationSuccessIntent(generatedPath, nextStepCommand(request.buildTool())));
  }

  @Override
  public void onGenerationCancelled() {
    generationStatusNotice = "";
    dispatchIntent(new UiIntent.GenerationCancelledIntent());
  }

  @Override
  public void onGenerationFailed(Throwable cause) {
    generationStatusNotice = "";
    dispatchIntent(
        new UiIntent.GenerationFailedIntent(
            ErrorMessageMapper.userFriendlyError(cause), ErrorMessageMapper.verboseDetails(cause)));
  }

  @Override
  public void onCancellationRequested() {
    generationStatusNotice = "";
    dispatchIntent(new UiIntent.GenerationCancellationRequestedIntent());
  }

  public void setStartupOverlayMinDuration(Duration minimumDuration) {
    Objects.requireNonNull(minimumDuration);
    catalogLoadCoordinator.setStartupOverlayMinDuration(minimumDuration);
  }

  public void render(Frame frame) {
    reconcileGenerationCompletionIfDone();
    uiRenderAdapter.updateContext(
        new CoreUiRenderAdapter.RenderContext(
            metadataRenderContext(),
            inputStates.get(FocusTarget.EXTENSION_SEARCH),
            extensionCatalogNavigation.listState(),
            extensionCatalogNavigation::isSelected,
            extensionCatalogPreferences::isFavorite));
    uiRenderer.render(frame, uiState(), uiRenderAdapter);
  }

  UiState uiState() {
    return uiState(true);
  }

  private UiState uiState(boolean useEffectiveStatusMessage) {
    MetadataPanelSnapshot metadataPanelSnapshot = metadataPanelSnapshot();
    ExtensionsPanelSnapshot extensionsPanelSnapshot = extensionsPanelSnapshot();
    FooterSnapshot footerSnapshot = footerSnapshot();
    String snapshotStatusMessage =
        useEffectiveStatusMessage ? effectiveStatusMessage() : statusMessage;
    return uiStateSnapshotMapper.map(
        request,
        focusTarget,
        commandPaletteSelection,
        new UiStateSnapshotMapper.ValidationState(validation, submitBlockedByValidation),
        new UiStateSnapshotMapper.SubmissionState(
            submitRequested,
            submitBlockedByTargetConflict,
            snapshotStatusMessage,
            errorMessage,
            verboseErrorDetails,
            showErrorDetails),
        new UiStateSnapshotMapper.ViewState(
            new UiState.OverlayState(
                isGenerationActive(),
                commandPaletteVisible,
                helpOverlayVisible,
                postGenerationMenu.isVisible(),
                startupOverlayVisible),
            new UiState.GenerationView(
                generationStateTracker.currentState(),
                generationStateTracker.progressRatio(),
                generationStateTracker.progressPhase(),
                generationFlowCoordinator.isCancellationRequested()),
            new UiState.CatalogLoadView(catalogLoadState),
            postGenerationMenu.snapshot(),
            new UiState.StartupOverlayView(startupOverlayVisible, startupStatusLines()),
            new UiState.ExtensionView(
                extensionCatalogProjection.filteredExtensions().size(),
                extensionCatalogProjection.totalCatalogExtensionCount(),
                extensionCatalogNavigation.selectedExtensionCount(),
                extensionCatalogProjection.favoritesOnlyFilterEnabled(),
                extensionCatalogProjection.selectedOnlyFilterEnabled(),
                extensionCatalogProjection.activePresetFilterName(),
                extensionCatalogProjection.activeCategoryFilterTitle(),
                inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
                focusedExtensionId())),
        new UiStateSnapshotMapper.PanelState(
            metadataPanelSnapshot, extensionsPanelSnapshot, footerSnapshot));
  }

  FocusTarget focusTarget() {
    return focusTarget;
  }

  ValidationReport validation() {
    return validation;
  }

  public ProjectRequest request() {
    return request;
  }

  List<String> selectedExtensionIds() {
    return extensionCatalogNavigation.selectedExtensionIds();
  }

  String statusMessage() {
    return effectiveStatusMessage();
  }

  String errorMessage() {
    return errorMessage;
  }

  boolean commandPaletteVisible() {
    return commandPaletteVisible;
  }

  boolean helpOverlayVisible() {
    return helpOverlayVisible;
  }

  boolean submitRequested() {
    return submitRequested;
  }

  public Optional<PostGenerationExitPlan> postGenerationExitPlan() {
    return Optional.ofNullable(postGenerationMenu.exitPlan());
  }

  boolean postGenerationMenuVisible() {
    return postGenerationMenu.isVisible();
  }

  boolean githubVisibilityMenuVisible() {
    return postGenerationMenu.isGithubVisibilityMenuVisible();
  }

  String postGenerationSuccessHint() {
    return postGenerationMenu.successHint();
  }

  List<String> postGenerationActionLabels() {
    return postGenerationMenu.actionLabels();
  }

  int postGenerationActionSelection() {
    return postGenerationMenu.actionSelection();
  }

  GenerationState generationState() {
    return generationStateTracker.currentState();
  }

  int filteredExtensionCount() {
    return extensionCatalogProjection.filteredExtensions().size();
  }

  String firstFilteredExtensionId() {
    List<ExtensionCatalogItem> filteredExtensions = extensionCatalogProjection.filteredExtensions();
    return filteredExtensions.isEmpty() ? "" : filteredExtensions.getFirst().id();
  }

  List<String> filteredExtensionIds() {
    return extensionCatalogProjection.filteredExtensions().stream()
        .map(ExtensionCatalogItem::id)
        .toList();
  }

  List<String> catalogSectionHeaders() {
    return extensionCatalogProjection.filteredRows().stream()
        .filter(ExtensionCatalogRow::isSectionHeader)
        .map(ExtensionCatalogRow::label)
        .toList();
  }

  boolean favoritesOnlyFilterEnabled() {
    return extensionCatalogProjection.favoritesOnlyFilterEnabled();
  }

  int favoriteExtensionCount() {
    return extensionCatalogPreferences.favoriteExtensionCount();
  }

  String focusedListExtensionId() {
    return focusedExtensionId();
  }

  private MetadataPanelSnapshot metadataPanelSnapshot() {
    return new MetadataPanelSnapshot(
        metadataPanelTitle(),
        isMetadataFocused(),
        !validation.isValid(),
        request.groupId(),
        request.artifactId(),
        request.version(),
        request.packageName(),
        OutputPathResolver.absoluteDisplayPath(request.outputDirectory()),
        MetadataSelectorManager.optionDisplayLabel(
            FocusTarget.PLATFORM_STREAM,
            selectorValue(FocusTarget.PLATFORM_STREAM),
            metadataCompatibility.metadataSnapshot()),
        selectorValue(FocusTarget.BUILD_TOOL),
        selectorValue(FocusTarget.JAVA_VERSION),
        metadataSelectors.selectorInfo(
            FocusTarget.PLATFORM_STREAM, selectorValue(FocusTarget.PLATFORM_STREAM)),
        metadataSelectors.selectorInfo(
            FocusTarget.BUILD_TOOL, selectorValue(FocusTarget.BUILD_TOOL)),
        metadataSelectors.selectorInfo(
            FocusTarget.JAVA_VERSION, selectorValue(FocusTarget.JAVA_VERSION)));
  }

  private ExtensionsPanelSnapshot extensionsPanelSnapshot() {
    return new ExtensionsPanelSnapshot(
        isExtensionPanelFocused(),
        focusTarget == FocusTarget.EXTENSION_LIST,
        focusTarget == FocusTarget.SUBMIT,
        focusTarget == FocusTarget.EXTENSION_SEARCH,
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

  private boolean isGenerationActive() {
    GenerationState state = generationStateTracker.currentState();
    return state == GenerationState.VALIDATING || state == GenerationState.LOADING;
  }

  private MetadataFieldRenderContext metadataRenderContext() {
    EnumMap<FocusTarget, List<String>> selectorDisplayOptions = new EnumMap<>(FocusTarget.class);
    selectorDisplayOptions.put(
        FocusTarget.PLATFORM_STREAM,
        selectorDisplayOptions(
            FocusTarget.PLATFORM_STREAM,
            metadataSelectors.optionsFor(FocusTarget.PLATFORM_STREAM)));
    selectorDisplayOptions.put(
        FocusTarget.BUILD_TOOL,
        selectorDisplayOptions(
            FocusTarget.BUILD_TOOL, metadataSelectors.optionsFor(FocusTarget.BUILD_TOOL)));
    selectorDisplayOptions.put(
        FocusTarget.JAVA_VERSION,
        selectorDisplayOptions(
            FocusTarget.JAVA_VERSION, metadataSelectors.optionsFor(FocusTarget.JAVA_VERSION)));
    return new MetadataFieldRenderContext(
        focusTarget, validation, inputStates, selectorDisplayOptions);
  }

  private List<String> startupStatusLines() {
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

  private String focusedExtensionId() {
    String focusedId =
        extensionCatalogProjection.itemIdAtRow(extensionCatalogNavigation.selectedRow());
    return focusedId == null ? "" : focusedId;
  }

  private String focusedExtensionDescription() {
    Integer selectedRow = extensionCatalogNavigation.selectedRow();
    if (selectedRow == null) {
      return "";
    }
    ExtensionCatalogItem selected = extensionCatalogProjection.itemAtRow(selectedRow);
    return selected == null ? "" : selected.description();
  }

  private boolean handleExtensionListKeys(
      KeyEvent keyEvent, java.util.function.Consumer<String> onToggled) {
    if (extensionCatalogNavigation.handleNavigationKey(
        extensionCatalogProjection.rows(), keyEvent)) {
      return true;
    }
    if (!keyEvent.isSelect()) {
      return false;
    }
    Integer selectedRow = extensionCatalogNavigation.selectedRow();
    if (selectedRow == null) {
      return false;
    }
    ExtensionCatalogItem extension = extensionCatalogProjection.itemAtRow(selectedRow);
    if (extension == null) {
      return false;
    }
    if (extensionCatalogNavigation.select(extension.id())) {
      extensionCatalogPreferences.recordRecentSelection(extension.id());
      extensionCatalogProjection.refreshRows(
          extension.id(), extensionCatalogNavigation, extensionCatalogPreferences);
    } else {
      extensionCatalogNavigation.deselect(extension.id());
      extensionCatalogProjection.reapplyAfterSelectionMutation(
          extensionCatalogNavigation, extensionCatalogPreferences);
    }
    onToggled.accept(extension.name());
    return true;
  }

  private void replaceExtensionCatalog(List<ExtensionCatalogItem> items, String query) {
    Set<String> availableExtensionIds = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      availableExtensionIds.add(item.id());
    }
    extensionCatalogNavigation.retainAvailableSelections(availableExtensionIds);
    extensionCatalogPreferences.retainAvailable(availableExtensionIds);
    extensionCatalogProjection.replaceCatalog(
        items, query, extensionCatalogNavigation, extensionCatalogPreferences, ignored -> {});
  }

  private FooterSnapshot footerSnapshot() {
    return new FooterSnapshot(
        isGenerationInProgress(),
        focusTarget,
        commandPaletteVisible,
        helpOverlayVisible,
        postGenerationMenu.isVisible(),
        effectiveStatusMessage(),
        activeErrorDetails(),
        verboseErrorDetails(),
        showErrorDetails,
        postGenerationMenu.successHint(),
        preGeneratePlan(),
        resolvedTargetPathForFooter(),
        focusedFieldValueForFooter(),
        focusedFieldIssueForFooter());
  }

  private String resolvedTargetPathForFooter() {
    if (helpOverlayVisible || commandPaletteVisible || postGenerationMenu.isVisible()) {
      return "";
    }
    try {
      return resolveGeneratedProjectDirectory().toString();
    } catch (RuntimeException pathError) {
      return "";
    }
  }

  private String focusedFieldValueForFooter() {
    if (helpOverlayVisible || commandPaletteVisible || postGenerationMenu.isVisible()) {
      return "";
    }
    return switch (focusTarget) {
      case GROUP_ID, ARTIFACT_ID, VERSION, PACKAGE_NAME, OUTPUT_DIR ->
          inputStates.get(focusTarget).text();
      default -> "";
    };
  }

  private String focusedFieldIssueForFooter() {
    if (helpOverlayVisible || commandPaletteVisible || postGenerationMenu.isVisible()) {
      return "";
    }
    if (!hasValidationErrorFor(focusTarget)) {
      return "";
    }
    String fieldName = UiFocusTargets.nameOf(focusTarget);
    return validation.errors().stream()
        .filter(error -> error.field().equalsIgnoreCase(fieldName))
        .findFirst()
        .map(error -> error.message())
        .orElse("");
  }

  @Override
  public UiAction handleCommandPaletteKey(KeyEvent keyEvent) {
    if (!commandPaletteVisible) {
      return null;
    }
    if (keyEvent.isCtrlC()) {
      cancelPendingAsyncOperations();
      return UiAction.handled(true);
    }
    if (keyEvent.isCancel()) {
      return routeIntent(
          new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.Dismiss()));
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      dispatchIntent(
          new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.MoveSelection(-1)));
      return UiAction.handled(false);
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      dispatchIntent(
          new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.MoveSelection(1)));
      return UiAction.handled(false);
    }
    if (keyEvent.isHome()) {
      dispatchIntent(
          new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.JumpHome()));
      return UiAction.handled(false);
    }
    if (keyEvent.isEnd()) {
      dispatchIntent(
          new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.JumpEnd()));
      return UiAction.handled(false);
    }
    if (UiKeyMatchers.isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < UiTextConstants.COMMAND_PALETTE_ENTRIES.size()) {
        dispatchIntent(
            new UiIntent.CommandPaletteIntent(
                new UiIntent.CommandPaletteCommand.SelectIndex(selected)));
        routeIntent(
            new UiIntent.CommandPaletteIntent(
                new UiIntent.CommandPaletteCommand.ConfirmSelection()));
      }
      return UiAction.handled(false);
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      return routeIntent(
          new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.ConfirmSelection()));
    }
    return UiAction.handled(false);
  }

  @Override
  public UiAction handlePostGenerationMenuKey(KeyEvent keyEvent) {
    UiIntent.PostGenerationCommand command = postGenerationMenu.handleKey(keyEvent);
    if (command == null) {
      return null;
    }
    return routeIntent(new UiIntent.PostGenerationIntent(command));
  }

  private void applyReducerState(UiState reducedState) {
    focusTarget = reducedState.focusTarget();
    statusMessage = reducedState.statusMessage();
    errorMessage = reducedState.errorMessage();
    verboseErrorDetails = reducedState.verboseErrorDetails();
    showErrorDetails = reducedState.showErrorDetails();
    submitRequested = reducedState.submitRequested();
    submitBlockedByValidation = reducedState.submitBlockedByValidation();
    submitBlockedByTargetConflict = reducedState.submitBlockedByTargetConflict();
    catalogLoadState = reducedState.catalogLoad().state();
    startupOverlayVisible = reducedState.startupOverlay().visible();
    commandPaletteVisible = reducedState.overlays().commandPaletteVisible();
    helpOverlayVisible = reducedState.overlays().helpOverlayVisible();
    commandPaletteSelection = reducedState.commandPaletteSelection();
    postGenerationMenu.apply(reducedState.postGeneration());
  }

  private ReduceResult dispatchIntent(UiIntent intent) {
    ReduceResult reduceResult = uiReducer.reduce(uiState(false), intent);
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), uiEffectsPort);
    return reduceResult;
  }

  private UiAction routeIntent(UiIntent intent) {
    UiAction action = dispatchIntent(intent).action();
    return action.handled() ? action : null;
  }

  private void exportRecipeAndLockFiles() {
    Path generatedProjectPath = postGenerationMenu.lastGeneratedProjectPath();
    if (generatedProjectPath == null) {
      statusMessage = "Cannot export Forgefile: no generated project path";
      return;
    }
    try {
      List<String> selectedExtensions = extensionCatalogNavigation.selectedExtensionIds();
      ForgefileLock lock =
          ForgefileLock.of(
              request.platformStream(),
              request.buildTool(),
              request.javaVersion(),
              List.of(),
              selectedExtensions);
      Forgefile forgefile =
          new Forgefile(
              request.groupId(),
              request.artifactId(),
              request.version(),
              request.packageName(),
              request.outputDirectory(),
              request.platformStream(),
              request.buildTool(),
              request.javaVersion(),
              List.of(),
              selectedExtensions,
              lock);
      Path forgefilePath = generatedProjectPath.resolve(UiTextConstants.FORGE_FILE_NAME);
      ForgefileStore.save(forgefilePath, forgefile);
      statusMessage = "Exported Forgefile to " + forgefilePath;
    } catch (RuntimeException runtimeException) {
      statusMessage = "Failed to export Forgefile: " + runtimeException.getMessage();
    }
  }

  private void executeCommandPaletteActionEffect(CommandPaletteAction action) {
    switch (action) {
      case FOCUS_EXTENSION_SEARCH -> focusExtensionSearch();
      case FOCUS_EXTENSION_LIST -> focusExtensionList();
      case TOGGLE_FAVORITES_FILTER -> toggleFavoritesOnlyFilter();
      case TOGGLE_SELECTED_FILTER -> toggleSelectedOnlyFilter();
      case CYCLE_PRESET_FILTER -> cyclePresetFilter();
      case JUMP_TO_FAVORITE -> jumpToFavorite();
      case CYCLE_CATEGORY_FILTER -> {
        if (focusTarget != FocusTarget.EXTENSION_LIST) {
          focusExtensionList();
        }
        cycleCategoryFilter();
      }
      case TOGGLE_CATEGORY -> {
        if (focusTarget != FocusTarget.EXTENSION_LIST) {
          focusExtensionList();
        }
        toggleCategoryCollapseAtSelection();
      }
      case OPEN_ALL_CATEGORIES -> {
        if (focusTarget != FocusTarget.EXTENSION_LIST) {
          focusExtensionList();
        }
        expandAllCategories();
      }
      case RELOAD_CATALOG -> requestCatalogReload();
      case TOGGLE_ERROR_DETAILS -> toggleErrorDetails();
    }
  }

  @Override
  public void toggleCommandPalette() {
    dispatchIntent(
        new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.ToggleVisibility()));
  }

  @Override
  public UiAction handleHelpOverlayKey(KeyEvent keyEvent) {
    if (!helpOverlayVisible) {
      return null;
    }
    if (keyEvent.isCtrlC()) {
      cancelPendingAsyncOperations();
      return UiAction.handled(true);
    }
    if (keyEvent.isCancel() || AppKeyActions.isHelpOverlayToggleKey(keyEvent)) {
      return routeIntent(new UiIntent.HelpOverlayIntent(new UiIntent.HelpOverlayCommand.Dismiss()));
    }
    if (isCommandPaletteToggleKey(keyEvent)) {
      toggleCommandPalette();
      return UiAction.handled(false);
    }
    return UiAction.handled(false);
  }

  @Override
  public void toggleHelpOverlay() {
    dispatchIntent(
        new UiIntent.HelpOverlayIntent(new UiIntent.HelpOverlayCommand.ToggleVisibility()));
  }

  private boolean isMetadataFocused() {
    return focusTarget == FocusTarget.GROUP_ID
        || focusTarget == FocusTarget.ARTIFACT_ID
        || focusTarget == FocusTarget.VERSION
        || focusTarget == FocusTarget.PACKAGE_NAME
        || focusTarget == FocusTarget.OUTPUT_DIR
        || focusTarget == FocusTarget.PLATFORM_STREAM
        || focusTarget == FocusTarget.BUILD_TOOL
        || focusTarget == FocusTarget.JAVA_VERSION;
  }

  private boolean isExtensionPanelFocused() {
    return focusTarget == FocusTarget.EXTENSION_SEARCH
        || focusTarget == FocusTarget.EXTENSION_LIST
        || focusTarget == FocusTarget.SUBMIT;
  }

  private String metadataPanelTitle() {
    return validation.isValid() ? "Project Metadata" : "Project Metadata [invalid]";
  }

  private boolean hasValidationErrorFor(FocusTarget target) {
    if ((!isTextInputFocus(target) && !MetadataSelectorManager.isSelectorFocus(target))
        || target == FocusTarget.EXTENSION_SEARCH) {
      return false;
    }
    String fieldName = UiFocusTargets.nameOf(target);
    return validation.errors().stream()
        .anyMatch(error -> error.field().equalsIgnoreCase(fieldName));
  }

  private String activeErrorDetails() {
    if (!errorMessage.isBlank()) {
      return errorMessage;
    }
    if (!catalogLoadState.errorMessage().isBlank()) {
      return catalogLoadState.errorMessage();
    }
    return "";
  }

  private String verboseErrorDetails() {
    if (!verboseErrorDetails.isBlank()) {
      return verboseErrorDetails;
    }
    if (!catalogLoadState.errorMessage().isBlank()) {
      return catalogLoadState.errorMessage();
    }
    return "";
  }

  private void scheduleFilteredExtensionsRefresh() {
    String query = inputStates.get(FocusTarget.EXTENSION_SEARCH).text();
    statusMessage = "Searching extensions...";
    extensionCatalogProjection.scheduleRefresh(
        query,
        extensionCatalogNavigation,
        extensionCatalogPreferences,
        this::updateExtensionFilterStatus);
  }

  private void syncMetadataSelectors() {
    MetadataDto metadataSnapshot = metadataCompatibility.metadataSnapshot();
    MetadataSelectorManager.ResolvedSelections resolved =
        metadataSelectors.sync(
            metadataSnapshot, request.platformStream(), request.buildTool(), request.javaVersion());

    request =
        CliPrefillMapper.map(
            new CliPrefill(
                inputStates.get(FocusTarget.GROUP_ID).text(),
                inputStates.get(FocusTarget.ARTIFACT_ID).text(),
                inputStates.get(FocusTarget.VERSION).text(),
                inputStates.get(FocusTarget.PACKAGE_NAME).text(),
                inputStates.get(FocusTarget.OUTPUT_DIR).text(),
                resolved.platformStream(),
                resolved.buildTool(),
                resolved.javaVersion()));
  }

  private boolean handleMetadataSelectorKey(FocusTarget target, KeyEvent keyEvent) {
    if (keyEvent.isLeft()
        || UiKeyMatchers.isVimLeftKey(keyEvent)
        || keyEvent.isUp()
        || UiKeyMatchers.isVimUpKey(keyEvent)) {
      return applySelectorCycle(target, -1);
    }
    if (keyEvent.isRight()
        || UiKeyMatchers.isVimRightKey(keyEvent)
        || keyEvent.isDown()
        || UiKeyMatchers.isVimDownKey(keyEvent)) {
      return applySelectorCycle(target, 1);
    }
    if (keyEvent.isHome()) {
      return applySelectorEdge(target, true);
    }
    if (keyEvent.isEnd()) {
      return applySelectorEdge(target, false);
    }
    return false;
  }

  private boolean applySelectorCycle(FocusTarget target, int delta) {
    String newValue = metadataSelectors.cycle(target, selectorValue(target), delta);
    if (newValue == null) {
      return false;
    }
    applySelector(target, newValue);
    return true;
  }

  private boolean applySelectorEdge(FocusTarget target, boolean first) {
    String newValue = metadataSelectors.selectEdge(target, first);
    if (newValue == null) {
      return false;
    }
    applySelector(target, newValue);
    return true;
  }

  private void applySelector(FocusTarget target, String selectedValue) {
    String platformStream = request.platformStream();
    String buildTool = request.buildTool();
    String javaVersion = request.javaVersion();

    if (target == FocusTarget.PLATFORM_STREAM) {
      platformStream = selectedValue;
    } else if (target == FocusTarget.BUILD_TOOL) {
      buildTool = selectedValue;
    } else if (target == FocusTarget.JAVA_VERSION) {
      javaVersion = selectedValue;
    }

    request =
        CliPrefillMapper.map(
            new CliPrefill(
                inputStates.get(FocusTarget.GROUP_ID).text(),
                inputStates.get(FocusTarget.ARTIFACT_ID).text(),
                inputStates.get(FocusTarget.VERSION).text(),
                inputStates.get(FocusTarget.PACKAGE_NAME).text(),
                inputStates.get(FocusTarget.OUTPUT_DIR).text(),
                platformStream,
                buildTool,
                javaVersion));
    syncMetadataSelectors();
    statusMessage = selectorStatusMessage(target);
  }

  private String selectorStatusMessage(FocusTarget target) {
    return switch (target) {
      case PLATFORM_STREAM ->
          "Platform stream selected: "
              + (request.platformStream().isBlank() ? "default" : request.platformStream());
      case BUILD_TOOL -> "Build tool selected: " + request.buildTool();
      case JAVA_VERSION -> "Java version selected: " + request.javaVersion();
      default -> "Selection updated";
    };
  }

  private String selectorValue(FocusTarget target) {
    return switch (target) {
      case PLATFORM_STREAM -> request.platformStream();
      case BUILD_TOOL -> request.buildTool();
      case JAVA_VERSION -> request.javaVersion();
      default -> "";
    };
  }

  private List<String> selectorDisplayOptions(FocusTarget target, List<String> options) {
    if (target != FocusTarget.PLATFORM_STREAM) {
      return options;
    }
    MetadataDto metadataSnapshot = metadataCompatibility.metadataSnapshot();
    List<String> displayOptions = new ArrayList<>(options.size());
    for (String option : options) {
      displayOptions.add(
          MetadataSelectorManager.optionDisplayLabel(target, option, metadataSnapshot));
    }
    return List.copyOf(displayOptions);
  }

  private void rebuildRequestFromInputs() {
    CliPrefill prefill =
        new CliPrefill(
            inputStates.get(FocusTarget.GROUP_ID).text(),
            inputStates.get(FocusTarget.ARTIFACT_ID).text(),
            inputStates.get(FocusTarget.VERSION).text(),
            inputStates.get(FocusTarget.PACKAGE_NAME).text(),
            inputStates.get(FocusTarget.OUTPUT_DIR).text(),
            request.platformStream(),
            request.buildTool(),
            request.javaVersion());
    request = CliPrefillMapper.map(prefill);
  }

  private void revalidate() {
    ValidationReport report = requestValidator.validate(request);
    validation = report.merge(metadataCompatibility.validate(request));
  }

  private void cancelPendingAsyncOperations() {
    catalogLoadCoordinator.cancel(catalogLoadIntentPort);
    extensionCatalogProjection.cancelPendingAsync();
  }

  private void reconcileGenerationCompletionIfDone() {
    generationFlowCoordinator.reconcileCompletionIfDone(this);
  }

  @Override
  public boolean isGenerationInProgress() {
    return generationStateTracker.isInProgress();
  }

  private GenerationRequest toGenerationRequest() {
    return new GenerationRequest(
        request.groupId(),
        request.artifactId(),
        request.version(),
        request.platformStream(),
        request.buildTool(),
        request.javaVersion(),
        extensionCatalogNavigation.selectedExtensionIds());
  }

  private Path resolveGeneratedProjectDirectory() {
    Path outputRoot = OutputPathResolver.resolveOutputRoot(request.outputDirectory());
    return outputRoot.resolve(request.artifactId()).normalize();
  }

  private void prepareForGenerationEffect() {
    generationStatusNotice = "";
    resetGenerationStateAfterTerminalOutcome();
  }

  private void runCatalogLoadEffect(ExtensionCatalogLoader loader) {
    catalogLoadCoordinator.startLoad(loader, catalogLoadIntentPort);
  }

  private void runCatalogReloadEffect() {
    catalogLoadCoordinator.requestReload(catalogLoadIntentPort);
  }

  private void applyCatalogLoadSuccessEffect(CatalogLoadSuccess success) {
    if (success.metadata() != null) {
      metadataCompatibility = MetadataCompatibilityContext.success(success.metadata());
      syncMetadataSelectors();
      revalidate();
    }
    replaceExtensionCatalog(success.items(), inputStates.get(FocusTarget.EXTENSION_SEARCH).text());
    extensionCatalogProjection.setPresetExtensionsByName(
        success.presetExtensionsByName(),
        extensionCatalogNavigation,
        extensionCatalogPreferences,
        ignored -> {});
  }

  private void cancelPendingAsyncEffect() {
    cancelPendingAsyncOperations();
  }

  private void exportRecipeAndLockEffect() {
    exportRecipeAndLockFiles();
  }

  private void resetGenerationStateAfterTerminalOutcome() {
    generationStateTracker.resetAfterTerminalOutcome();
  }

  private void startGenerationEffect() {
    generationFlowCoordinator.startFlow(
        projectGenerationRunner, toGenerationRequest(), resolveGeneratedProjectDirectory(), this);
  }

  private void transitionGenerationStateEffect(GenerationState targetState) {
    transitionGenerationState(targetState);
  }

  private void requestGenerationCancellationEffect() {
    generationFlowCoordinator.requestCancellation(this);
  }

  private void requestAsyncRepaintEffect() {
    requestAsyncRepaint();
  }

  private void applyMetadataSelectorKeyEffect(FocusTarget target, KeyEvent keyEvent) {
    if (handleMetadataSelectorKey(target, keyEvent)) {
      revalidate();
      dispatchSubmitEditRecovery();
    }
  }

  private void applyTextInputKeyEffect(FocusTarget target, KeyEvent keyEvent) {
    if (!handleTextInputKey(inputStates.get(target), keyEvent)) {
      return;
    }
    if (target == FocusTarget.EXTENSION_SEARCH) {
      scheduleFilteredExtensionsRefresh();
      return;
    }
    rebuildRequestFromInputs();
    revalidate();
    dispatchSubmitEditRecovery();
  }

  @Override
  public String generationStateLabel() {
    return generationStateTracker.stateLabel();
  }

  @Override
  public void scheduleOnRenderThread(Runnable task) {
    scheduler.schedule(Duration.ZERO, task);
  }

  private boolean transitionGenerationState(GenerationState targetState) {
    return generationStateTracker.transitionTo(targetState);
  }

  static boolean isValidTransition(GenerationState currentState, GenerationState targetState) {
    return GenerationStateTracker.isValidTransition(currentState, targetState);
  }

  private static String nextStepCommand(String buildTool) {
    String normalizedBuildTool = BuildToolCodec.toUiValue(buildTool);
    if ("gradle".equals(normalizedBuildTool) || "gradle-kotlin-dsl".equals(normalizedBuildTool)) {
      return "./gradlew quarkusDev";
    }
    return "mvn quarkus:dev";
  }

  private static boolean isTextInputFocus(FocusTarget focusTarget) {
    return UiFocusPredicates.isTextInputFocus(focusTarget);
  }

  private static String panelTitle(String baseTitle, boolean focused) {
    return focused ? baseTitle + " [focus]" : baseTitle;
  }

  private UiAction handleTickEvent() {
    boolean loading = catalogLoadState.isLoading();
    CatalogLoadCoordinator.StartupOverlayTickResult overlayTick =
        catalogLoadCoordinator.tickStartupOverlay(catalogLoadState);
    if (startupOverlayVisible != overlayTick.visible()) {
      dispatchIntent(new UiIntent.StartupOverlayVisibilityIntent(overlayTick.visible()));
    }
    boolean shouldRender = loading || overlayTick.visible() || overlayTick.repaintRequired();
    if (asyncRepaintSignal.consume()) {
      shouldRender = true;
    }
    if (generationStateTracker.currentState() == GenerationState.LOADING) {
      if (!generationFlowCoordinator.isCancellationRequested()) {
        generationStateTracker.tick(generationFlowCoordinator.elapsedMillisSinceStart());
        generationStatusNotice = "";
      }
      shouldRender = true;
    }
    return shouldRender ? new UiAction(true, false) : UiAction.ignored();
  }

  private void updateExtensionFilterStatus(int filteredCount) {
    if (isGenerationInProgress()) {
      return;
    }
    statusMessage = "Extensions filtered: " + filteredCount;
  }

  private void requestAsyncRepaint() {
    asyncRepaintSignal.request();
  }

  private void toggleFavoriteAtSelection() {
    statusMessage =
        extensionInteraction.toggleFavoriteAtSelection(this::updateExtensionFilterStatus);
  }

  private void cycleCategoryFilter() {
    statusMessage = extensionInteraction.cycleCategoryFilter(this::updateExtensionFilterStatus);
  }

  private void clearCategoryFilter() {
    String msg = extensionInteraction.clearCategoryFilter(this::updateExtensionFilterStatus);
    if (msg != null) {
      statusMessage = msg;
    }
  }

  private void clearSelectedExtensions() {
    statusMessage = extensionInteraction.clearSelectedExtensions();
  }

  private void toggleCategoryCollapseAtSelection() {
    statusMessage = extensionInteraction.toggleCategoryCollapseAtSelection();
  }

  private void expandAllCategories() {
    statusMessage = extensionInteraction.expandAllCategories();
  }

  private void jumpToFavorite() {
    if (focusTarget != FocusTarget.EXTENSION_LIST) {
      focusExtensionList();
    }
    statusMessage = extensionInteraction.jumpToFavorite();
  }

  private void jumpToAdjacentCategorySection(boolean forward) {
    statusMessage = extensionInteraction.jumpToAdjacentSection(forward);
  }

  private void handleExtensionListHierarchyLeft() {
    String msg = extensionInteraction.handleHierarchyLeft();
    if (msg != null) {
      statusMessage = msg;
    }
  }

  private void handleExtensionListHierarchyRight() {
    String msg = extensionInteraction.handleHierarchyRight();
    if (msg != null) {
      statusMessage = msg;
    }
  }

  private void toggleFavoritesOnlyFilter() {
    statusMessage =
        extensionInteraction.toggleFavoritesOnlyFilter(this::updateExtensionFilterStatus);
  }

  private void toggleSelectedOnlyFilter() {
    statusMessage =
        extensionInteraction.toggleSelectedOnlyFilter(this::updateExtensionFilterStatus);
  }

  private void cyclePresetFilter() {
    statusMessage = extensionInteraction.cyclePresetFilter(this::updateExtensionFilterStatus);
  }

  private void clearPresetFilter() {
    statusMessage = extensionInteraction.clearPresetFilter(this::updateExtensionFilterStatus);
  }

  private void toggleErrorDetails() {
    dispatchIntent(new UiIntent.ToggleErrorDetailsIntent(!activeErrorDetails().isBlank()));
  }

  private void requestCatalogReload() {
    dispatchIntent(new UiIntent.CatalogReloadRequestedIntent());
  }

  private String effectiveStatusMessage() {
    if (generationStateTracker.currentState() != GenerationState.LOADING) {
      return statusMessage;
    }
    if (!generationStatusNotice.isBlank()) {
      return generationStatusNotice;
    }
    if (generationFlowCoordinator.isCancellationRequested()) {
      return statusMessage;
    }
    if (generationStateTracker.progressPhase().isBlank()) {
      return "Generation in progress. Press Esc to cancel.";
    }
    return "Generation in progress: " + generationStateTracker.progressPhase();
  }

  private static boolean shouldToggleHelpOverlay(KeyEvent keyEvent, FocusTarget currentFocus) {
    if (!AppKeyActions.isHelpOverlayToggleKey(keyEvent)) {
      return false;
    }
    if (currentFocus == FocusTarget.EXTENSION_SEARCH) {
      return true;
    }
    return !isTextInputFocus(currentFocus);
  }

  private static boolean isUpNavigation(KeyEvent keyEvent) {
    return keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent);
  }

  private static boolean shouldFocusExtensionSearch(KeyEvent keyEvent, FocusTarget currentFocus) {
    if (AppKeyActions.isFocusExtensionSearchSlashKey(keyEvent)) {
      return !isTextInputFocus(currentFocus) && currentFocus != FocusTarget.EXTENSION_SEARCH;
    }
    return AppKeyActions.isFocusExtensionSearchCtrlKey(keyEvent);
  }

  private String currentTargetConflictErrorMessage() {
    try {
      Path generatedProjectDirectory = resolveGeneratedProjectDirectory();
      if (!Files.exists(generatedProjectDirectory)) {
        return "";
      }
      return "Output directory already exists: "
          + generatedProjectDirectory.toAbsolutePath().normalize();
    } catch (RuntimeException runtimeException) {
      return "";
    }
  }

  private void dispatchSubmitEditRecovery() {
    dispatchIntent(
        new UiIntent.SubmitEditRecoveryIntent(
            new UiIntent.SubmitRecoveryContext(currentTargetConflictErrorMessage())));
  }

  private void moveFocusedInputCursorToEndAfterSubmitFeedback() {
    if (isTextInputFocus(focusTarget)
        && (focusTarget == FocusTarget.OUTPUT_DIR || hasValidationErrorFor(focusTarget))) {
      inputStates.get(focusTarget).moveCursorToEnd();
    }
  }

  private void focusExtensionSearch() {
    dispatchIntent(new UiIntent.ExtensionPanelFocusIntent(FocusTarget.EXTENSION_SEARCH));
    inputStates.get(FocusTarget.EXTENSION_SEARCH).moveCursorToEnd();
  }

  private void focusExtensionList() {
    dispatchIntent(new UiIntent.ExtensionPanelFocusIntent(FocusTarget.EXTENSION_LIST));
  }

  private void clearExtensionSearchFilter() {
    TextInputState searchState = inputStates.get(FocusTarget.EXTENSION_SEARCH);
    searchState.setText("");
    searchState.moveCursorToStart();
    extensionCatalogProjection.refreshNow(
        "",
        extensionCatalogNavigation,
        extensionCatalogPreferences,
        this::updateExtensionFilterStatus);
    statusMessage = "Extension search cleared";
  }

  static String catalogLoadedStatusMessage(CatalogSource source, boolean stale) {
    return switch (source) {
      case LIVE -> "Loaded extension catalog from live API";
      case CACHE ->
          stale
              ? "Loaded stale extension catalog from cache"
              : "Loaded extension catalog from cache";
    };
  }

  private void moveFocusToAdjacentInvalidField(boolean forward) {
    List<FocusTarget> invalidTargets = ValidationFocusTargets.orderedInvalid(validation);
    if (invalidTargets.isEmpty()) {
      statusMessage = "No invalid fields";
      return;
    }
    int currentIndex = invalidTargets.indexOf(focusTarget);
    int nextIndex;
    if (currentIndex < 0) {
      nextIndex = forward ? 0 : invalidTargets.size() - 1;
    } else {
      nextIndex = Math.floorMod(currentIndex + (forward ? 1 : -1), invalidTargets.size());
    }
    focusTarget = invalidTargets.get(nextIndex);
    if (isTextInputFocus(focusTarget)) {
      inputStates.get(focusTarget).moveCursorToEnd();
    }
    statusMessage = "Focus moved to invalid field: " + UiFocusTargets.nameOf(focusTarget);
  }

  private String preGeneratePlan() {
    if (isGenerationInProgress() || postGenerationMenu.isVisible()) {
      return "";
    }
    String targetPathDisplay;
    try {
      targetPathDisplay = resolveGeneratedProjectDirectory().toString();
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

  enum GenerationState {
    IDLE,
    VALIDATING,
    LOADING,
    SUCCESS,
    ERROR,
    CANCELLED
  }

  private static boolean handleTextInputKey(TextInputState state, KeyEvent event) {
    if (!UiTextInputKeys.isSupportedEditKey(event)) {
      return false;
    }
    if (event.code() == KeyCode.CHAR) {
      state.insert(event.character());
      return true;
    }
    if (event.isDeleteBackward()) {
      state.deleteBackward();
      return true;
    }
    if (event.isDeleteForward()) {
      state.deleteForward();
      return true;
    }
    if (event.isLeft()) {
      state.moveCursorLeft();
      return true;
    }
    if (event.isRight()) {
      state.moveCursorRight();
      return true;
    }
    if (event.isHome()) {
      state.moveCursorToStart();
      return true;
    }
    if (event.isEnd()) {
      state.moveCursorToEnd();
      return true;
    }
    return false;
  }
}
