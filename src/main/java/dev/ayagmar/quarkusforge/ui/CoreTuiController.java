package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.Forgefile;
import dev.ayagmar.quarkusforge.ForgefileLock;
import dev.ayagmar.quarkusforge.ForgefileStore;
import dev.ayagmar.quarkusforge.IdeDetector;
import dev.ayagmar.quarkusforge.api.BuildToolCodec;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.util.OutputPathResolver;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.select.Select;
import dev.tamboui.widgets.select.SelectState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public final class CoreTuiController
    implements CompactInputRenderer, UiRoutingContext, GenerationFlowCallbacks, UiRenderer.Adapter {
  private static final int METADATA_PANEL_HEIGHT_COMPACT = 4;
  private static final int METADATA_PANEL_HEIGHT_NARROW = 10;
  private static final List<FocusTarget> FOCUS_ORDER =
      List.of(
          FocusTarget.GROUP_ID,
          FocusTarget.ARTIFACT_ID,
          FocusTarget.BUILD_TOOL,
          FocusTarget.PLATFORM_STREAM,
          FocusTarget.VERSION,
          FocusTarget.PACKAGE_NAME,
          FocusTarget.JAVA_VERSION,
          FocusTarget.OUTPUT_DIR,
          FocusTarget.EXTENSION_SEARCH,
          FocusTarget.EXTENSION_LIST,
          FocusTarget.SUBMIT);
  private static final ProjectGenerationRunner NOOP_PROJECT_GENERATION_RUNNER =
      (generationRequest, outputDirectory, cancelled, progressListener) ->
          CompletableFuture.failedFuture(
              new IllegalStateException("Generation flow is not configured in this runtime"));
  private final EnumMap<FocusTarget, TextInputState> inputStates = new EnumMap<>(FocusTarget.class);
  private final ProjectRequestValidator requestValidator = new ProjectRequestValidator();
  private MetadataCompatibilityContext metadataCompatibility;
  private final UiScheduler scheduler;
  private final ExtensionCatalogState extensionCatalogState;
  private final ExtensionInteractionHandler extensionInteraction;
  private final ProjectGenerationRunner projectGenerationRunner;
  private final UiTheme theme;
  private final BodyPanelRenderer bodyPanelRenderer;
  private final Select focusedSelectorWidget;
  private final Select focusedSelectorErrorWidget;
  private final MetadataSelectorManager metadataSelectors = new MetadataSelectorManager();

  private ProjectRequest request;
  private ValidationReport validation;
  private FocusTarget focusTarget;
  private String statusMessage;
  private String errorMessage;
  private String verboseErrorDetails;
  private boolean submitRequested;
  private boolean submitBlockedByValidation;
  private boolean submitBlockedByTargetConflict;
  private final GenerationStateTracker generationStateTracker;
  private final FooterLinesComposer footerLinesComposer;
  private final AsyncRepaintSignal asyncRepaintSignal;
  private final UiEventRouter uiEventRouter;
  private final GenerationFlowCoordinator generationFlowCoordinator;
  private final UiReducer uiReducer;
  private final UiEffectsRunner uiEffectsRunner;
  private final UiStateSnapshotMapper uiStateSnapshotMapper;
  private final UiRenderer uiRenderer;
  private volatile long extensionCatalogLoadToken;
  private CatalogLoadState catalogLoadState = CatalogLoadState.initial();
  private ExtensionCatalogLoader extensionCatalogLoader;
  private boolean showErrorDetails;
  private boolean commandPaletteVisible;
  private boolean helpOverlayVisible;
  private int commandPaletteSelection;
  private final PostGenerationMenuState postGenerationMenu = new PostGenerationMenuState();
  private final StartupOverlayTracker startupOverlay = new StartupOverlayTracker();

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
    submitRequested = false;
    submitBlockedByValidation = false;
    submitBlockedByTargetConflict = false;
    metadataCompatibility = initialState.metadataCompatibility();
    this.scheduler = scheduler;
    this.projectGenerationRunner = projectGenerationRunner;
    theme = UiTheme.loadDefault();
    bodyPanelRenderer = new BodyPanelRenderer(theme);
    focusedSelectorWidget =
        Select.builder()
            .style(UiInputStyles.focusedField(theme, false))
            .selectedColor(theme.color("focus"))
            .indicatorColor(theme.color("focus"))
            .leftIndicator("◀ ")
            .rightIndicator(" ▶")
            .build();
    focusedSelectorErrorWidget =
        Select.builder()
            .style(UiInputStyles.focusedField(theme, true))
            .selectedColor(theme.color("error"))
            .indicatorColor(theme.color("error"))
            .leftIndicator("◀ ")
            .rightIndicator(" ▶")
            .build();
    generationStateTracker = new GenerationStateTracker();
    footerLinesComposer = new FooterLinesComposer();
    asyncRepaintSignal = new AsyncRepaintSignal();
    uiEventRouter = new UiEventRouter();
    generationFlowCoordinator = new GenerationFlowCoordinator();
    uiReducer = new CoreUiReducer();
    uiEffectsRunner = new UiEffectsRunner();
    uiStateSnapshotMapper = new UiStateSnapshotMapper();
    uiRenderer = new UiRenderer();
    extensionCatalogLoadToken = 0L;
    extensionCatalogLoader = null;
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
    for (FocusTarget target : FOCUS_ORDER) {
      if (isTextInputFocus(target)) {
        inputStates.get(target).moveCursorToEnd();
      }
    }
    extensionCatalogState =
        new ExtensionCatalogState(
            scheduler,
            debounceDelay,
            inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
            favoritesStore,
            favoritesPersistenceExecutor);
    extensionInteraction = new ExtensionInteractionHandler(extensionCatalogState);

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
    Objects.requireNonNull(loader);
    extensionCatalogLoader = loader;
    long loadToken = ++extensionCatalogLoadToken;
    catalogLoadState = CatalogLoadState.loadingFrom(catalogLoadState);
    statusMessage = "Loading extension catalog...";
    startupOverlay.activateIfFirstLoad(loadToken);
    requestAsyncRepaint();

    CompletableFuture<ExtensionCatalogLoadResult> loadFuture;
    try {
      loadFuture = loader.load();
    } catch (RuntimeException runtimeException) {
      loadFuture = CompletableFuture.failedFuture(runtimeException);
    }
    if (loadFuture == null) {
      loadFuture =
          CompletableFuture.failedFuture(new IllegalStateException("loader returned null future"));
    }

    loadFuture.whenComplete(
        (result, throwable) -> {
          scheduleOnRenderThread(() -> applyCatalogLoadCompletion(loadToken, result, throwable));
        });
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
    if (extensionCatalogState.favoritesOnlyFilterEnabled()) {
      toggleFavoritesOnlyFilter();
      return UiAction.handled(false);
    }
    if (extensionCatalogState.selectedOnlyFilterEnabled()) {
      toggleSelectedOnlyFilter();
      return UiAction.handled(false);
    }
    if (!extensionCatalogState.activePresetFilterName().isBlank()) {
      clearPresetFilter();
      return UiAction.handled(false);
    }
    if (!extensionCatalogState.activeCategoryFilterTitle().isBlank()) {
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
      ReduceResult reduceResult =
          uiReducer.reduce(uiState(), new UiIntent.CancelGenerationIntent());
      applyReducerState(reduceResult.nextState());
      uiEffectsRunner.run(reduceResult.effects(), this);
      return reduceResult.action();
    }
    cancelPendingAsyncOperations();
    return UiAction.handled(true);
  }

  @Override
  public UiAction handleWhileGenerationInProgress(KeyEvent keyEvent) {
    if (keyEvent.isConfirm()) {
      statusMessage = "Generation already in progress. Press Esc to cancel.";
      return UiAction.handled(false);
    }
    statusMessage = "Generation in progress. Press Esc to cancel.";
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
    ReduceResult reduceResult =
        uiReducer.reduce(uiState(), new UiIntent.FocusNavigationIntent(keyEvent, focusTarget));
    if (reduceResult.effects().isEmpty()) {
      return null;
    }
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
    return reduceResult.action();
  }

  @Override
  public UiAction handleSubmitFlow(KeyEvent keyEvent) {
    if (keyEvent.isConfirm() || AppKeyActions.isGenerateShortcutKey(keyEvent)) {
      handleSubmitRequest();
      return UiAction.handled(false);
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
        && extensionCatalogState.isSelectionAtTop()) {
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
        && extensionCatalogState.isCategorySectionHeaderSelected()) {
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
        && extensionCatalogState.handleListKeys(
            keyEvent, toggledName -> statusMessage = "Toggled extension: " + toggledName)) {
      return UiAction.handled(false);
    }
    return null;
  }

  @Override
  public UiAction handleMetadataSelectorFlow(KeyEvent keyEvent) {
    ReduceResult reduceResult =
        uiReducer.reduce(uiState(), new UiIntent.MetadataInputIntent(keyEvent, focusTarget));
    if (reduceResult.effects().isEmpty()) {
      return null;
    }
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
    return reduceResult.action();
  }

  @Override
  public UiAction handleTextInputFlow(KeyEvent keyEvent) {
    ReduceResult reduceResult =
        uiReducer.reduce(uiState(), new UiIntent.TextInputIntent(keyEvent, focusTarget));
    if (reduceResult.effects().isEmpty()) {
      return null;
    }
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
    return reduceResult.action();
  }

  @Override
  public void beforeGenerationStart() {
    postGenerationMenu.resetForNewGeneration();
    extensionCatalogState.cancelPendingAsync();
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
    statusMessage = "Submit ignored in state: " + stateLabel;
  }

  @Override
  public void onProgress(GenerationProgressUpdate progressUpdate) {
    generationStateTracker.updateProgress(progressUpdate);
    ReduceResult reduceResult =
        uiReducer.reduce(uiState(), new UiIntent.GenerationProgressIntent(progressUpdate));
    applyReducerState(reduceResult.nextState());
  }

  @Override
  public void onGenerationSuccess(Path generatedPath) {
    ReduceResult reduceResult =
        uiReducer.reduce(
            uiState(),
            new UiIntent.GenerationSuccessIntent(
                generatedPath, nextStepCommand(request.buildTool())));
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
  }

  @Override
  public void onGenerationCancelled() {
    ReduceResult reduceResult =
        uiReducer.reduce(uiState(), new UiIntent.GenerationCancelledIntent());
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
  }

  @Override
  public void onGenerationFailed(Throwable cause) {
    ReduceResult reduceResult =
        uiReducer.reduce(
            uiState(),
            new UiIntent.GenerationFailedIntent(
                ErrorMessageMapper.userFriendlyError(cause),
                ErrorMessageMapper.verboseDetails(cause)));
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
  }

  @Override
  public void onCancellationRequested() {
    ReduceResult reduceResult =
        uiReducer.reduce(uiState(), new UiIntent.GenerationCancellationRequestedIntent());
    applyReducerState(reduceResult.nextState());
  }

  public void setStartupOverlayMinDuration(Duration minimumDuration) {
    Objects.requireNonNull(minimumDuration);
    startupOverlay.setMinDuration(minimumDuration);
  }

  public void render(Frame frame) {
    reconcileGenerationCompletionIfDone();
    uiRenderer.render(frame, uiState(), this);
  }

  UiState uiState() {
    MetadataPanelSnapshot metadataPanelSnapshot = metadataPanelSnapshot();
    ExtensionsPanelSnapshot extensionsPanelSnapshot = extensionsPanelSnapshot();
    FooterSnapshot footerSnapshot = footerSnapshot();
    boolean startupOverlayVisible = isStartupOverlayVisible();
    List<String> startupStatusLines = startupStatusLines();
    return uiStateSnapshotMapper.map(
        request,
        validation,
        focusTarget,
        statusMessage,
        errorMessage,
        verboseErrorDetails,
        submitRequested,
        submitBlockedByValidation,
        submitBlockedByTargetConflict,
        commandPaletteSelection,
        metadataPanelSnapshot,
        extensionsPanelSnapshot,
        footerSnapshot,
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
        new UiState.CatalogLoadView(
            catalogLoadState.isLoading(),
            catalogLoadState.sourceLabel(),
            catalogLoadState.isStale(),
            catalogLoadState.errorMessage()),
        new UiState.PostGenerationView(
            postGenerationMenu.isVisible(),
            postGenerationMenu.isGithubVisibilityMenuVisible(),
            postGenerationMenu.actionSelection(),
            postGenerationMenu.githubVisibilitySelection(),
            postGenerationMenu.actionLabels(),
            postGenerationMenu.successHint()),
        new UiState.StartupOverlayView(startupOverlayVisible, startupStatusLines),
        new UiState.ExtensionView(
            extensionCatalogState.filteredExtensions().size(),
            extensionCatalogState.totalCatalogExtensionCount(),
            extensionCatalogState.selectedExtensionCount(),
            extensionCatalogState.favoritesOnlyFilterEnabled(),
            extensionCatalogState.selectedOnlyFilterEnabled(),
            extensionCatalogState.activePresetFilterName(),
            extensionCatalogState.activeCategoryFilterTitle(),
            inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
            extensionCatalogState.focusedExtensionId()));
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
    return extensionCatalogState.selectedExtensionIds();
  }

  String statusMessage() {
    return statusMessage;
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
    return extensionCatalogState.filteredExtensions().size();
  }

  String firstFilteredExtensionId() {
    List<ExtensionCatalogItem> filteredExtensions = extensionCatalogState.filteredExtensions();
    return filteredExtensions.isEmpty() ? "" : filteredExtensions.getFirst().id();
  }

  List<String> filteredExtensionIds() {
    return extensionCatalogState.filteredExtensions().stream()
        .map(ExtensionCatalogItem::id)
        .toList();
  }

  List<String> catalogSectionHeaders() {
    return extensionCatalogState.filteredRows().stream()
        .filter(ExtensionCatalogRow::isSectionHeader)
        .map(ExtensionCatalogRow::label)
        .toList();
  }

  boolean favoritesOnlyFilterEnabled() {
    return extensionCatalogState.favoritesOnlyFilterEnabled();
  }

  int favoriteExtensionCount() {
    return extensionCatalogState.favoriteExtensionCount();
  }

  String focusedListExtensionId() {
    return extensionCatalogState.focusedExtensionId();
  }

  @Override
  public List<String> composeFooterLines(int width, FooterSnapshot footerSnapshot) {
    return footerLinesComposer.compose(width, footerSnapshot);
  }

  @Override
  public int estimateFooterHeight(List<String> lines, int availableWidth) {
    return estimateFooterHeightInternal(lines, availableWidth);
  }

  @Override
  public void renderHeader(Frame frame, Rect area) {
    String text = "  QUARKUS FORGE  ─  Keyboard-first project generator";
    Paragraph header =
        Paragraph.builder()
            .text(text)
            .style(Style.EMPTY.fg(theme.color("accent")).bold())
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(header, area);
  }

  @Override
  public void renderBody(
      Frame frame,
      Rect area,
      MetadataPanelSnapshot metadataPanelSnapshot,
      ExtensionsPanelSnapshot extensionsPanelSnapshot) {
    int metadataHeight =
        area.width() < UiLayoutConstants.NARROW_WIDTH_THRESHOLD
            ? METADATA_PANEL_HEIGHT_NARROW
            : METADATA_PANEL_HEIGHT_COMPACT;
    List<Rect> bodyLayout =
        Layout.vertical()
            .constraints(Constraint.length(metadataHeight), Constraint.fill())
            .split(area);

    bodyPanelRenderer.renderMetadataPanel(
        frame,
        bodyLayout.get(0),
        metadataPanelSnapshot,
        this,
        CoreTuiController::panelTitle,
        this::panelBorderStyle);
    bodyPanelRenderer.renderExtensionsPanel(
        frame,
        bodyLayout.get(1),
        extensionsPanelSnapshot,
        inputStates.get(FocusTarget.EXTENSION_SEARCH),
        extensionCatalogState.listState(),
        CoreTuiController::panelTitle,
        this::panelBorderStyle,
        extensionCatalogState::isSelected,
        extensionCatalogState::isFavorite);
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
        extensionCatalogState.favoritesOnlyFilterEnabled(),
        extensionCatalogState.selectedOnlyFilterEnabled(),
        extensionCatalogState.favoriteExtensionCount(),
        extensionCatalogState.activePresetFilterName(),
        extensionCatalogState.activeCategoryFilterTitle(),
        validation.errors().size(),
        extensionCatalogState.filteredExtensions().size(),
        extensionCatalogState.totalCatalogExtensionCount(),
        extensionCatalogState.filteredRows(),
        extensionCatalogState.selectedExtensionIds(),
        inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
        extensionCatalogState.focusedExtensionDescription());
  }

  @Override
  public void renderCompactSelector(
      Frame frame,
      Rect area,
      String label,
      String value,
      FocusTarget target,
      int selectedIndex,
      int totalOptions) {
    if (focusTarget == target && isMetadataSelectorFocus(target)) {
      renderCompactFocusedSelector(frame, area, label, target, selectedIndex);
      return;
    }
    String displayValue = value.isBlank() ? "default" : value;
    boolean focused = focusTarget == target;

    String positionHint =
        totalOptions > 1 ? " (" + (selectedIndex + 1) + "/" + totalOptions + ")" : "";
    if (focused) {
      displayValue = "◀ " + displayValue + " ▶" + positionHint;
    } else if (totalOptions > 1) {
      displayValue = displayValue + " ◀▶";
    }

    renderCompactField(frame, area, label, displayValue, target, "]");
  }

  private void renderCompactFocusedSelector(
      Frame frame, Rect area, String label, FocusTarget target, int selectedIndex) {
    List<String> options = metadataSelectors.optionsFor(target);
    if (options.isEmpty()) {
      renderCompactField(frame, area, label, "default", target, "]");
      return;
    }

    List<String> displayOptions = selectorDisplayOptions(target, options);
    int index = Math.max(0, Math.min(selectedIndex, displayOptions.size() - 1));

    boolean hasError = hasValidationErrorFor(target);
    String errorHint = validationErrorHint(target);
    Style fieldStyle = UiInputStyles.focusedField(theme, hasError);
    String prefix = "  " + label + ": [";
    String suffix = "]" + (errorHint.isEmpty() ? "" : " " + errorHint);
    int selectorWidth = area.width() - prefix.length() - suffix.length();
    if (selectorWidth < 5) {
      renderCompactField(frame, area, label, displayOptions.get(index), target, "]");
      return;
    }

    frame.renderWidget(
        Paragraph.builder().text(prefix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(area.left(), area.top(), Math.min(prefix.length(), area.width()), area.height()));

    Rect selectArea =
        new Rect(area.left() + prefix.length(), area.top(), selectorWidth, area.height());
    Select selectWidget = hasError ? focusedSelectorErrorWidget : focusedSelectorWidget;
    frame.renderStatefulWidget(selectWidget, selectArea, new SelectState(displayOptions, index));

    frame.renderWidget(
        Paragraph.builder().text(suffix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(
            selectArea.left() + selectArea.width(),
            area.top(),
            area.width() - prefix.length() - selectArea.width(),
            area.height()));
  }

  @Override
  public void renderCompactText(
      Frame frame, Rect area, String label, String value, FocusTarget target) {
    if (focusTarget == target && isTextInputFocus(target)) {
      renderCompactFocusedTextInput(frame, area, label, target);
      return;
    }
    String displayValue = value.isBlank() ? defaultValueFor(target) : value;
    renderCompactField(frame, area, label, displayValue, target, "_]");
  }

  private void renderCompactFocusedTextInput(
      Frame frame, Rect area, String label, FocusTarget target) {
    TextInputState state = inputStates.get(target);
    if (state == null) {
      renderCompactField(frame, area, label, "", target, "_]");
      return;
    }

    String errorHint = validationErrorHint(target);
    String prefix = "  " + label + ": [";
    String suffix = "]" + (errorHint.isEmpty() ? "" : " " + errorHint);
    int inputWidth = area.width() - prefix.length() - suffix.length();
    if (inputWidth < 1) {
      String fallbackValue = state.text().isBlank() ? defaultValueFor(target) : state.text();
      renderCompactField(frame, area, label, fallbackValue, target, "_]");
      return;
    }

    Style fieldStyle = UiInputStyles.focusedField(theme, hasValidationErrorFor(target));
    frame.renderWidget(
        Paragraph.builder().text(prefix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(area.left(), area.top(), Math.min(prefix.length(), area.width()), area.height()));

    Rect inputArea = new Rect(area.left() + prefix.length(), area.top(), inputWidth, area.height());
    TextInput.builder()
        .style(fieldStyle)
        .cursorStyle(UiInputStyles.cursor(theme))
        .placeholder(defaultValueFor(target))
        .placeholderStyle(Style.EMPTY.fg(theme.color("muted")).italic())
        .build()
        .renderWithCursor(inputArea, frame.buffer(), state, frame);

    frame.renderWidget(
        Paragraph.builder().text(suffix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(
            inputArea.left() + inputArea.width(),
            area.top(),
            area.width() - prefix.length() - inputArea.width(),
            area.height()));
  }

  private void renderCompactField(
      Frame frame,
      Rect area,
      String label,
      String displayValue,
      FocusTarget target,
      String focusSuffix) {
    boolean focused = focusTarget == target;
    String errorHint = validationErrorHint(target);
    int reservedForError = errorHint.isEmpty() ? 0 : errorHint.length() + 1;
    int maxValueLen = Math.max(8, area.width() - label.length() - 5 - reservedForError);
    if (displayValue.length() > maxValueLen) {
      displayValue = displayValue.substring(0, maxValueLen - 2) + "..";
    }

    String line = String.format("  %s: %s", label, displayValue);
    if (focused) {
      line = "  " + label + ": [" + displayValue + focusSuffix;
    }
    if (!errorHint.isEmpty()) {
      line = line + " " + errorHint;
    }

    Style style = Style.EMPTY.fg(focused ? theme.color("focus") : theme.color("text"));
    if (hasValidationErrorFor(target)) {
      style = Style.EMPTY.fg(theme.color("error"));
    }
    if (focused) {
      style = style.bold();
    }

    Paragraph paragraph =
        Paragraph.builder().text(line).style(style).overflow(Overflow.ELLIPSIS).build();
    frame.renderWidget(paragraph, area);
  }

  private String defaultValueFor(FocusTarget target) {
    return switch (target) {
      case GROUP_ID -> "org.acme";
      case ARTIFACT_ID -> "quarkus-app";
      case VERSION -> "1.0.0-SNAPSHOT";
      case PACKAGE_NAME -> "org.acme.quarkus";
      case OUTPUT_DIR -> ".";
      default -> "";
    };
  }

  @Override
  public void renderFooter(Frame frame, Rect area, List<String> footerLines) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < footerLines.size(); i++) {
      if (i > 0) {
        sb.append("\n");
      }
      sb.append("  ").append(footerLines.get(i));
    }

    Paragraph footer =
        Paragraph.builder()
            .text(sb.toString())
            .style(Style.EMPTY.fg(theme.color("muted")))
            .overflow(Overflow.WRAP_WORD)
            .build();
    frame.renderWidget(footer, area);
  }

  private static int estimateFooterHeightInternal(List<String> lines, int availableWidth) {
    if (availableWidth <= 0) {
      return Math.max(1, lines.size());
    }
    int height = 0;
    for (String line : lines) {
      int lineWidth = line.length() + 2;
      height += Math.max(1, (lineWidth + availableWidth - 1) / availableWidth);
    }
    return Math.max(1, height);
  }

  @Override
  public void renderCommandPalette(Frame frame, Rect viewport, int selection) {
    OverlayRenderer.renderCommandPalette(
        frame, viewport, theme, UiTextConstants.COMMAND_PALETTE_ENTRIES, selection);
  }

  private boolean isGenerationActive() {
    GenerationState state = generationStateTracker.currentState();
    return state == GenerationState.VALIDATING || state == GenerationState.LOADING;
  }

  @Override
  public void renderGenerationOverlay(
      Frame frame, Rect viewport, UiState.GenerationView generation) {
    OverlayRenderer.renderGenerationOverlay(
        frame, viewport, theme, generation.progressRatio(), generation.progressPhase());
  }

  @Override
  public void renderHelpOverlay(Frame frame, Rect viewport) {
    OverlayRenderer.renderHelpOverlay(
        frame, viewport, theme, helpOverlayLines(), helpOverlayTitle());
  }

  private List<String> helpOverlayLines() {
    List<String> lines = new ArrayList<>(UiTextConstants.GLOBAL_HELP_LINES);
    lines.add("");
    lines.add("Context (" + contextHelpTitle() + ")");
    lines.addAll(contextHelpLines());
    return lines;
  }

  private String helpOverlayTitle() {
    return "Help [focus] - " + contextHelpTitle();
  }

  private String contextHelpTitle() {
    if (postGenerationMenu.isGithubVisibilityMenuVisible()) {
      return "github visibility";
    }
    if (postGenerationMenu.isVisible()) {
      return "post-generate";
    }
    if (isGenerationInProgress()) {
      return "generation";
    }
    return switch (focusTarget) {
      case EXTENSION_SEARCH -> "extension search";
      case EXTENSION_LIST -> "extension list";
      case SUBMIT -> "submit";
      default -> "metadata";
    };
  }

  private List<String> contextHelpLines() {
    if (postGenerationMenu.isGithubVisibilityMenuVisible()) {
      return List.of(
          "  Up/Down or j/k  : choose repository visibility",
          "  Enter           : confirm and publish",
          "  Esc             : back to post-generate actions");
    }
    if (postGenerationMenu.isVisible()) {
      return List.of(
          "  Up/Down or j/k  : choose post-generate action",
          "  Enter           : run selected action",
          "  Esc             : quit");
    }
    if (isGenerationInProgress()) {
      return List.of(
          "  Esc / Ctrl+C    : cancel current generation",
          "  Enter           : disabled while generation is active");
    }
    return switch (focusTarget) {
      case EXTENSION_SEARCH ->
          List.of(
              "  Type            : filter extensions",
              "  Down            : move focus to extension list",
              "  Alt+S           : toggle selected-only view",
              "  Esc             : clear filter or return to list");
      case EXTENSION_LIST ->
          List.of(
              "  Up/Down or j/k  : move list selection",
              "  Space           : toggle extension",
              "  f               : toggle favorite",
              "  Alt+S           : toggle selected-only view",
              "  v               : cycle category filter");
      case SUBMIT ->
          List.of(
              "  Enter / Alt+G   : submit generation",
              "  j/k             : move focus",
              "  Esc             : quit");
      default ->
          List.of(
              "  Left/Right      : change selector value",
              "  Type            : edit focused text field",
              "  Enter / Alt+G   : submit generation");
    };
  }

  @Override
  public void renderStartupStatusOverlay(
      Frame frame, Rect viewport, UiState.StartupOverlayView startupOverlay) {
    OverlayRenderer.renderStartupOverlay(frame, viewport, theme, startupOverlay.statusLines());
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

  @Override
  public void renderPostGenerationOverlay(
      Frame frame, Rect viewport, UiState.PostGenerationView postGeneration) {
    if (postGeneration.githubVisibilityVisible()) {
      OverlayRenderer.renderGitHubVisibilityOverlay(
          frame,
          viewport,
          theme,
          UiTextConstants.GITHUB_VISIBILITY_LABELS,
          postGeneration.githubVisibilitySelection());
      return;
    }
    OverlayRenderer.renderPostGenerationOverlay(
        frame, viewport, theme, postGeneration.actionLabels(), postGeneration.actionSelection());
  }

  private FooterSnapshot footerSnapshot() {
    return new FooterSnapshot(
        isGenerationInProgress(),
        focusTarget,
        commandPaletteVisible,
        helpOverlayVisible,
        postGenerationMenu.isVisible(),
        statusMessage,
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
    String fieldName = focusTargetName(focusTarget);
    return validation.errors().stream()
        .filter(error -> error.field().equalsIgnoreCase(fieldName))
        .findFirst()
        .map(error -> error.message())
        .orElse("");
  }

  private void moveFocus(int offset) {
    if (postGenerationMenu.isVisible()) {
      return;
    }
    int index = FOCUS_ORDER.indexOf(focusTarget);
    int size = FOCUS_ORDER.size();
    int nextIndex = Math.floorMod(index + offset, size);
    focusTarget = FOCUS_ORDER.get(nextIndex);
    statusMessage = "Focus moved to " + focusTargetName(focusTarget);
    errorMessage = "";
    submitBlockedByValidation = false;
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
      closeCommandPalette();
      statusMessage = "Command palette closed";
      return UiAction.handled(false);
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      moveCommandPaletteSelection(-1);
      return UiAction.handled(false);
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      moveCommandPaletteSelection(1);
      return UiAction.handled(false);
    }
    if (keyEvent.isHome()) {
      commandPaletteSelection = 0;
      return UiAction.handled(false);
    }
    if (keyEvent.isEnd()) {
      commandPaletteSelection = UiTextConstants.COMMAND_PALETTE_ENTRIES.size() - 1;
      return UiAction.handled(false);
    }
    if (UiKeyMatchers.isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < UiTextConstants.COMMAND_PALETTE_ENTRIES.size()) {
        commandPaletteSelection = selected;
        executeCommandPaletteSelection();
      }
      return UiAction.handled(false);
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      executeCommandPaletteSelection();
      return UiAction.handled(false);
    }
    return UiAction.handled(false);
  }

  @Override
  public UiAction handlePostGenerationMenuKey(KeyEvent keyEvent) {
    PostGenerationMenuState.MenuKeyResult result = postGenerationMenu.handleKey(keyEvent);
    if (result == null) {
      return null;
    }
    ReduceResult reduceResult = uiReducer.reduce(uiState(), mapPostGenerationIntent(result));
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
    return reduceResult.action();
  }

  private static UiIntent mapPostGenerationIntent(PostGenerationMenuState.MenuKeyResult result) {
    return switch (result) {
      case PostGenerationMenuState.MenuKeyResult.Handled _ ->
          new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.HANDLED);
      case PostGenerationMenuState.MenuKeyResult.Quit _ ->
          new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.QUIT);
      case PostGenerationMenuState.MenuKeyResult.ExportRecipe _ ->
          new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.EXPORT_RECIPE);
      case PostGenerationMenuState.MenuKeyResult.GenerateAgain _ ->
          new UiIntent.PostGenerationIntent(UiIntent.PostGenerationTransition.GENERATE_AGAIN);
    };
  }

  private void applyReducerState(UiState reducedState) {
    statusMessage = reducedState.statusMessage();
    errorMessage = reducedState.errorMessage();
    verboseErrorDetails = reducedState.verboseErrorDetails();
  }

  private void exportRecipeAndLockFiles() {
    Path generatedProjectPath = postGenerationMenu.lastGeneratedProjectPath();
    if (generatedProjectPath == null) {
      statusMessage = "Cannot export Forgefile: no generated project path";
      return;
    }
    try {
      List<String> selectedExtensions = extensionCatalogState.selectedExtensionIds();
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

  private void moveCommandPaletteSelection(int delta) {
    int size = UiTextConstants.COMMAND_PALETTE_ENTRIES.size();
    if (size == 0) {
      return;
    }
    commandPaletteSelection = Math.floorMod(commandPaletteSelection + delta, size);
  }

  private void executeCommandPaletteSelection() {
    if (UiTextConstants.COMMAND_PALETTE_ENTRIES.isEmpty()) {
      closeCommandPalette();
      return;
    }
    CommandPaletteAction action =
        UiTextConstants.COMMAND_PALETTE_ENTRIES.get(commandPaletteSelection).action();
    closeCommandPalette();
    executeCommandPaletteAction(action);
  }

  private void executeCommandPaletteAction(CommandPaletteAction action) {
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
    if (commandPaletteVisible) {
      closeCommandPalette();
      statusMessage = "Command palette closed";
      return;
    }
    if (isGenerationInProgress()) {
      statusMessage = "Generation in progress. Press Esc to cancel.";
      return;
    }
    if (postGenerationMenu.isVisible()) {
      statusMessage = "Post-generation actions are open.";
      return;
    }
    closeHelpOverlay();
    commandPaletteVisible = true;
    commandPaletteSelection = 0;
    statusMessage = "Command palette opened";
  }

  private void closeCommandPalette() {
    commandPaletteVisible = false;
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
      closeHelpOverlay();
      statusMessage = "Help closed";
      return UiAction.handled(false);
    }
    if (isCommandPaletteToggleKey(keyEvent)) {
      closeHelpOverlay();
      toggleCommandPalette();
      return UiAction.handled(false);
    }
    return UiAction.handled(false);
  }

  @Override
  public void toggleHelpOverlay() {
    if (helpOverlayVisible) {
      closeHelpOverlay();
      statusMessage = "Help closed";
      return;
    }
    if (isGenerationInProgress()) {
      statusMessage = "Generation in progress. Press Esc to cancel.";
      return;
    }
    if (postGenerationMenu.isVisible()) {
      statusMessage = "Post-generation actions are open.";
      return;
    }
    closeCommandPalette();
    helpOverlayVisible = true;
    statusMessage = "Help opened";
  }

  private void closeHelpOverlay() {
    helpOverlayVisible = false;
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

  private Style panelBorderStyle(boolean focused, boolean hasError, boolean isLoading) {
    if (hasError) {
      return Style.EMPTY.fg(theme.color("error")).bold();
    }
    if (isLoading) {
      return Style.EMPTY.fg(theme.color("warning")).bold();
    }
    if (focused) {
      return Style.EMPTY.fg(theme.color("focus")).bold();
    }
    return Style.EMPTY.fg(theme.color("accent"));
  }

  private boolean hasValidationErrorFor(FocusTarget target) {
    if ((!isTextInputFocus(target) && !isMetadataSelectorFocus(target))
        || target == FocusTarget.EXTENSION_SEARCH) {
      return false;
    }
    String fieldName = focusTargetName(target);
    return validation.errors().stream()
        .anyMatch(error -> error.field().equalsIgnoreCase(fieldName));
  }

  private String validationErrorHint(FocusTarget target) {
    if (!hasValidationErrorFor(target)) {
      return "";
    }
    String fieldName = focusTargetName(target);
    return validation.errors().stream()
        .filter(error -> error.field().equalsIgnoreCase(fieldName))
        .findFirst()
        .map(error -> "⚠ " + error.message())
        .orElse("");
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
    extensionCatalogState.scheduleRefresh(query, this::updateExtensionFilterStatus);
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

  private void refreshValidationFeedbackAfterEdit() {
    if (submitBlockedByTargetConflict) {
      Path generatedProjectDirectory = safeResolveGeneratedProjectDirectory();
      if (generatedProjectDirectory != null && Files.exists(generatedProjectDirectory)) {
        errorMessage = targetExistsMessage(generatedProjectDirectory);
        return;
      }
      submitBlockedByTargetConflict = false;
      errorMessage = "";
      statusMessage = "Target folder conflict resolved";
    }

    if (!submitBlockedByValidation) {
      return;
    }
    if (validation.isValid()) {
      submitBlockedByValidation = false;
      errorMessage = "";
      statusMessage = "Validation restored";
      return;
    }
    errorMessage = firstValidationError(validation);
  }

  private void cancelPendingAsyncOperations() {
    extensionCatalogLoadToken++;
    if (catalogLoadState instanceof CatalogLoadState.Loading loading
        && loading.previous() != null) {
      catalogLoadState = loading.previous();
    } else if (catalogLoadState.isLoading()) {
      catalogLoadState = CatalogLoadState.initial();
    }
    extensionCatalogState.cancelPendingAsync();
  }

  private void startGenerationFlow() {
    generationFlowCoordinator.startFlow(
        projectGenerationRunner, toGenerationRequest(), resolveGeneratedProjectDirectory(), this);
  }

  private void reconcileGenerationCompletionIfDone() {
    generationFlowCoordinator.reconcileCompletionIfDone(this);
  }

  private void requestGenerationCancellation() {
    generationFlowCoordinator.requestCancellation(this);
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
        extensionCatalogState.selectedExtensionIds());
  }

  private Path resolveGeneratedProjectDirectory() {
    Path outputRoot = OutputPathResolver.resolveOutputRoot(request.outputDirectory());
    return outputRoot.resolve(request.artifactId()).normalize();
  }

  private void resetGenerationStateAfterTerminalOutcome() {
    generationStateTracker.resetAfterTerminalOutcome();
  }

  void cancelPendingAsyncForReducer() {
    cancelPendingAsyncOperations();
  }

  void exportRecipeAndLockForReducer() {
    exportRecipeAndLockFiles();
  }

  void resetGenerationForReducer() {
    resetGenerationStateAfterTerminalOutcome();
  }

  void startGenerationForReducer() {
    startGenerationFlow();
  }

  void requestGenerationCancellationForReducer() {
    requestGenerationCancellation();
  }

  void showPostGenerationSuccessForReducer(Path generatedPath, String nextCommand) {
    postGenerationMenu.showAfterSuccess(generatedPath, nextCommand);
  }

  void hidePostGenerationForReducer() {
    postGenerationMenu.hideAfterFailureOrCancel();
  }

  void requestAsyncRepaintForReducer() {
    requestAsyncRepaint();
  }

  void moveFocusForReducer(int offset) {
    moveFocus(offset);
  }

  void applyMetadataSelectorKeyForReducer(FocusTarget target, KeyEvent keyEvent) {
    if (handleMetadataSelectorKey(target, keyEvent)) {
      revalidate();
      refreshValidationFeedbackAfterEdit();
    }
  }

  void applyTextInputKeyForReducer(FocusTarget target, KeyEvent keyEvent) {
    if (!handleTextInputKey(inputStates.get(target), keyEvent)) {
      return;
    }
    if (target == FocusTarget.EXTENSION_SEARCH) {
      scheduleFilteredExtensionsRefresh();
      return;
    }
    rebuildRequestFromInputs();
    revalidate();
    refreshValidationFeedbackAfterEdit();
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

  private static String catalogLoadFailureMessage(Throwable throwable) {
    String message = ErrorMessageMapper.userFriendlyError(throwable);
    if (message.contains("no valid cache snapshot found")) {
      return "Live catalog/cache unavailable. Using bundled snapshot (Ctrl+R to retry).";
    }
    return "Catalog load failed: " + message;
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

  private static boolean isMetadataSelectorFocus(FocusTarget focusTarget) {
    return MetadataSelectorManager.isSelectorFocus(focusTarget);
  }

  private static String panelTitle(String baseTitle, boolean focused) {
    return focused ? baseTitle + " [focus]" : baseTitle;
  }

  private static String focusTargetName(FocusTarget target) {
    return switch (target) {
      case GROUP_ID -> "groupId";
      case ARTIFACT_ID -> "artifactId";
      case VERSION -> "version";
      case PACKAGE_NAME -> "packageName";
      case OUTPUT_DIR -> "outputDirectory";
      case PLATFORM_STREAM -> "platformStream";
      case BUILD_TOOL -> "buildTool";
      case JAVA_VERSION -> "javaVersion";
      case EXTENSION_SEARCH -> "extensionSearch";
      case EXTENSION_LIST -> "extensionList";
      case SUBMIT -> "submit";
    };
  }

  private static String firstValidationError(ValidationReport report) {
    return report.errors().isEmpty()
        ? ""
        : report.errors().getFirst().field() + ": " + report.errors().getFirst().message();
  }

  private UiAction handleTickEvent() {
    boolean loading = catalogLoadState.isLoading();
    boolean shouldRender = loading || startupOverlay.isVisible(loading);
    if (startupOverlay.tick(loading)) {
      shouldRender = true;
    }
    if (asyncRepaintSignal.consume()) {
      shouldRender = true;
    }
    if (generationStateTracker.currentState() == GenerationState.LOADING) {
      if (!generationFlowCoordinator.isCancellationRequested()) {
        generationStateTracker.tick(generationFlowCoordinator.elapsedMillisSinceStart());
        statusMessage = "Generation in progress: " + generationStateTracker.progressPhase();
      }
      shouldRender = true;
    }
    return shouldRender ? new UiAction(true, false) : UiAction.ignored();
  }

  private void handleSubmitRequest() {
    postGenerationMenu.reset();
    submitRequested = true;
    resetGenerationStateAfterTerminalOutcome();
    if (!transitionGenerationState(GenerationState.VALIDATING)) {
      statusMessage = "Submit ignored in state: " + generationStateLabel();
      return;
    }
    if (!validation.isValid()) {
      submitBlockedByValidation = true;
      submitBlockedByTargetConflict = false;
      errorMessage = firstValidationError(validation);
      FocusTarget invalidTarget = firstInvalidFocusableTarget(validation);
      int issueCount = validation.errors().size();
      if (invalidTarget != null) {
        focusTarget = invalidTarget;
        if (isTextInputFocus(invalidTarget)) {
          inputStates.get(invalidTarget).moveCursorToEnd();
        }
        statusMessage =
            "Submit blocked: fix "
                + focusTargetName(invalidTarget)
                + " ("
                + issueCount
                + " issue"
                + (issueCount == 1 ? "" : "s")
                + ")";
      } else {
        statusMessage =
            "Submit blocked: invalid input ("
                + issueCount
                + " issue"
                + (issueCount == 1 ? "" : "s")
                + ")";
      }
      transitionGenerationState(GenerationState.ERROR);
      return;
    }

    submitBlockedByValidation = false;
    submitBlockedByTargetConflict = false;
    errorMessage = "";
    Path generatedProjectDirectory = resolveGeneratedProjectDirectory();
    if (Files.exists(generatedProjectDirectory)) {
      focusTarget = FocusTarget.OUTPUT_DIR;
      inputStates.get(FocusTarget.OUTPUT_DIR).moveCursorToEnd();
      submitBlockedByTargetConflict = true;
      errorMessage = targetExistsMessage(generatedProjectDirectory);
      statusMessage = "Submit blocked: target folder exists (change output/artifact)";
      transitionGenerationState(GenerationState.ERROR);
      return;
    }
    if (projectGenerationRunner == NOOP_PROJECT_GENERATION_RUNNER) {
      statusMessage =
          "Submit requested with "
              + extensionCatalogState.selectedExtensionCount()
              + " extension(s), but generation service is not configured.";
      transitionGenerationState(GenerationState.IDLE);
      return;
    }
    ReduceResult reduceResult = uiReducer.reduce(uiState(), new UiIntent.SubmitReadyIntent());
    applyReducerState(reduceResult.nextState());
    uiEffectsRunner.run(reduceResult.effects(), this);
  }

  private void updateExtensionFilterStatus(int filteredCount) {
    if (isGenerationInProgress()) {
      return;
    }
    statusMessage = "Extensions filtered: " + filteredCount;
  }

  private void applyCatalogLoadCompletion(
      long loadToken, ExtensionCatalogLoadResult result, Throwable throwable) {
    if (isStaleCatalogLoadToken(loadToken)) {
      return;
    }
    if (throwable != null) {
      applyCatalogLoadFailure(
          catalogLoadFailureMessage(ThrowableUnwrapper.unwrapCompletionCause(throwable)));
      return;
    }
    if (result == null) {
      applyCatalogLoadFailure("Catalog load failed: empty load result");
      return;
    }
    List<ExtensionCatalogItem> items =
        result.extensions().stream()
            .map(
                extension ->
                    new ExtensionCatalogItem(
                        extension.id(),
                        extension.name(),
                        extension.shortName(),
                        extension.category(),
                        extension.order(),
                        extension.description()))
            .toList();
    if (items.isEmpty()) {
      applyCatalogLoadFailure("Catalog load returned no extensions");
      return;
    }
    if (result.metadata() != null) {
      metadataCompatibility = MetadataCompatibilityContext.success(result.metadata());
      syncMetadataSelectors();
      revalidate();
    }
    catalogLoadState = CatalogLoadState.loaded(result.source().label(), result.stale());
    errorMessage = "";
    verboseErrorDetails = "";
    statusMessage =
        !result.detailMessage().isBlank()
            ? result.detailMessage()
            : catalogLoadedStatusMessage(result.source(), result.stale());
    extensionCatalogState.replaceCatalog(
        items, inputStates.get(FocusTarget.EXTENSION_SEARCH).text(), ignored -> {});
    extensionCatalogState.setPresetExtensionsByName(result.presetExtensionsByName(), ignored -> {});
    requestAsyncRepaint();
  }

  private boolean isStaleCatalogLoadToken(long loadToken) {
    return loadToken != extensionCatalogLoadToken;
  }

  private void applyCatalogLoadFailure(String message) {
    CatalogLoadState previous =
        catalogLoadState instanceof CatalogLoadState.Loading loading ? loading.previous() : null;
    if (previous instanceof CatalogLoadState.Loaded prev && prev.isLiveLoad()) {
      // Reload failed after a previous successful live load — keep the existing catalog
      catalogLoadState = prev;
      statusMessage = "Catalog reload failed; keeping current catalog";
    } else {
      // Initial load failed or no live catalog yet — fall back
      catalogLoadState = CatalogLoadState.failed(message);
      statusMessage = "Using fallback extension catalog";
    }
    errorMessage = message;
    verboseErrorDetails = message;
    requestAsyncRepaint();
  }

  private boolean isStartupOverlayVisible() {
    return startupOverlay.isVisible(catalogLoadState.isLoading());
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
    String activeError = activeErrorDetails();
    if (activeError.isBlank()) {
      showErrorDetails = false;
      statusMessage = "No error details available";
      return;
    }
    showErrorDetails = !showErrorDetails;
    statusMessage = showErrorDetails ? "Expanded error details" : "Collapsed error details";
  }

  private void requestCatalogReload() {
    if (extensionCatalogLoader == null) {
      statusMessage = "Catalog reload unavailable";
      return;
    }
    statusMessage = "Reloading extension catalog...";
    loadExtensionCatalogAsync(extensionCatalogLoader);
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

  private static String targetExistsMessage(Path generatedProjectDirectory) {
    return "Output directory already exists: "
        + generatedProjectDirectory.toAbsolutePath().normalize();
  }

  private Path safeResolveGeneratedProjectDirectory() {
    try {
      return resolveGeneratedProjectDirectory();
    } catch (RuntimeException runtimeException) {
      return null;
    }
  }

  private static FocusTarget firstInvalidFocusableTarget(ValidationReport report) {
    for (var error : report.errors()) {
      FocusTarget target = fieldToFocusTarget(error.field());
      if (target != null) {
        return target;
      }
    }
    return null;
  }

  private static FocusTarget fieldToFocusTarget(String fieldName) {
    if (fieldName == null || fieldName.isBlank()) {
      return null;
    }
    return switch (fieldName.trim().toLowerCase()) {
      case "groupid" -> FocusTarget.GROUP_ID;
      case "artifactid" -> FocusTarget.ARTIFACT_ID;
      case "version" -> FocusTarget.VERSION;
      case "packagename" -> FocusTarget.PACKAGE_NAME;
      case "outputdirectory" -> FocusTarget.OUTPUT_DIR;
      case "platformstream" -> FocusTarget.PLATFORM_STREAM;
      case "buildtool" -> FocusTarget.BUILD_TOOL;
      case "javaversion" -> FocusTarget.JAVA_VERSION;
      default -> null;
    };
  }

  private void focusExtensionSearch() {
    focusTarget = FocusTarget.EXTENSION_SEARCH;
    inputStates.get(FocusTarget.EXTENSION_SEARCH).moveCursorToEnd();
    statusMessage = "Focus moved to extensionSearch";
    errorMessage = "";
    submitBlockedByValidation = false;
  }

  private void focusExtensionList() {
    focusTarget = FocusTarget.EXTENSION_LIST;
    statusMessage = "Focus moved to extensionList";
    errorMessage = "";
    submitBlockedByValidation = false;
  }

  private void clearExtensionSearchFilter() {
    TextInputState searchState = inputStates.get(FocusTarget.EXTENSION_SEARCH);
    searchState.setText("");
    searchState.moveCursorToStart();
    extensionCatalogState.refreshNow("", this::updateExtensionFilterStatus);
    statusMessage = "Extension search cleared";
  }

  private static String catalogLoadedStatusMessage(CatalogSource source, boolean stale) {
    return switch (source) {
      case LIVE -> "Loaded extension catalog from live API";
      case CACHE ->
          stale
              ? "Loaded stale extension catalog from cache"
              : "Loaded extension catalog from cache";
    };
  }

  private void moveFocusToAdjacentInvalidField(boolean forward) {
    List<FocusTarget> invalidTargets = invalidFocusableTargets(validation);
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
    statusMessage = "Focus moved to invalid field: " + focusTargetName(focusTarget);
  }

  private static List<FocusTarget> invalidFocusableTargets(ValidationReport report) {
    List<FocusTarget> targets = new ArrayList<>();
    for (var error : report.errors()) {
      FocusTarget target = fieldToFocusTarget(error.field());
      if (target != null && !targets.contains(target)) {
        targets.add(target);
      }
    }
    return targets;
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
        + extensionCatalogState.selectedExtensionCount()
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
    if (event.code() == dev.tamboui.tui.event.KeyCode.CHAR) {
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
