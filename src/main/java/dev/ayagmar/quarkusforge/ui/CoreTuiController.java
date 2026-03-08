package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.BuildToolCodec;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ErrorMessageMapper;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileLock;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import dev.ayagmar.quarkusforge.persistence.ExtensionFavoritesStore;
import dev.ayagmar.quarkusforge.postgen.IdeDetector;
import dev.ayagmar.quarkusforge.util.OutputPathResolver;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.input.TextInputState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
  private final ExtensionEffects extensionEffects;
  private final ProjectGenerationRunner projectGenerationRunner;
  private final MetadataSelectorManager metadataSelectors = new MetadataSelectorManager();

  private UiState reducerState;
  private String generationStatusNotice;
  private final GenerationStateTracker generationStateTracker;
  private final AsyncRepaintSignal asyncRepaintSignal;
  private final UiEventRouter uiEventRouter;
  private final GenerationFlowCoordinator generationFlowCoordinator;
  private final CatalogLoadCoordinator catalogLoadCoordinator;
  private final UiReducer uiReducer;
  private final UiEffectsRunner uiEffectsRunner;
  private final UiEffectsPort uiEffectsPort;
  private final InputEffects inputEffects;
  private final GenerationEffects generationEffects;
  private final UiRenderStateAssembler uiRenderStateAssembler;
  private final UiRenderer uiRenderer;
  private final CoreUiRenderAdapter uiRenderAdapter;
  private final CatalogEffects catalogEffects;
  private final CatalogLoadIntentPort catalogLoadIntentPort;
  private final PostGenerationMenuState postGenerationMenu;

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
    generationStatusNotice = "";
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
            catalogEffects.startLoad(loader, catalogLoadIntentPort);
          }

          @Override
          public void requestCatalogReload() {
            catalogEffects.requestReload(catalogLoadIntentPort);
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
          public String exportRecipeAndLock() {
            return exportRecipeAndLockEffect();
          }

          @Override
          public List<UiIntent> executeExtensionCommand(UiIntent.ExtensionCommand command) {
            return extensionEffects.executeCommand(command);
          }

          @Override
          public List<UiIntent> applyExtensionNavigationKey(KeyEvent keyEvent) {
            return extensionEffects.applyNavigationKey(keyEvent);
          }

          @Override
          public List<UiIntent> applyCatalogLoadSuccess(CatalogLoadSuccess success) {
            CatalogEffects.ApplyLoadSuccessResult result =
                catalogEffects.applyLoadSuccess(
                    success,
                    metadataCompatibility,
                    inputStates.get(FocusTarget.EXTENSION_SEARCH).text());
            metadataCompatibility = result.metadataCompatibility();
            return result.followUpIntents();
          }

          @Override
          public void startGeneration() {
            generationEffects.startGeneration(
                currentRequest(),
                extensionCatalogNavigation.selectedExtensionIds(),
                OutputPathResolver.resolveGeneratedProjectDirectory(currentRequest()),
                CoreTuiController.this);
          }

          @Override
          public void transitionGenerationState(GenerationState targetState) {
            generationEffects.transitionGenerationState(targetState);
          }

          @Override
          public void requestGenerationCancellation() {
            generationEffects.requestCancellation(CoreTuiController.this);
          }

          @Override
          public void requestAsyncRepaint() {
            requestAsyncRepaintEffect();
          }

          @Override
          public void moveTextInputCursorToEnd(FocusTarget target) {
            inputEffects.moveTextInputCursorToEnd(target);
          }

          @Override
          public List<UiIntent> applyMetadataSelectorKey(FocusTarget target, KeyEvent keyEvent) {
            return inputEffects.applyMetadataSelectorKey(target, keyEvent);
          }

          @Override
          public List<UiIntent> applyTextInputKey(FocusTarget target, KeyEvent keyEvent) {
            return inputEffects.applyTextInputKey(target, keyEvent);
          }
        };
    uiRenderer = new UiRenderer();
    uiRenderAdapter =
        new CoreUiRenderAdapter(
            theme, bodyPanelRenderer, footerLinesComposer, new CompactFieldRenderer(theme));
    postGenerationMenu =
        new PostGenerationMenuState(UiTextConstants.postGenerationActions(detectedIdes));
    catalogLoadIntentPort =
        new CatalogLoadIntentPort() {
          @Override
          public CatalogLoadState currentCatalogLoadState() {
            return reducerState == null
                ? CatalogLoadState.initial()
                : reducerState.catalogLoad().state();
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

    for (FocusTarget target : FocusTarget.values()) {
      inputStates.put(target, new TextInputState(""));
    }
    ProjectRequest initialRequest = initialState.request();
    inputStates.get(FocusTarget.GROUP_ID).setText(initialRequest.groupId());
    inputStates.get(FocusTarget.ARTIFACT_ID).setText(initialRequest.artifactId());
    inputStates.get(FocusTarget.VERSION).setText(initialRequest.version());
    inputStates.get(FocusTarget.PACKAGE_NAME).setText(initialRequest.packageName());
    inputStates.get(FocusTarget.OUTPUT_DIR).setText(initialRequest.outputDirectory());
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
    generationEffects =
        new GenerationEffects(
            generationFlowCoordinator, generationStateTracker, this.projectGenerationRunner);
    catalogEffects =
        new CatalogEffects(
            catalogLoadCoordinator,
            extensionCatalogPreferences,
            extensionCatalogNavigation,
            extensionCatalogProjection,
            new CatalogEffects.Callbacks() {
              @Override
              public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
                return CoreTuiController.this.extensionStateUpdatedIntent();
              }

              @Override
              public ProjectRequest currentRequest() {
                return CoreTuiController.this.currentRequest();
              }

              @Override
              public ProjectRequest syncMetadataSelectors(ProjectRequest request) {
                return CoreTuiController.this.syncMetadataSelectors(request);
              }

              @Override
              public ValidationReport validateRequest(ProjectRequest request) {
                return CoreTuiController.this.validateRequest(request);
              }
            });
    inputEffects =
        new InputEffects(
            inputStates,
            metadataSelectors,
            new InputEffects.Callbacks() {
              @Override
              public ProjectRequest currentRequest() {
                return CoreTuiController.this.currentRequest();
              }

              @Override
              public ProjectRequest syncMetadataSelectors(ProjectRequest request) {
                return CoreTuiController.this.syncMetadataSelectors(request);
              }

              @Override
              public ValidationReport validateRequest(ProjectRequest request) {
                return CoreTuiController.this.validateRequest(request);
              }

              @Override
              public UiIntent.SubmitEditRecoveryIntent submitRecoveryIntent(
                  ProjectRequest request) {
                return CoreTuiController.this.submitRecoveryIntent(request);
              }

              @Override
              public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
                return CoreTuiController.this.extensionStateUpdatedIntent();
              }

              @Override
              public boolean isGenerationInProgress() {
                return CoreTuiController.this.isGenerationInProgress();
              }

              @Override
              public void scheduleFilteredRefresh(
                  String query, java.util.function.IntConsumer onFiltered) {
                catalogEffects.scheduleFilteredRefresh(query, onFiltered);
              }

              @Override
              public void dispatchIntents(List<UiIntent> intents) {
                intents.forEach(CoreTuiController.this::dispatchIntent);
              }
            });
    extensionEffects =
        new ExtensionEffects(
            catalogEffects,
            new ExtensionInteractionHandler(
                extensionCatalogPreferences,
                extensionCatalogNavigation,
                extensionCatalogProjection),
            extensionCatalogPreferences,
            extensionCatalogNavigation,
            extensionCatalogProjection,
            new ExtensionEffects.Callbacks() {
              @Override
              public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
                return CoreTuiController.this.extensionStateUpdatedIntent();
              }

              @Override
              public TextInputState extensionSearchState() {
                return inputStates.get(FocusTarget.EXTENSION_SEARCH);
              }

              @Override
              public String currentStatusMessage() {
                return CoreTuiController.this.currentStatusMessage();
              }
            });
    uiRenderStateAssembler =
        new UiRenderStateAssembler(
            inputStates,
            metadataSelectors,
            extensionCatalogPreferences,
            extensionCatalogNavigation,
            extensionCatalogProjection,
            generationStateTracker,
            new UiStateSnapshotMapper());

    ProjectRequest synchronizedRequest = syncMetadataSelectors(initialRequest);
    ValidationReport synchronizedValidation = validateRequest(synchronizedRequest);
    reducerState =
        initialReducerState(synchronizedRequest, synchronizedValidation, extensionViewSnapshot());
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
      dispatchIntent(
          new UiIntent.StatusMessageIntent(
              "Terminal resized to " + resizeEvent.width() + "x" + resizeEvent.height()));
      return UiAction.handled(false);
    }
    if (!(event instanceof KeyEvent keyEvent)) {
      return UiAction.ignored();
    }
    return uiEventRouter.routeKeyEvent(keyEvent, this);
  }

  @Override
  public boolean shouldToggleHelpOverlay(KeyEvent keyEvent) {
    return CoreTuiController.shouldToggleHelpOverlay(keyEvent, focusTarget());
  }

  @Override
  public boolean isCommandPaletteToggleKey(KeyEvent keyEvent) {
    return AppKeyActions.isCommandPaletteToggleKey(keyEvent);
  }

  @Override
  public UiAction handleExtensionCancelFlow(KeyEvent keyEvent) {
    if (!keyEvent.isCancel()) {
      return null;
    }
    return routeIntent(new UiIntent.ExtensionCancelIntent());
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
    CommandPaletteAction sharedAction = sharedGlobalShortcutAction(keyEvent, focusTarget());
    return sharedAction == null ? null : routeSharedAction(sharedAction);
  }

  @Override
  public UiAction handleFocusNavigationFlow(KeyEvent keyEvent) {
    return routeIntent(new UiIntent.FocusNavigationIntent(keyEvent, focusTarget()));
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
                      currentTargetConflictErrorMessage(currentRequest()))));
      moveFocusedInputCursorToEndAfterSubmitFeedback();
      return action != null ? action : UiAction.handled(false);
    }
    return null;
  }

  @Override
  public UiAction handleExtensionFocusFlow(KeyEvent keyEvent) {
    return routeIntent(new UiIntent.ExtensionInteractionIntent(keyEvent));
  }

  @Override
  public UiAction handleMetadataSelectorFlow(KeyEvent keyEvent) {
    FocusTarget focusTarget = focusTarget();
    return routeIntent(
        new UiIntent.MetadataInputIntent(keyEvent, focusTarget, hasSelectorOptions(focusTarget)));
  }

  @Override
  public UiAction handleTextInputFlow(KeyEvent keyEvent) {
    return routeIntent(new UiIntent.TextInputIntent(keyEvent, focusTarget()));
  }

  @Override
  public void beforeGenerationStart() {
    extensionCatalogProjection.cancelPendingAsync();
  }

  @Override
  public boolean transitionTo(GenerationState targetState) {
    return generationEffects.transitionGenerationState(targetState);
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
        new UiIntent.GenerationSuccessIntent(
            generatedPath, nextStepCommand(currentRequest().buildTool())));
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
        uiRenderStateAssembler.renderContext(reducerState, metadataCompatibility));
    uiRenderer.render(frame, renderModel(), uiRenderAdapter);
  }

  UiState uiState() {
    syncReducerRuntimeState();
    return reducerState.withStatusMessage(effectiveStatusMessage());
  }

  UiRenderModel renderModel() {
    syncReducerRuntimeState();
    return uiRenderStateAssembler.renderModel(
        reducerState,
        effectiveStatusMessage(),
        metadataCompatibility,
        generationFlowCoordinator.isCancellationRequested());
  }

  FocusTarget focusTarget() {
    return reducerState.focusTarget();
  }

  ValidationReport validation() {
    return currentValidation();
  }

  public ProjectRequest request() {
    return currentRequest();
  }

  List<String> selectedExtensionIds() {
    return extensionCatalogNavigation.selectedExtensionIds();
  }

  String statusMessage() {
    return effectiveStatusMessage();
  }

  private String currentStatusMessage() {
    return reducerState == null ? "" : reducerState.statusMessage();
  }

  String errorMessage() {
    return reducerState.errorMessage();
  }

  boolean commandPaletteVisible() {
    return reducerState.overlays().commandPaletteVisible();
  }

  boolean helpOverlayVisible() {
    return reducerState.overlays().helpOverlayVisible();
  }

  boolean submitRequested() {
    return reducerState.submitRequested();
  }

  public Optional<PostGenerationExitPlan> postGenerationExitPlan() {
    return Optional.ofNullable(reducerState.postGeneration().exitPlan());
  }

  boolean postGenerationMenuVisible() {
    return reducerState.postGeneration().visible();
  }

  boolean githubVisibilityMenuVisible() {
    return reducerState.postGeneration().githubVisibilityVisible();
  }

  String postGenerationSuccessHint() {
    return reducerState.postGeneration().successHint();
  }

  List<String> postGenerationActionLabels() {
    return reducerState.postGeneration().actionLabels();
  }

  int postGenerationActionSelection() {
    return reducerState.postGeneration().actionSelection();
  }

  GenerationState generationState() {
    return generationStateTracker.currentState();
  }

  int filteredExtensionCount() {
    return reducerState.extensions().filteredCount();
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
    return reducerState.extensions().favoritesOnlyEnabled();
  }

  int favoriteExtensionCount() {
    return extensionCatalogPreferences.favoriteExtensionCount();
  }

  String focusedListExtensionId() {
    return reducerState.extensions().focusedExtensionId();
  }

  private CatalogLoadState catalogLoadState() {
    return reducerState.catalogLoad().state();
  }

  private ProjectRequest currentRequest() {
    return reducerState.request();
  }

  private ValidationReport currentValidation() {
    return reducerState.validation();
  }

  private UiState initialReducerState(
      ProjectRequest request, ValidationReport validation, UiState.ExtensionView extensions) {
    return new UiState(
        request,
        validation,
        FocusTarget.GROUP_ID,
        "Ready",
        "",
        "",
        false,
        false,
        false,
        false,
        0,
        new UiState.OverlayState(false, false, false, false),
        new UiState.CatalogLoadView(CatalogLoadState.initial()),
        postGenerationMenu.initialView(),
        extensions);
  }

  private String focusedExtensionId() {
    String focusedId =
        extensionCatalogProjection.itemIdAtRow(extensionCatalogNavigation.selectedRow());
    return focusedId == null ? "" : focusedId;
  }

  private UiState.ExtensionView extensionViewSnapshot() {
    Integer selectedRow = extensionCatalogNavigation.selectedRow();
    return UiState.ExtensionView.snapshot(
        extensionCatalogProjection.filteredExtensions().size(),
        extensionCatalogProjection.totalCatalogExtensionCount(),
        extensionCatalogNavigation.selectedExtensionCount(),
        extensionCatalogProjection.favoritesOnlyFilterEnabled(),
        extensionCatalogProjection.selectedOnlyFilterEnabled(),
        extensionCatalogProjection.activePresetFilterName(),
        extensionCatalogProjection.activeCategoryFilterTitle(),
        inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
        focusedExtensionId(),
        extensionCatalogNavigation.isSelectionAtTop(extensionCatalogProjection.rows()),
        extensionCatalogProjection.isCategorySectionHeaderSelected(selectedRow));
  }

  private UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
    return new UiIntent.ExtensionStateUpdatedIntent(extensionViewSnapshot());
  }

  @Override
  public UiAction handleCommandPaletteKey(KeyEvent keyEvent) {
    if (!commandPaletteVisible()) {
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
    UiIntent.PostGenerationCommand command =
        postGenerationMenu.handleKey(reducerState.postGeneration(), keyEvent);
    if (command == null) {
      return null;
    }
    return routeIntent(new UiIntent.PostGenerationIntent(command));
  }

  private void storeReducerState(UiState reducedState) {
    reducerState = reducedState;
  }

  private ReduceResult dispatchIntent(UiIntent intent) {
    syncReducerRuntimeState();
    ArrayDeque<UiIntent> pendingIntents = new ArrayDeque<>();
    pendingIntents.add(intent);
    ReduceResult firstResult = null;
    while (!pendingIntents.isEmpty()) {
      UiIntent nextIntent = pendingIntents.removeFirst();
      ReduceResult reduceResult = uiReducer.reduce(reducerState, nextIntent);
      if (firstResult == null) {
        firstResult = reduceResult;
      }
      storeReducerState(reduceResult.nextState());
      pendingIntents.addAll(uiEffectsRunner.run(reduceResult.effects(), uiEffectsPort));
    }
    return firstResult == null
        ? new ReduceResult(reducerState, List.of(), UiAction.ignored())
        : firstResult;
  }

  private UiAction routeIntent(UiIntent intent) {
    UiAction action = dispatchIntent(intent).action();
    return action.handled() ? action : null;
  }

  private String exportRecipeAndLockFiles() {
    Path generatedProjectPath = reducerState.postGeneration().lastGeneratedProjectPath();
    if (generatedProjectPath == null) {
      return "Cannot export Forgefile: no generated project path";
    }
    try {
      ProjectRequest request = currentRequest();
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
      return "Exported Forgefile to " + forgefilePath;
    } catch (RuntimeException runtimeException) {
      return "Failed to export Forgefile: " + runtimeException.getMessage();
    }
  }

  @Override
  public void toggleCommandPalette() {
    dispatchIntent(
        new UiIntent.CommandPaletteIntent(new UiIntent.CommandPaletteCommand.ToggleVisibility()));
  }

  @Override
  public UiAction handleHelpOverlayKey(KeyEvent keyEvent) {
    if (!helpOverlayVisible()) {
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

  private boolean hasValidationErrorFor(FocusTarget target) {
    if ((!isTextInputFocus(target) && !MetadataSelectorManager.isSelectorFocus(target))
        || target == FocusTarget.EXTENSION_SEARCH) {
      return false;
    }
    String fieldName = UiFocusTargets.nameOf(target);
    return currentValidation().errors().stream()
        .anyMatch(error -> error.field().equalsIgnoreCase(fieldName));
  }

  private ProjectRequest syncMetadataSelectors(ProjectRequest request) {
    MetadataDto metadataSnapshot = metadataCompatibility.metadataSnapshot();
    MetadataSelectorManager.ResolvedSelections resolved =
        metadataSelectors.sync(
            metadataSnapshot, request.platformStream(), request.buildTool(), request.javaVersion());

    return inputEffects.buildRequestFromInputs(
        resolved.platformStream(), resolved.buildTool(), resolved.javaVersion());
  }

  private ValidationReport validateRequest(ProjectRequest request) {
    ValidationReport report = requestValidator.validate(request);
    return report.merge(metadataCompatibility.validate(request));
  }

  private void cancelPendingAsyncOperations() {
    catalogEffects.cancelPendingAsync(catalogLoadIntentPort);
  }

  private void reconcileGenerationCompletionIfDone() {
    generationFlowCoordinator.reconcileCompletionIfDone(this);
  }

  @Override
  public boolean isGenerationInProgress() {
    return generationStateTracker.isInProgress();
  }

  private void prepareForGenerationEffect() {
    generationStatusNotice = "";
    generationEffects.prepareForGeneration();
  }

  private void cancelPendingAsyncEffect() {
    cancelPendingAsyncOperations();
  }

  private String exportRecipeAndLockEffect() {
    return exportRecipeAndLockFiles();
  }

  private void requestAsyncRepaintEffect() {
    requestAsyncRepaint();
  }

  @Override
  public String generationStateLabel() {
    return generationStateTracker.stateLabel();
  }

  @Override
  public void scheduleOnRenderThread(Runnable task) {
    scheduler.schedule(Duration.ZERO, task);
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

  private UiAction handleTickEvent() {
    syncReducerRuntimeState();
    boolean loading = catalogLoadState().isLoading();
    CatalogLoadCoordinator.StartupOverlayTickResult overlayTick =
        catalogLoadCoordinator.tickStartupOverlay(catalogLoadState());
    if (reducerState.overlays().startupOverlayVisible() != overlayTick.visible()) {
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

  private void requestAsyncRepaint() {
    asyncRepaintSignal.request();
  }

  private void syncReducerRuntimeState() {
    boolean generationVisible = isGenerationOverlayVisible();
    if (reducerState.overlays().generationVisible() != generationVisible) {
      reducerState =
          uiReducer
              .reduce(
                  reducerState, new UiIntent.GenerationOverlayVisibilityIntent(generationVisible))
              .nextState();
    }
  }

  private boolean hasSelectorOptions(FocusTarget focusTarget) {
    return MetadataSelectorManager.isSelectorFocus(focusTarget)
        && !metadataSelectors.optionsFor(focusTarget).isEmpty();
  }

  private boolean isGenerationOverlayVisible() {
    GenerationState generationState = generationStateTracker.currentState();
    return generationState == GenerationState.VALIDATING
        || generationState == GenerationState.LOADING;
  }

  private String effectiveStatusMessage() {
    if (generationStateTracker.currentState() != GenerationState.LOADING) {
      return reducerState.statusMessage();
    }
    if (!generationStatusNotice.isBlank()) {
      return generationStatusNotice;
    }
    if (generationFlowCoordinator.isCancellationRequested()) {
      return reducerState.statusMessage();
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

  private static boolean shouldFocusExtensionSearch(KeyEvent keyEvent, FocusTarget currentFocus) {
    if (AppKeyActions.isFocusExtensionSearchSlashKey(keyEvent)) {
      return !isTextInputFocus(currentFocus) && currentFocus != FocusTarget.EXTENSION_SEARCH;
    }
    return AppKeyActions.isFocusExtensionSearchCtrlKey(keyEvent);
  }

  private static CommandPaletteAction sharedGlobalShortcutAction(
      KeyEvent keyEvent, FocusTarget currentFocus) {
    if (shouldFocusExtensionSearch(keyEvent, currentFocus)) {
      return CommandPaletteAction.FOCUS_EXTENSION_SEARCH;
    }
    if (AppKeyActions.isFocusExtensionListKey(keyEvent)) {
      return CommandPaletteAction.FOCUS_EXTENSION_LIST;
    }
    if (AppKeyActions.isCatalogReloadKey(keyEvent)) {
      return CommandPaletteAction.RELOAD_CATALOG;
    }
    if (AppKeyActions.isFavoritesFilterToggleKey(keyEvent)) {
      return CommandPaletteAction.TOGGLE_FAVORITES_FILTER;
    }
    if (AppKeyActions.isSelectedOnlyFilterToggleKey(keyEvent)) {
      return CommandPaletteAction.TOGGLE_SELECTED_FILTER;
    }
    if (AppKeyActions.isPresetFilterCycleKey(keyEvent)) {
      return CommandPaletteAction.CYCLE_PRESET_FILTER;
    }
    if (AppKeyActions.isJumpToFavoriteKey(keyEvent)) {
      return CommandPaletteAction.JUMP_TO_FAVORITE;
    }
    if (AppKeyActions.isErrorDetailsToggleKey(keyEvent)) {
      return CommandPaletteAction.TOGGLE_ERROR_DETAILS;
    }
    return null;
  }

  private String currentTargetConflictErrorMessage(ProjectRequest request) {
    try {
      Path generatedProjectDirectory = OutputPathResolver.resolveGeneratedProjectDirectory(request);
      if (!Files.exists(generatedProjectDirectory)) {
        return "";
      }
      return "Output directory already exists: "
          + generatedProjectDirectory.toAbsolutePath().normalize();
    } catch (RuntimeException runtimeException) {
      return "";
    }
  }

  private UiIntent.SubmitEditRecoveryIntent submitRecoveryIntent(ProjectRequest request) {
    return new UiIntent.SubmitEditRecoveryIntent(
        new UiIntent.SubmitRecoveryContext(currentTargetConflictErrorMessage(request)));
  }

  private void moveFocusedInputCursorToEndAfterSubmitFeedback() {
    FocusTarget focusTarget = focusTarget();
    if (isTextInputFocus(focusTarget)
        && (focusTarget == FocusTarget.OUTPUT_DIR || hasValidationErrorFor(focusTarget))) {
      inputStates.get(focusTarget).moveCursorToEnd();
    }
  }

  private UiAction routeSharedAction(CommandPaletteAction action) {
    return routeIntent(new UiIntent.SharedActionIntent(action));
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
    List<FocusTarget> invalidTargets = ValidationFocusTargets.orderedInvalid(currentValidation());
    if (invalidTargets.isEmpty()) {
      dispatchIntent(new UiIntent.StatusMessageIntent("No invalid fields"));
      return;
    }
    int currentIndex = invalidTargets.indexOf(focusTarget());
    int nextIndex;
    if (currentIndex < 0) {
      nextIndex = forward ? 0 : invalidTargets.size() - 1;
    } else {
      nextIndex = Math.floorMod(currentIndex + (forward ? 1 : -1), invalidTargets.size());
    }
    FocusTarget nextFocusTarget = invalidTargets.get(nextIndex);
    if (isTextInputFocus(nextFocusTarget)) {
      inputStates.get(nextFocusTarget).moveCursorToEnd();
    }
    dispatchIntent(
        new UiIntent.FocusStatusIntent(
            nextFocusTarget,
            "Focus moved to invalid field: " + UiFocusTargets.nameOf(nextFocusTarget)));
  }
}
