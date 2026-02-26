package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ApiErrorMessages;
import dev.ayagmar.quarkusforge.api.BuildToolCodec;
import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.tui.event.TickEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.gauge.LineGauge;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CoreTuiController implements BodyPanelRenderer.CompactInputRenderer {
  private static final List<FocusTarget> FOCUS_ORDER =
      List.of(
          FocusTarget.PLATFORM_STREAM,
          FocusTarget.BUILD_TOOL,
          FocusTarget.JAVA_VERSION,
          FocusTarget.GROUP_ID,
          FocusTarget.ARTIFACT_ID,
          FocusTarget.VERSION,
          FocusTarget.PACKAGE_NAME,
          FocusTarget.OUTPUT_DIR,
          FocusTarget.EXTENSION_SEARCH,
          FocusTarget.EXTENSION_LIST,
          FocusTarget.SUBMIT);

  private static final int NARROW_WIDTH_THRESHOLD = 100;
  private static final List<String> STARTUP_SPLASH_ART =
      List.of(
          "   ____ _   _   _    ____  _  ___   _ ____",
          "  / __ \\ | | | / \\  |  _ \\| |/ / | | / ___|",
          " | |  | | |_| |/ _ \\ | |_) | ' /| | | \\___ \\",
          " | |__| |  _  / ___ \\|  _ <| . \\| |_| |___) |",
          "  \\___\\_\\_| |_/_/   \\_\\_| \\_\\_|\\_\\\\___/|____/");
  private static final ProjectGenerationRunner NOOP_PROJECT_GENERATION_RUNNER =
      (generationRequest, outputDirectory, cancelled, progressListener) ->
          CompletableFuture.failedFuture(
              new IllegalStateException("Generation flow is not configured in this runtime"));
  private static final List<CommandPaletteEntry> COMMAND_PALETTE_ENTRIES =
      List.of(
          new CommandPaletteEntry(
              "Focus extension search", "/ or Ctrl+F", CommandPaletteAction.FOCUS_EXTENSION_SEARCH),
          new CommandPaletteEntry(
              "Focus extension list", "Ctrl+L", CommandPaletteAction.FOCUS_EXTENSION_LIST),
          new CommandPaletteEntry(
              "Toggle favorite filter", "Ctrl+K", CommandPaletteAction.TOGGLE_FAVORITES_FILTER),
          new CommandPaletteEntry(
              "Jump to next favorite", "Ctrl+J", CommandPaletteAction.JUMP_TO_FAVORITE),
          new CommandPaletteEntry(
              "Cycle category filter", "v", CommandPaletteAction.CYCLE_CATEGORY_FILTER),
          new CommandPaletteEntry("Toggle category", "c", CommandPaletteAction.TOGGLE_CATEGORY),
          new CommandPaletteEntry(
              "Open all categories", "C", CommandPaletteAction.OPEN_ALL_CATEGORIES),
          new CommandPaletteEntry("Reload catalog", "Ctrl+R", CommandPaletteAction.RELOAD_CATALOG),
          new CommandPaletteEntry(
              "Toggle error details", "Ctrl+E", CommandPaletteAction.TOGGLE_ERROR_DETAILS));
  private static final List<String> POST_GENERATION_ACTION_LABELS =
      List.of("Open in IDE", "Open in terminal", "Generate again", "Quit");
  private static final List<String> GLOBAL_HELP_LINES =
      List.of(
          "Global",
          "  Tab / Shift+Tab : move focus",
          "  Enter           : submit generation",
          "  Alt+G           : submit generation",
          "  Esc / Ctrl+C    : cancel generation or quit",
          "  ?               : toggle help",
          "  Ctrl+P          : command palette",
          "",
          "Extensions",
          "  / or Ctrl+F     : focus extension search",
          "  Esc             : clear search/filter or return to list",
          "  Ctrl+L          : focus extension list",
          "  Up/Down or j/k  : move in list",
          "  Home/End        : first/last list row",
          "  PgUp/PgDn       : previous/next category",
          "  Space           : toggle extension",
          "  v               : cycle category filter",
          "  x               : clear selected extensions",
          "  f               : toggle favorite",
          "  c / C           : close/open focused category / open all",
          "  Ctrl+J          : jump to next favorite",
          "  Ctrl+K          : toggle favorites filter",
          "  Ctrl+R          : reload extension catalog",
          "",
          "Diagnostics",
          "  Ctrl+E          : toggle expanded error details",
          "",
          "Help",
          "  Esc or ?        : close this help");
  private final EnumMap<FocusTarget, TextInputState> inputStates = new EnumMap<>(FocusTarget.class);
  private final ProjectRequestValidator requestValidator = new ProjectRequestValidator();
  private MetadataCompatibilityContext metadataCompatibility;
  private final UiScheduler scheduler;
  private final ExtensionCatalogState extensionCatalogState;
  private final ProjectGenerationRunner projectGenerationRunner;
  private final UiTheme theme;
  private final BodyPanelRenderer bodyPanelRenderer;
  private List<String> availableBuildTools;
  private List<String> availableJavaVersions;
  private List<String> availablePlatformStreams;

  private ProjectRequest request;
  private ValidationReport validation;
  private FocusTarget focusTarget;
  private String statusMessage;
  private String errorMessage;
  private boolean submitRequested;
  private boolean submitBlockedByValidation;
  private final GenerationStateTracker generationStateTracker;
  private final FooterLinesComposer footerLinesComposer;
  private final AsyncRepaintSignal asyncRepaintSignal;
  private CompletableFuture<Path> generationFuture;
  private long generationStartedAtNanos;
  private volatile boolean generationCancelRequested;
  private volatile long generationToken;
  private volatile long extensionCatalogLoadToken;
  private boolean extensionCatalogLoading;
  private String extensionCatalogErrorMessage;
  private String extensionCatalogSource;
  private boolean extensionCatalogStale;
  private ExtensionCatalogLoader extensionCatalogLoader;
  private String successHint;
  private boolean showErrorDetails;
  private boolean commandPaletteVisible;
  private boolean helpOverlayVisible;
  private int commandPaletteSelection;
  private boolean postGenerationMenuVisible;
  private int postGenerationActionSelection;
  private Path lastGeneratedProjectPath;
  private String lastGeneratedNextCommand;
  private PostGenerationExitPlan postGenerationExitPlan;
  private long startupOverlayMinDurationNanos;
  private long startupOverlayVisibleUntilNanos;
  private boolean startupOverlayVisibleOnLastTick;

  private CoreTuiController(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner,
      ExtensionFavoritesStore favoritesStore,
      Executor favoritesPersistenceExecutor) {
    Objects.requireNonNull(initialState);
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(debounceDelay);
    Objects.requireNonNull(projectGenerationRunner);
    Objects.requireNonNull(favoritesStore);
    Objects.requireNonNull(favoritesPersistenceExecutor);
    request = initialState.request();
    validation = initialState.validation();
    focusTarget = FocusTarget.PLATFORM_STREAM;
    statusMessage = "Ready";
    errorMessage = "";
    submitRequested = false;
    submitBlockedByValidation = false;
    metadataCompatibility = initialState.metadataCompatibility();
    this.scheduler = scheduler;
    this.projectGenerationRunner = projectGenerationRunner;
    theme = UiTheme.loadDefault();
    bodyPanelRenderer = new BodyPanelRenderer(theme);
    generationStateTracker = new GenerationStateTracker();
    footerLinesComposer = new FooterLinesComposer();
    asyncRepaintSignal = new AsyncRepaintSignal();
    generationFuture = null;
    generationStartedAtNanos = 0L;
    generationCancelRequested = false;
    generationToken = 0L;
    extensionCatalogLoadToken = 0L;
    extensionCatalogLoading = false;
    extensionCatalogErrorMessage = "";
    extensionCatalogSource = "snapshot";
    extensionCatalogStale = false;
    extensionCatalogLoader = null;
    successHint = "";
    showErrorDetails = false;
    commandPaletteVisible = false;
    helpOverlayVisible = false;
    commandPaletteSelection = 0;
    postGenerationMenuVisible = false;
    postGenerationActionSelection = 0;
    lastGeneratedProjectPath = null;
    lastGeneratedNextCommand = "";
    postGenerationExitPlan = null;
    startupOverlayMinDurationNanos = 0L;
    startupOverlayVisibleUntilNanos = 0L;
    startupOverlayVisibleOnLastTick = false;
    availableBuildTools = List.of();
    availableJavaVersions = List.of();
    availablePlatformStreams = List.of();

    for (FocusTarget target : FocusTarget.values()) {
      inputStates.put(target, new TextInputState(""));
    }
    inputStates.get(FocusTarget.GROUP_ID).setText(request.groupId());
    inputStates.get(FocusTarget.ARTIFACT_ID).setText(request.artifactId());
    inputStates.get(FocusTarget.VERSION).setText(request.version());
    inputStates.get(FocusTarget.PACKAGE_NAME).setText(request.packageName());
    inputStates.get(FocusTarget.OUTPUT_DIR).setText(request.outputDirectory());
    syncMetadataSelectors();
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
        favoritesPersistenceExecutor);
  }

  public static Executor defaultFavoritesPersistenceExecutor() {
    return ForkJoinPool.commonPool();
  }

  public void loadExtensionCatalogAsync(ExtensionCatalogLoader loader) {
    Objects.requireNonNull(loader);
    extensionCatalogLoader = loader;
    long loadToken = ++extensionCatalogLoadToken;
    extensionCatalogLoading = true;
    extensionCatalogErrorMessage = "";
    statusMessage = "Loading extension catalog...";
    startupOverlayVisibleUntilNanos =
        loadToken == 1 && startupOverlayMinDurationNanos > 0L
            ? System.nanoTime() + startupOverlayMinDurationNanos
            : 0L;
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
    if (event instanceof TickEvent tickEvent) {
      return handleTickEvent(tickEvent);
    }
    if (event instanceof ResizeEvent resizeEvent) {
      statusMessage = "Terminal resized to " + resizeEvent.width() + "x" + resizeEvent.height();
      return UiAction.handled(false);
    }
    if (!(event instanceof KeyEvent keyEvent)) {
      return UiAction.ignored();
    }
    UiAction helpOverlayAction = handleHelpOverlayKey(keyEvent);
    if (helpOverlayAction != null) {
      return helpOverlayAction;
    }
    if (shouldToggleHelpOverlay(keyEvent, focusTarget)) {
      toggleHelpOverlay();
      return UiAction.handled(false);
    }
    if (isCommandPaletteToggleKey(keyEvent)) {
      toggleCommandPalette();
      return UiAction.handled(false);
    }
    UiAction commandPaletteAction = handleCommandPaletteKey(keyEvent);
    if (commandPaletteAction != null) {
      return commandPaletteAction;
    }
    UiAction postGenerationAction = handlePostGenerationMenuKey(keyEvent);
    if (postGenerationAction != null) {
      return postGenerationAction;
    }

    if (shouldClearExtensionSearchOnCancel(keyEvent)) {
      clearExtensionSearchFilter();
      return UiAction.handled(false);
    }
    if (shouldDisableFavoritesFilterOnCancel(keyEvent)) {
      toggleFavoritesOnlyFilter();
      return UiAction.handled(false);
    }
    if (shouldDisableCategoryFilterOnCancel(keyEvent)) {
      clearCategoryFilter();
      return UiAction.handled(false);
    }
    if (shouldExitExtensionSearchOnCancel(keyEvent)) {
      focusExtensionList();
      return UiAction.handled(false);
    }

    if (shouldQuitKeyEvent(keyEvent)) {
      if (isGenerationInProgress()) {
        requestGenerationCancellation();
        return UiAction.handled(false);
      }
      cancelPendingAsyncOperations();
      return UiAction.handled(true);
    }

    if (isGenerationInProgress()) {
      if (keyEvent.isConfirm()) {
        statusMessage = "Generation already in progress. Press Esc to cancel.";
        return UiAction.handled(false);
      }
      statusMessage = "Generation in progress. Press Esc to cancel.";
      return UiAction.handled(false);
    }
    if (shouldFocusExtensionSearch(keyEvent, focusTarget)) {
      focusExtensionSearch();
      return UiAction.handled(false);
    }
    if (shouldFocusExtensionList(keyEvent)) {
      focusExtensionList();
      return UiAction.handled(false);
    }
    if (isCatalogReloadKey(keyEvent)) {
      requestCatalogReload();
      return UiAction.handled(false);
    }
    if (isFavoritesFilterToggleKey(keyEvent)) {
      toggleFavoritesOnlyFilter();
      return UiAction.handled(false);
    }
    if (isJumpToFavoriteKey(keyEvent)) {
      jumpToFavorite();
      return UiAction.handled(false);
    }
    if (isErrorDetailsToggleKey(keyEvent)) {
      toggleErrorDetails();
      return UiAction.handled(false);
    }
    if (keyEvent.isKey(dev.tamboui.tui.event.KeyCode.TAB) && keyEvent.hasShift()) {
      moveFocus(-1);
      return UiAction.handled(false);
    }
    if (keyEvent.isKey(dev.tamboui.tui.event.KeyCode.TAB) && !keyEvent.hasShift()) {
      moveFocus(1);
      return UiAction.handled(false);
    }
    if (keyEvent.isFocusNext()) {
      moveFocus(1);
      return UiAction.handled(false);
    }
    if (keyEvent.isFocusPrevious()) {
      moveFocus(-1);
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.SUBMIT) {
      if (isVimDownKey(keyEvent)) {
        moveFocus(1);
        return UiAction.handled(false);
      }
      if (isVimUpKey(keyEvent)) {
        moveFocus(-1);
        return UiAction.handled(false);
      }
    }

    if (keyEvent.isConfirm() || isGenerateShortcutKey(keyEvent)) {
      handleSubmitRequest();
      return UiAction.handled(false);
    }

    if (focusTarget == FocusTarget.EXTENSION_SEARCH && keyEvent.isDown()) {
      focusExtensionList();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && isUpNavigation(keyEvent)
        && extensionCatalogState.isSelectionAtTop()) {
      focusExtensionSearch();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isSectionHierarchyLeftKey(keyEvent)) {
      handleExtensionListHierarchyLeft();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isSectionHierarchyRightKey(keyEvent)) {
      handleExtensionListHierarchyRight();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isSectionJumpDownKey(keyEvent)) {
      jumpToAdjacentCategorySection(true);
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isSectionJumpUpKey(keyEvent)) {
      jumpToAdjacentCategorySection(false);
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST
        && keyEvent.isSelect()
        && extensionCatalogState.isCategorySectionHeaderSelected()) {
      toggleCategoryCollapseAtSelection();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isFavoriteToggleKey(keyEvent)) {
      toggleFavoriteAtSelection();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isCategoryFilterCycleKey(keyEvent)) {
      cycleCategoryFilter();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isClearSelectedExtensionsKey(keyEvent)) {
      clearSelectedExtensions();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isCategoryCollapseToggleKey(keyEvent)) {
      toggleCategoryCollapseAtSelection();
      return UiAction.handled(false);
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST && isExpandAllCategoriesKey(keyEvent)) {
      expandAllCategories();
      return UiAction.handled(false);
    }

    if (focusTarget == FocusTarget.EXTENSION_LIST
        && extensionCatalogState.handleListKeys(
            keyEvent, toggledName -> statusMessage = "Toggled extension: " + toggledName)) {
      return UiAction.handled(false);
    }

    if (isMetadataSelectorFocus(focusTarget) && handleMetadataSelectorKey(focusTarget, keyEvent)) {
      revalidate();
      refreshValidationFeedbackAfterEdit();
      return UiAction.handled(false);
    }

    if (isTextInputFocus(focusTarget)
        && handleTextInputKey(inputStates.get(focusTarget), keyEvent)) {
      if (focusTarget == FocusTarget.EXTENSION_SEARCH) {
        scheduleFilteredExtensionsRefresh();
      } else {
        rebuildRequestFromInputs();
        revalidate();
        refreshValidationFeedbackAfterEdit();
      }
      return UiAction.handled(false);
    }

    return UiAction.ignored();
  }

  public void setStartupOverlayMinDuration(Duration minimumDuration) {
    Objects.requireNonNull(minimumDuration);
    startupOverlayMinDurationNanos = Math.max(0L, minimumDuration.toNanos());
  }

  public void render(Frame frame) {
    reconcileGenerationCompletionIfDone();
    Rect area = frame.area();
    List<String> footerLines = footerLinesComposer.compose(area.width(), footerSnapshot());
    int footerHeight = estimateFooterHeight(footerLines, Math.max(1, area.width() - 2));
    List<Rect> rootLayout =
        Layout.vertical()
            .constraints(Constraint.length(1), Constraint.fill(), Constraint.length(footerHeight))
            .split(area);

    renderHeader(frame, rootLayout.get(0));
    renderBody(frame, rootLayout.get(1));
    renderFooter(frame, rootLayout.get(2), footerLines);
    if (isGenerationActive()) {
      renderGenerationOverlay(frame, area);
    }
    if (commandPaletteVisible) {
      renderCommandPalette(frame, area);
    }
    if (helpOverlayVisible) {
      renderHelpOverlay(frame, area);
    }
    if (postGenerationMenuVisible) {
      renderPostGenerationOverlay(frame, area);
    }
    if (isStartupOverlayVisible()) {
      renderStartupStatusOverlay(frame, area);
    }
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
    return Optional.ofNullable(postGenerationExitPlan);
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

  private void renderHeader(Frame frame, Rect area) {
    String text = "  QUARKUS FORGE  ─  Keyboard-first project generator";
    Paragraph header =
        Paragraph.builder()
            .text(text)
            .style(Style.EMPTY.fg(theme.color("accent")).bold())
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(header, area);
  }

  private void renderBody(Frame frame, Rect area) {
    List<Rect> bodyLayout;
    if (area.width() < NARROW_WIDTH_THRESHOLD) {
      bodyLayout =
          Layout.vertical().constraints(Constraint.ratio(1, 2), Constraint.ratio(1, 2)).split(area);
    } else {
      bodyLayout =
          Layout.horizontal()
              .constraints(Constraint.ratio(11, 20), Constraint.ratio(9, 20))
              .split(area);
    }

    bodyPanelRenderer.renderMetadataPanel(
        frame,
        bodyLayout.get(0),
        metadataPanelSnapshot(),
        this,
        CoreTuiController::panelTitle,
        this::panelBorderStyle);
    bodyPanelRenderer.renderExtensionsPanel(
        frame,
        bodyLayout.get(1),
        extensionsPanelSnapshot(),
        extensionCatalogState.listState(),
        this,
        CoreTuiController::panelTitle,
        this::panelBorderStyle,
        extensionCatalogState::isSelected,
        extensionCatalogState::isFavorite);
  }

  private BodyPanelRenderer.MetadataPanelSnapshot metadataPanelSnapshot() {
    return new BodyPanelRenderer.MetadataPanelSnapshot(
        metadataPanelTitle(), isMetadataFocused(), !validation.isValid());
  }

  private BodyPanelRenderer.ExtensionsPanelSnapshot extensionsPanelSnapshot() {
    return new BodyPanelRenderer.ExtensionsPanelSnapshot(
        extensionsPanelTitle(),
        isExtensionPanelFocused(),
        focusTarget == FocusTarget.EXTENSION_LIST,
        focusTarget == FocusTarget.SUBMIT,
        focusTarget == FocusTarget.EXTENSION_SEARCH,
        extensionCatalogLoading,
        extensionCatalogErrorMessage,
        extensionCatalogSource,
        extensionCatalogStale,
        extensionCatalogState.favoritesOnlyFilterEnabled(),
        extensionCatalogState.favoriteExtensionCount(),
        extensionCatalogState.activeCategoryFilterTitle(),
        extensionCatalogState.filteredExtensions().size(),
        extensionCatalogState.totalCatalogExtensionCount(),
        extensionCatalogState.filteredRows(),
        extensionCatalogState.selectedExtensionIds(),
        inputStates.get(FocusTarget.EXTENSION_SEARCH).text());
  }

  @Override
  public void renderSelector(Frame frame, Rect area, String label, FocusTarget target) {
    boolean focused = focusTarget == target;
    String value = selectorInlineLabel(target);

    StringBuilder line = new StringBuilder();
    line.append(String.format("  %-10s  ", label));

    String[] parts = value.split("  ");
    for (String part : parts) {
      if (part.startsWith("(*)")) {
        line.append("● ").append(part.substring(3).trim()).append("  ");
      } else if (part.startsWith("( )")) {
        line.append("○ ").append(part.substring(3).trim()).append("  ");
      }
    }

    if (focused) {
      line.append("◀ ▶");
    }

    Style style = Style.EMPTY.fg(focused ? theme.color("focus") : theme.color("text"));
    if (hasValidationErrorFor(target)) {
      style = Style.EMPTY.fg(theme.color("error"));
    }

    Paragraph paragraph =
        Paragraph.builder().text(line.toString()).style(style).overflow(Overflow.ELLIPSIS).build();
    frame.renderWidget(paragraph, area);
  }

  @Override
  public void renderText(Frame frame, Rect area, String label, FocusTarget target) {
    boolean focused = focusTarget == target;
    String value = inputStates.get(target).text();
    if (value.isBlank()) {
      value = defaultValueFor(target);
    }

    String display = focused ? "[ " + value + "_ ]" : "[ " + value + " ]";
    String line = String.format("  %-10s  %s", label, display);

    Style style = Style.EMPTY.fg(focused ? theme.color("focus") : theme.color("text"));
    if (hasValidationErrorFor(target)) {
      style = Style.EMPTY.fg(theme.color("error"));
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

  private void renderFooter(Frame frame, Rect area, List<String> footerLines) {
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

  private static int estimateFooterHeight(List<String> lines, int availableWidth) {
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

  private void renderCommandPalette(Frame frame, Rect viewport) {
    if (viewport.width() < 28 || viewport.height() < 10) {
      return;
    }
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < COMMAND_PALETTE_ENTRIES.size(); i++) {
      CommandPaletteEntry entry = COMMAND_PALETTE_ENTRIES.get(i);
      String prefix = i == commandPaletteSelection ? "> " : "  ";
      lines.add(prefix + (i + 1) + ". " + entry.label() + " [" + entry.shortcut() + "]");
    }
    lines.add("");
    lines.add("Enter: run | 1-9: quick run | Up/Down: navigate | Esc or Ctrl+P: close");

    int maxLineLength = lines.stream().mapToInt(String::length).max().orElse(40);
    int width = Math.min(Math.max(56, maxLineLength + 4), viewport.width() - 2);
    int height = Math.min(lines.size() + 2, viewport.height() - 2);
    Rect overlayArea =
        new Rect(
            viewport.x() + Math.max(0, (viewport.width() - width) / 2),
            viewport.y() + Math.max(0, (viewport.height() - height) / 2),
            width,
            height);

    Paragraph palette =
        Paragraph.builder()
            .text(String.join("\n", lines))
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title("Command Palette [focus]")
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(theme.color("focus")).bold())
                    .build())
            .build();
    frame.renderWidget(palette, overlayArea);
  }

  private boolean isGenerationActive() {
    GenerationState state = generationStateTracker.currentState();
    return state == GenerationState.VALIDATING || state == GenerationState.LOADING;
  }

  private void renderGenerationOverlay(Frame frame, Rect viewport) {
    if (viewport.width() < 30 || viewport.height() < 8) {
      return;
    }
    int width = Math.min(60, viewport.width() - 4);
    int height = 7;
    Rect overlayArea =
        new Rect(
            viewport.x() + Math.max(0, (viewport.width() - width) / 2),
            viewport.y() + Math.max(0, (viewport.height() - height) / 2),
            width,
            height);

    double ratio = generationStateTracker.progressRatio();
    String phase = generationStateTracker.progressPhase();
    int percent = (int) (ratio * 100);
    String percentLabel = percent + "%";

    Block overlayBlock =
        Block.builder()
            .title("Generating Project (" + percentLabel + ")")
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(theme.color("accent")).bold())
            .build();

    Rect inner = overlayBlock.inner(overlayArea);
    frame.renderWidget(overlayBlock, overlayArea);

    if (inner.isEmpty() || inner.height() < 4) {
      return;
    }

    List<Rect> rows =
        Layout.vertical()
            .constraints(
                Constraint.length(1), Constraint.length(1), Constraint.length(1), Constraint.fill())
            .split(inner);

    Paragraph phaseLine =
        Paragraph.builder()
            .text("  " + phase)
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(phaseLine, rows.get(0));

    LineGauge gauge =
        LineGauge.builder()
            .ratio(ratio)
            .label("  ")
            .lineSet(LineGauge.THICK)
            .filledStyle(Style.EMPTY.fg(theme.color("accent")))
            .unfilledStyle(Style.EMPTY.fg(theme.color("muted")))
            .style(Style.EMPTY.bg(theme.color("base")))
            .build();
    frame.renderWidget(gauge, rows.get(1));

    Paragraph emptyLine =
        Paragraph.builder().text("").style(Style.EMPTY.bg(theme.color("base"))).build();
    frame.renderWidget(emptyLine, rows.get(2));

    Paragraph hintLine =
        Paragraph.builder()
            .text("  Esc: cancel")
            .style(Style.EMPTY.fg(theme.color("muted")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(hintLine, rows.get(3));
  }

  private void renderHelpOverlay(Frame frame, Rect viewport) {
    if (viewport.width() < 36 || viewport.height() < 12) {
      return;
    }
    List<String> helpLines = helpOverlayLines();
    int maxLineLength = helpLines.stream().mapToInt(String::length).max().orElse(40);
    int width = Math.min(Math.max(68, maxLineLength + 4), viewport.width() - 2);
    int height = Math.min(helpLines.size() + 2, viewport.height() - 2);
    Rect overlayArea =
        new Rect(
            viewport.x() + Math.max(0, (viewport.width() - width) / 2),
            viewport.y() + Math.max(0, (viewport.height() - height) / 2),
            width,
            height);

    Paragraph helpOverlay =
        Paragraph.builder()
            .text(String.join("\n", helpLines))
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title(helpOverlayTitle())
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(theme.color("focus")).bold())
                    .build())
            .build();
    frame.renderWidget(helpOverlay, overlayArea);
  }

  private List<String> helpOverlayLines() {
    List<String> lines = new ArrayList<>(GLOBAL_HELP_LINES);
    lines.add("");
    lines.add("Context (" + contextHelpTitle() + ")");
    lines.addAll(contextHelpLines());
    return lines;
  }

  private String helpOverlayTitle() {
    return "Help [focus] - " + contextHelpTitle();
  }

  private String contextHelpTitle() {
    if (postGenerationMenuVisible) {
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
    if (postGenerationMenuVisible) {
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
              "  Esc             : clear filter or return to list");
      case EXTENSION_LIST ->
          List.of(
              "  Up/Down or j/k  : move list selection",
              "  Space           : toggle extension",
              "  f               : toggle favorite",
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

  private void renderStartupStatusOverlay(Frame frame, Rect viewport) {
    if (viewport.width() < 44 || viewport.height() < 12) {
      return;
    }
    List<String> lines = startupStatusLines();
    int maxLineLength = lines.stream().mapToInt(String::length).max().orElse(40);
    int width = Math.min(Math.max(64, maxLineLength + 4), viewport.width() - 2);
    int height = Math.min(lines.size() + 2, viewport.height() - 2);
    Rect overlayArea =
        new Rect(
            viewport.x() + Math.max(0, (viewport.width() - width) / 2),
            viewport.y() + Math.max(0, (viewport.height() - height) / 2),
            width,
            height);

    Paragraph overlay =
        Paragraph.builder()
            .text(String.join("\n", lines))
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title("Startup")
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(theme.color("accent")).bold())
                    .build())
            .build();
    frame.renderWidget(overlay, overlayArea);
  }

  private List<String> startupStatusLines() {
    String metadataLabel =
        metadataCompatibility.loadError() == null ? "done" : "done (snapshot fallback)";
    String catalogLabel = extensionCatalogLoading ? "in progress" : "done";
    String readyLabel = extensionCatalogLoading ? "waiting" : "ready";
    String spinner = extensionCatalogLoading ? "|" : "-";
    List<String> lines = new ArrayList<>();
    lines.addAll(STARTUP_SPLASH_ART);
    lines.add("");
    lines.add("  metadata fetch   : " + metadataLabel);
    lines.add("  catalog load     : " + catalogLabel);
    lines.add("  ready            : " + readyLabel);
    lines.add("");
    lines.add("  " + spinner + " Please wait...");
    return List.copyOf(lines);
  }

  private void renderPostGenerationOverlay(Frame frame, Rect viewport) {
    if (viewport.width() < 36 || viewport.height() < 10) {
      return;
    }
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < POST_GENERATION_ACTION_LABELS.size(); i++) {
      String prefix = i == postGenerationActionSelection ? "> " : "  ";
      lines.add(prefix + (i + 1) + ". " + POST_GENERATION_ACTION_LABELS.get(i));
    }
    lines.add("");
    lines.add("Enter: select | Up/Down or j/k: navigate | Esc: quit");

    int maxLineLength = lines.stream().mapToInt(String::length).max().orElse(40);
    int width = Math.min(Math.max(58, maxLineLength + 4), viewport.width() - 2);
    int height = Math.min(lines.size() + 2, viewport.height() - 2);
    Rect overlayArea =
        new Rect(
            viewport.x() + Math.max(0, (viewport.width() - width) / 2),
            viewport.y() + Math.max(0, (viewport.height() - height) / 2),
            width,
            height);

    Paragraph overlay =
        Paragraph.builder()
            .text(String.join("\n", lines))
            .style(Style.EMPTY.fg(theme.color("text")).bg(theme.color("base")))
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title("Project Generated [focus]")
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(theme.color("focus")).bold())
                    .build())
            .build();
    frame.renderWidget(overlay, overlayArea);
  }

  private FooterLinesComposer.FooterSnapshot footerSnapshot() {
    return new FooterLinesComposer.FooterSnapshot(
        isGenerationInProgress(),
        focusTarget,
        commandPaletteVisible,
        helpOverlayVisible,
        postGenerationMenuVisible,
        statusMessage,
        activeErrorDetails(),
        showErrorDetails,
        successHint);
  }

  private void moveFocus(int offset) {
    if (postGenerationMenuVisible) {
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

  private UiAction handleCommandPaletteKey(KeyEvent keyEvent) {
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
    if (keyEvent.isUp() || isVimUpKey(keyEvent)) {
      moveCommandPaletteSelection(-1);
      return UiAction.handled(false);
    }
    if (keyEvent.isDown() || isVimDownKey(keyEvent)) {
      moveCommandPaletteSelection(1);
      return UiAction.handled(false);
    }
    if (keyEvent.isHome()) {
      commandPaletteSelection = 0;
      return UiAction.handled(false);
    }
    if (keyEvent.isEnd()) {
      commandPaletteSelection = COMMAND_PALETTE_ENTRIES.size() - 1;
      return UiAction.handled(false);
    }
    if (isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < COMMAND_PALETTE_ENTRIES.size()) {
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

  private UiAction handlePostGenerationMenuKey(KeyEvent keyEvent) {
    if (!postGenerationMenuVisible) {
      return null;
    }
    if (keyEvent.isCtrlC()) {
      cancelPendingAsyncOperations();
      selectPostGenerationExit(PostGenerationExitAction.QUIT);
      return UiAction.handled(true);
    }
    if (keyEvent.isCancel()) {
      cancelPendingAsyncOperations();
      selectPostGenerationExit(PostGenerationExitAction.QUIT);
      return UiAction.handled(true);
    }
    if (keyEvent.isUp() || isVimUpKey(keyEvent)) {
      movePostGenerationSelection(-1);
      return UiAction.handled(false);
    }
    if (keyEvent.isDown() || isVimDownKey(keyEvent)) {
      movePostGenerationSelection(1);
      return UiAction.handled(false);
    }
    if (keyEvent.isKey(dev.tamboui.tui.event.KeyCode.TAB) && keyEvent.hasShift()) {
      movePostGenerationSelection(-1);
      return UiAction.handled(false);
    }
    if (keyEvent.isKey(dev.tamboui.tui.event.KeyCode.TAB) && !keyEvent.hasShift()) {
      movePostGenerationSelection(1);
      return UiAction.handled(false);
    }
    if (isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < POST_GENERATION_ACTION_LABELS.size()) {
        postGenerationActionSelection = selected;
        return executePostGenerationSelection();
      }
      return UiAction.handled(false);
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      return executePostGenerationSelection();
    }
    return UiAction.handled(false);
  }

  private void movePostGenerationSelection(int delta) {
    int size = POST_GENERATION_ACTION_LABELS.size();
    if (size == 0) {
      return;
    }
    postGenerationActionSelection = Math.floorMod(postGenerationActionSelection + delta, size);
  }

  private UiAction executePostGenerationSelection() {
    PostGenerationExitAction action =
        switch (postGenerationActionSelection) {
          case 0 -> PostGenerationExitAction.OPEN_IDE;
          case 1 -> PostGenerationExitAction.OPEN_TERMINAL;
          case 2 -> PostGenerationExitAction.GENERATE_AGAIN;
          default -> PostGenerationExitAction.QUIT;
        };
    if (action == PostGenerationExitAction.GENERATE_AGAIN) {
      postGenerationMenuVisible = false;
      postGenerationActionSelection = 0;
      postGenerationExitPlan = null;
      successHint = "";
      lastGeneratedProjectPath = null;
      lastGeneratedNextCommand = "";
      resetGenerationStateAfterTerminalOutcome();
      statusMessage = "Ready for next generation";
      errorMessage = "";
      return UiAction.handled(false);
    }
    cancelPendingAsyncOperations();
    selectPostGenerationExit(action);
    return UiAction.handled(true);
  }

  private void selectPostGenerationExit(PostGenerationExitAction action) {
    postGenerationMenuVisible = false;
    postGenerationActionSelection = 0;
    postGenerationExitPlan =
        new PostGenerationExitPlan(action, lastGeneratedProjectPath, lastGeneratedNextCommand);
  }

  private void moveCommandPaletteSelection(int delta) {
    int size = COMMAND_PALETTE_ENTRIES.size();
    if (size == 0) {
      return;
    }
    commandPaletteSelection = Math.floorMod(commandPaletteSelection + delta, size);
  }

  private void executeCommandPaletteSelection() {
    if (COMMAND_PALETTE_ENTRIES.isEmpty()) {
      closeCommandPalette();
      return;
    }
    CommandPaletteAction action = COMMAND_PALETTE_ENTRIES.get(commandPaletteSelection).action();
    closeCommandPalette();
    executeCommandPaletteAction(action);
  }

  private void executeCommandPaletteAction(CommandPaletteAction action) {
    switch (action) {
      case FOCUS_EXTENSION_SEARCH -> focusExtensionSearch();
      case FOCUS_EXTENSION_LIST -> focusExtensionList();
      case TOGGLE_FAVORITES_FILTER -> toggleFavoritesOnlyFilter();
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

  private void toggleCommandPalette() {
    if (commandPaletteVisible) {
      closeCommandPalette();
      statusMessage = "Command palette closed";
      return;
    }
    if (isGenerationInProgress()) {
      statusMessage = "Generation in progress. Press Esc to cancel.";
      return;
    }
    if (postGenerationMenuVisible) {
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

  private UiAction handleHelpOverlayKey(KeyEvent keyEvent) {
    if (!helpOverlayVisible) {
      return null;
    }
    if (keyEvent.isCtrlC()) {
      cancelPendingAsyncOperations();
      return UiAction.handled(true);
    }
    if (keyEvent.isCancel() || isHelpOverlayToggleKey(keyEvent)) {
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

  private void toggleHelpOverlay() {
    if (helpOverlayVisible) {
      closeHelpOverlay();
      statusMessage = "Help closed";
      return;
    }
    if (isGenerationInProgress()) {
      statusMessage = "Generation in progress. Press Esc to cancel.";
      return;
    }
    if (postGenerationMenuVisible) {
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

  private String extensionsPanelTitle() {
    if (extensionCatalogLoading) {
      return "Extensions [loading]";
    }
    if (!extensionCatalogErrorMessage.isBlank()) {
      return "Extensions [fallback]";
    }
    return "Extensions";
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

  private String activeErrorDetails() {
    if (!errorMessage.isBlank()) {
      return errorMessage;
    }
    if (!extensionCatalogErrorMessage.isBlank()) {
      return extensionCatalogErrorMessage;
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
    availableBuildTools =
        resolveSelectorOptions(
            metadataSnapshot == null ? List.of() : metadataSnapshot.buildTools(),
            request.buildTool());
    availableJavaVersions =
        resolveSelectorOptions(
            metadataSnapshot == null ? List.of() : metadataSnapshot.javaVersions(),
            request.javaVersion());
    availablePlatformStreams =
        resolvePlatformStreamOptions(metadataSnapshot, request.platformStream());

    String selectedPlatformStream =
        normalizeSelectedPlatformStream(
            request.platformStream(), metadataSnapshot, availablePlatformStreams);
    String selectedBuildTool = normalizeSelectedOption(request.buildTool(), availableBuildTools);
    String selectedJavaVersion =
        normalizeSelectedOption(request.javaVersion(), availableJavaVersions);

    request =
        CliPrefillMapper.map(
            new CliPrefill(
                inputStates.get(FocusTarget.GROUP_ID).text(),
                inputStates.get(FocusTarget.ARTIFACT_ID).text(),
                inputStates.get(FocusTarget.VERSION).text(),
                inputStates.get(FocusTarget.PACKAGE_NAME).text(),
                inputStates.get(FocusTarget.OUTPUT_DIR).text(),
                selectedPlatformStream,
                selectedBuildTool,
                selectedJavaVersion));

    inputStates
        .get(FocusTarget.PLATFORM_STREAM)
        .setText(selectorInlineLabel(FocusTarget.PLATFORM_STREAM));
    inputStates.get(FocusTarget.BUILD_TOOL).setText(selectorInlineLabel(FocusTarget.BUILD_TOOL));
    inputStates
        .get(FocusTarget.JAVA_VERSION)
        .setText(selectorInlineLabel(FocusTarget.JAVA_VERSION));
    inputStates.get(FocusTarget.PLATFORM_STREAM).moveCursorToStart();
    inputStates.get(FocusTarget.BUILD_TOOL).moveCursorToStart();
    inputStates.get(FocusTarget.JAVA_VERSION).moveCursorToStart();
  }

  private boolean handleMetadataSelectorKey(FocusTarget target, KeyEvent keyEvent) {
    if (keyEvent.isLeft() || isVimLeftKey(keyEvent) || keyEvent.isUp() || isVimUpKey(keyEvent)) {
      return cycleSelector(target, -1);
    }
    if (keyEvent.isRight()
        || isVimRightKey(keyEvent)
        || keyEvent.isDown()
        || isVimDownKey(keyEvent)) {
      return cycleSelector(target, 1);
    }
    if (keyEvent.isHome()) {
      return selectSelectorEdge(target, true);
    }
    if (keyEvent.isEnd()) {
      return selectSelectorEdge(target, false);
    }
    return false;
  }

  private String selectorInlineLabel(FocusTarget target) {
    List<String> options = selectorOptionsFor(target);
    if (options.isEmpty()) {
      return "( ) no options available";
    }

    String selected = selectorValue(target);
    List<String> labels = new ArrayList<>();
    for (String option : options) {
      boolean selectedOption = option.equalsIgnoreCase(selected);
      labels.add((selectedOption ? "(*) " : "( ) ") + selectorOptionLabel(target, option));
    }
    return String.join("  ", labels);
  }

  private String selectorOptionLabel(FocusTarget target, String option) {
    if (target != FocusTarget.PLATFORM_STREAM) {
      return option.isBlank() ? "default" : option;
    }
    if (option.isBlank()) {
      return "default";
    }

    MetadataDto metadataSnapshot = metadataCompatibility.metadataSnapshot();
    if (metadataSnapshot == null) {
      return option;
    }
    MetadataDto.PlatformStream platformStream = metadataSnapshot.findPlatformStream(option);
    if (platformStream == null) {
      return option;
    }
    return platformStream.recommended()
        ? platformStream.platformVersion() + "*"
        : platformStream.platformVersion();
  }

  private boolean cycleSelector(FocusTarget target, int delta) {
    List<String> options = selectorOptionsFor(target);
    if (options.isEmpty()) {
      return false;
    }
    int currentIndex = indexOfOption(options, selectorValue(target));
    if (currentIndex < 0) {
      currentIndex = 0;
    }
    int selectedIndex = Math.floorMod(currentIndex + delta, options.size());
    applySelector(target, options.get(selectedIndex));
    return true;
  }

  private boolean selectSelectorEdge(FocusTarget target, boolean first) {
    List<String> options = selectorOptionsFor(target);
    if (options.isEmpty()) {
      return false;
    }
    applySelector(target, first ? options.getFirst() : options.getLast());
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

  private List<String> selectorOptionsFor(FocusTarget target) {
    return switch (target) {
      case PLATFORM_STREAM -> availablePlatformStreams;
      case BUILD_TOOL -> availableBuildTools;
      case JAVA_VERSION -> availableJavaVersions;
      default -> List.of();
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

  private static List<String> resolveSelectorOptions(
      List<String> metadataValues, String fallbackValue) {
    List<String> options = new ArrayList<>();
    for (String metadataValue : metadataValues) {
      if (metadataValue == null || metadataValue.isBlank()) {
        continue;
      }
      if (indexOfOption(options, metadataValue) < 0) {
        options.add(metadataValue);
      }
    }
    if (options.isEmpty() && fallbackValue != null && !fallbackValue.isBlank()) {
      options.add(fallbackValue);
    }
    return List.copyOf(options);
  }

  private static List<String> resolvePlatformStreamOptions(
      MetadataDto metadataSnapshot, String fallbackValue) {
    List<String> options = new ArrayList<>();
    if (metadataSnapshot != null) {
      for (MetadataDto.PlatformStream platformStream : metadataSnapshot.platformStreams()) {
        if (platformStream.key().isBlank()) {
          continue;
        }
        if (indexOfOption(options, platformStream.key()) < 0) {
          options.add(platformStream.key());
        }
      }
    }
    if (options.isEmpty()) {
      if (fallbackValue != null && !fallbackValue.isBlank()) {
        options.add(fallbackValue);
      } else {
        options.add("");
      }
    }
    return List.copyOf(options);
  }

  private static String normalizeSelectedOption(String currentValue, List<String> options) {
    if (options.isEmpty()) {
      return currentValue == null ? "" : currentValue.trim();
    }
    int index = indexOfOption(options, currentValue);
    return index >= 0 ? options.get(index) : options.getFirst();
  }

  private static String normalizeSelectedPlatformStream(
      String currentValue, MetadataDto metadataSnapshot, List<String> options) {
    int explicitIndex = indexOfOption(options, currentValue);
    if (explicitIndex >= 0) {
      return options.get(explicitIndex);
    }
    if (metadataSnapshot != null && !metadataSnapshot.platformStreams().isEmpty()) {
      int recommendedIndex =
          indexOfOption(options, metadataSnapshot.recommendedPlatformStreamKey());
      if (recommendedIndex >= 0) {
        return options.get(recommendedIndex);
      }
    }
    return options.isEmpty() ? "" : options.getFirst();
  }

  private static int indexOfOption(List<String> options, String candidate) {
    if (candidate == null || candidate.isBlank()) {
      return options.indexOf("");
    }
    for (int i = 0; i < options.size(); i++) {
      if (options.get(i).equalsIgnoreCase(candidate.trim())) {
        return i;
      }
    }
    return -1;
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
    extensionCatalogLoading = false;
    extensionCatalogState.cancelPendingAsync();
  }

  private void startGenerationFlow() {
    successHint = "";
    postGenerationMenuVisible = false;
    postGenerationExitPlan = null;
    lastGeneratedProjectPath = null;
    lastGeneratedNextCommand = "";
    extensionCatalogState.cancelPendingAsync();
    generationCancelRequested = false;
    long token = ++generationToken;
    if (!transitionGenerationState(GenerationState.LOADING)) {
      statusMessage = "Submit ignored in state: " + generationStateLabel();
      return;
    }
    generationStartedAtNanos = System.nanoTime();
    onGenerationProgress(
        token,
        GenerationProgressUpdate.requestingArchive(
            "requesting project archive from Quarkus API..."));

    GenerationRequest generationRequest = toGenerationRequest();
    Path outputDirectory = resolveGeneratedProjectDirectory();

    CompletableFuture<Path> startedFuture;
    try {
      startedFuture =
          projectGenerationRunner.generate(
              generationRequest,
              outputDirectory,
              () -> generationCancelRequested || token != generationToken,
              progressUpdate ->
                  scheduleOnRenderThread(() -> onGenerationProgress(token, progressUpdate)));
    } catch (RuntimeException runtimeException) {
      onGenerationCompleted(token, null, runtimeException);
      return;
    }
    if (startedFuture == null) {
      onGenerationCompleted(
          token, null, new IllegalStateException("Generation service returned null future"));
      return;
    }

    generationFuture = startedFuture;
    startedFuture.whenComplete(
        (generatedPath, throwable) ->
            scheduleOnRenderThread(() -> onGenerationCompleted(token, generatedPath, throwable)));
  }

  private void onGenerationProgress(long token, GenerationProgressUpdate progressUpdate) {
    if (token != generationToken
        || generationStateTracker.currentState() != GenerationState.LOADING) {
      return;
    }
    generationStateTracker.updateProgress(progressUpdate);
    String progressMessage = progressUpdate.message().isBlank() ? "working..." : progressUpdate.message();
    statusMessage = "Generation in progress: " + progressMessage;
  }

  private void onGenerationCompleted(long token, Path generatedPath, Throwable throwable) {
    if (token != generationToken
        || generationStateTracker.currentState() != GenerationState.LOADING) {
      return;
    }
    generationFuture = null;

    Throwable cause = unwrapCompletionCause(throwable);
    if (cause == null && generatedPath != null) {
      transitionGenerationState(GenerationState.SUCCESS);
      Path normalizedPath = generatedPath.toAbsolutePath().normalize();
      String nextCommand = nextStepCommand(request.buildTool());
      lastGeneratedProjectPath = normalizedPath;
      lastGeneratedNextCommand = nextCommand;
      statusMessage = "Generation succeeded: " + normalizedPath;
      errorMessage = "";
      successHint = "cd " + normalizedPath + " && " + nextCommand;
      postGenerationMenuVisible = true;
      postGenerationActionSelection = 0;
      requestAsyncRepaint();
      return;
    }

    if (cause == null) {
      cause = new IllegalStateException("Generation finished without an output path");
    }

    if (cause instanceof CancellationException || generationCancelRequested) {
      transitionGenerationState(GenerationState.CANCELLED);
      statusMessage = "Generation cancelled. Update inputs and press Enter to retry.";
      errorMessage = "";
      successHint = "";
      postGenerationMenuVisible = false;
      requestAsyncRepaint();
      return;
    }

    transitionGenerationState(GenerationState.ERROR);
    statusMessage = "Generation failed.";
    errorMessage = userFriendlyError(cause);
    successHint = "";
    postGenerationMenuVisible = false;
    requestAsyncRepaint();
  }

  private void reconcileGenerationCompletionIfDone() {
    if (generationStateTracker.currentState() != GenerationState.LOADING
        || generationFuture == null) {
      return;
    }
    if (!generationFuture.isDone()) {
      return;
    }

    Path generatedPath = null;
    Throwable throwable = null;
    try {
      generatedPath = generationFuture.join();
    } catch (RuntimeException completionFailure) {
      throwable = completionFailure;
    }
    onGenerationCompleted(generationToken, generatedPath, throwable);
  }

  private void requestGenerationCancellation() {
    if (generationStateTracker.currentState() != GenerationState.LOADING) {
      return;
    }
    if (generationFuture != null && generationFuture.isDone()) {
      reconcileGenerationCompletionIfDone();
      return;
    }
    generationCancelRequested = true;
    statusMessage = "Cancellation requested. Waiting for cleanup...";
    errorMessage = "";
    if (generationFuture != null) {
      generationFuture.cancel(true);
    }
  }

  private boolean isGenerationInProgress() {
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
    Path outputRoot = Path.of(request.outputDirectory());
    return outputRoot.resolve(request.artifactId());
  }

  private void resetGenerationStateAfterTerminalOutcome() {
    generationStateTracker.resetAfterTerminalOutcome();
  }

  private String generationStateLabel() {
    return generationStateTracker.stateLabel();
  }

  private void scheduleOnRenderThread(Runnable task) {
    scheduler.schedule(Duration.ZERO, task);
  }

  private boolean transitionGenerationState(GenerationState targetState) {
    return generationStateTracker.transitionTo(targetState);
  }

  static boolean isValidTransition(GenerationState currentState, GenerationState targetState) {
    return GenerationStateTracker.isValidTransition(currentState, targetState);
  }

  private static Throwable unwrapCompletionCause(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    if (throwable instanceof CompletionException completionException
        && completionException.getCause() != null) {
      return completionException.getCause();
    }
    return throwable;
  }

  private static String userFriendlyError(Throwable throwable) {
    return ApiErrorMessages.userFriendlyMessage(throwable);
  }

  private static String catalogLoadFailureMessage(Throwable throwable) {
    String message = userFriendlyError(throwable);
    if (message.contains("no valid cache snapshot found")) {
      return "Live catalog/cache unavailable. Using bundled snapshot (Ctrl+R to retry).";
    }
    return "Catalog load failed: " + message;
  }

  private String catalogReloadFailureStatusMessage() {
    if ("snapshot".equals(extensionCatalogSource) && !extensionCatalogStale) {
      return "Using fallback extension catalog";
    }
    return "Catalog reload failed; keeping current catalog";
  }

  private static String nextStepCommand(String buildTool) {
    String normalizedBuildTool = BuildToolCodec.toUiValue(buildTool);
    if ("gradle".equals(normalizedBuildTool) || "gradle-kotlin-dsl".equals(normalizedBuildTool)) {
      return "./gradlew quarkusDev";
    }
    return "mvn quarkus:dev";
  }

  @FunctionalInterface
  public interface ExtensionCatalogLoader {
    CompletableFuture<ExtensionCatalogLoadResult> load();
  }

  public record ExtensionCatalogLoadResult(
      List<ExtensionDto> extensions,
      CatalogSource source,
      boolean stale,
      String detailMessage,
      MetadataDto metadata) {
    public ExtensionCatalogLoadResult {
      extensions = List.copyOf(Objects.requireNonNull(extensions));
      source = Objects.requireNonNull(source);
      detailMessage = detailMessage == null ? "" : detailMessage.strip();

      if (source != CatalogSource.CACHE && stale) {
        throw new IllegalArgumentException("stale flag is allowed only for cache source");
      }
    }

    public static ExtensionCatalogLoadResult live(List<ExtensionDto> extensions) {
      return new ExtensionCatalogLoadResult(extensions, CatalogSource.LIVE, false, "", null);
    }
  }

  private static boolean isTextInputFocus(FocusTarget focusTarget) {
    return focusTarget == FocusTarget.GROUP_ID
        || focusTarget == FocusTarget.ARTIFACT_ID
        || focusTarget == FocusTarget.VERSION
        || focusTarget == FocusTarget.PACKAGE_NAME
        || focusTarget == FocusTarget.OUTPUT_DIR
        || focusTarget == FocusTarget.EXTENSION_SEARCH;
  }

  private static boolean isMetadataSelectorFocus(FocusTarget focusTarget) {
    return focusTarget == FocusTarget.PLATFORM_STREAM
        || focusTarget == FocusTarget.BUILD_TOOL
        || focusTarget == FocusTarget.JAVA_VERSION;
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

  private UiAction handleTickEvent(TickEvent ignored) {
    boolean startupOverlayVisibleNow = isStartupOverlayVisible();
    boolean shouldRender = extensionCatalogLoading || startupOverlayVisibleNow;
    if (startupOverlayVisibleOnLastTick && !startupOverlayVisibleNow) {
      // Force one final redraw to clear expired startup overlay without key input.
      shouldRender = true;
    }
    startupOverlayVisibleOnLastTick = startupOverlayVisibleNow;
    if (asyncRepaintSignal.consume()) {
      shouldRender = true;
    }
    if (generationStateTracker.currentState() == GenerationState.LOADING) {
      if (!generationCancelRequested) {
        long elapsedMillis = Math.max(0L, (System.nanoTime() - generationStartedAtNanos) / 1_000_000L);
        generationStateTracker.tick(elapsedMillis);
        statusMessage = "Generation in progress: " + generationStateTracker.progressPhase();
      }
      shouldRender = true;
    }
    return shouldRender ? new UiAction(true, false) : UiAction.ignored();
  }

  private void handleSubmitRequest() {
    postGenerationMenuVisible = false;
    postGenerationExitPlan = null;
    submitRequested = true;
    resetGenerationStateAfterTerminalOutcome();
    if (!transitionGenerationState(GenerationState.VALIDATING)) {
      statusMessage = "Submit ignored in state: " + generationStateLabel();
      return;
    }
    if (!validation.isValid()) {
      submitBlockedByValidation = true;
      errorMessage = firstValidationError(validation);
      statusMessage = "Submit blocked: invalid input";
      transitionGenerationState(GenerationState.ERROR);
      return;
    }

    submitBlockedByValidation = false;
    errorMessage = "";
    if (projectGenerationRunner == NOOP_PROJECT_GENERATION_RUNNER) {
      statusMessage =
          "Submit requested with "
              + extensionCatalogState.selectedExtensionCount()
              + " extension(s), but generation service is not configured.";
      transitionGenerationState(GenerationState.IDLE);
      return;
    }
    startGenerationFlow();
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
      applyCatalogLoadFailure(catalogLoadFailureMessage(unwrapCompletionCause(throwable)));
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
                        extension.order()))
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
    extensionCatalogLoading = false;
    extensionCatalogSource = result.source().label();
    extensionCatalogStale = result.stale();
    errorMessage = "";
    extensionCatalogErrorMessage = catalogPanelErrorMessage(result);
    statusMessage =
        !result.detailMessage().isBlank()
            ? result.detailMessage()
            : catalogLoadedStatusMessage(result.source(), result.stale());
    extensionCatalogState.replaceCatalog(
        items, inputStates.get(FocusTarget.EXTENSION_SEARCH).text(), ignored -> {});
    requestAsyncRepaint();
  }

  private boolean isStaleCatalogLoadToken(long loadToken) {
    return loadToken != extensionCatalogLoadToken;
  }

  private void applyCatalogLoadFailure(String message) {
    extensionCatalogLoading = false;
    extensionCatalogErrorMessage = message;
    errorMessage = message;
    statusMessage = catalogReloadFailureStatusMessage();
    requestAsyncRepaint();
  }

  private boolean isStartupOverlayVisible() {
    return extensionCatalogLoading || System.nanoTime() < startupOverlayVisibleUntilNanos;
  }

  private void requestAsyncRepaint() {
    asyncRepaintSignal.request();
  }

  private static String catalogPanelErrorMessage(ExtensionCatalogLoadResult result) {
    if (result.detailMessage().isBlank()) {
      return "";
    }
    return result.source() == CatalogSource.SNAPSHOT ? result.detailMessage() : "";
  }

  private void toggleFavoriteAtSelection() {
    ExtensionCatalogState.FavoriteToggleResult toggleResult =
        extensionCatalogState.toggleFavoriteAtSelection(this::updateExtensionFilterStatus);
    if (!toggleResult.changed()) {
      statusMessage = "No extension selected to favorite";
      return;
    }
    statusMessage =
        (toggleResult.favoriteNow() ? "Favorited extension: " : "Unfavorited extension: ")
            + toggleResult.extensionName();
  }

  private void cycleCategoryFilter() {
    ExtensionCatalogState.CategoryFilterResult result =
        extensionCatalogState.cycleCategoryFilter(this::updateExtensionFilterStatus);
    if (!result.filtered()) {
      statusMessage = "Category filter cleared (" + result.matchCount() + " matches)";
      return;
    }
    statusMessage =
        "Category filter: " + result.categoryTitle() + " (" + result.matchCount() + " matches)";
  }

  private void clearCategoryFilter() {
    boolean cleared = extensionCatalogState.clearCategoryFilter(this::updateExtensionFilterStatus);
    if (!cleared) {
      return;
    }
    statusMessage = "Category filter cleared";
  }

  private void clearSelectedExtensions() {
    int clearedCount = extensionCatalogState.clearSelectedExtensions();
    if (clearedCount == 0) {
      statusMessage = "No selected extensions to clear";
      return;
    }
    statusMessage =
        "Cleared " + clearedCount + " selected " + (clearedCount == 1 ? "extension" : "extensions");
  }

  private void toggleCategoryCollapseAtSelection() {
    ExtensionCatalogState.CategoryCollapseResult collapseResult =
        extensionCatalogState.toggleCategoryCollapseAtSelection();
    if (!collapseResult.changed()) {
      statusMessage = "No category selected to close";
      return;
    }
    statusMessage =
        (collapseResult.collapsed() ? "Closed category: " : "Opened category: ")
            + collapseResult.categoryTitle();
  }

  private void expandAllCategories() {
    int reopenedCount = extensionCatalogState.expandAllCategories();
    if (reopenedCount == 0) {
      statusMessage = "All categories are already open";
      return;
    }
    statusMessage =
        "Opened " + reopenedCount + " " + (reopenedCount == 1 ? "category" : "categories");
  }

  private void jumpToFavorite() {
    if (focusTarget != FocusTarget.EXTENSION_LIST) {
      focusExtensionList();
    }
    ExtensionCatalogState.JumpToFavoriteResult jumpResult = extensionCatalogState.jumpToFavorite();
    if (!jumpResult.jumped()) {
      statusMessage = "No favorite extension in current catalog view";
      return;
    }
    statusMessage = "Jumped to favorite: " + jumpResult.extensionName();
  }

  private void jumpToAdjacentCategorySection(boolean forward) {
    ExtensionCatalogState.SectionJumpResult jumpResult =
        extensionCatalogState.jumpToAdjacentSection(forward);
    if (!jumpResult.moved()) {
      statusMessage = forward ? "No next category section" : "No previous category section";
      return;
    }
    statusMessage = "Jumped to category: " + jumpResult.categoryTitle();
  }

  private void handleExtensionListHierarchyLeft() {
    ExtensionCatalogState.SectionFocusResult parentSectionResult =
        extensionCatalogState.focusParentSectionHeader();
    if (parentSectionResult.moved()) {
      statusMessage = "Moved to section: " + parentSectionResult.sectionTitle();
      return;
    }
    if (extensionCatalogState.isCategorySectionHeaderSelected()
        && !extensionCatalogState.isSelectedCategorySectionCollapsed()) {
      toggleCategoryCollapseAtSelection();
    }
  }

  private void handleExtensionListHierarchyRight() {
    ExtensionCatalogState.SectionFocusResult childResult =
        extensionCatalogState.focusFirstVisibleItemInSelectedSection();
    if (childResult.moved()) {
      statusMessage = "Moved to first item in section: " + childResult.sectionTitle();
      return;
    }
    if (extensionCatalogState.isCategorySectionHeaderSelected()
        && extensionCatalogState.isSelectedCategorySectionCollapsed()) {
      toggleCategoryCollapseAtSelection();
    }
  }

  private void toggleFavoritesOnlyFilter() {
    boolean enabled =
        extensionCatalogState.toggleFavoritesOnlyFilter(this::updateExtensionFilterStatus);
    statusMessage = enabled ? "Favorites filter enabled" : "Favorites filter disabled";
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

  private static boolean isCatalogReloadKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasCtrl()
        && (keyEvent.character() == 'r' || keyEvent.character() == 'R');
  }

  private static boolean isGenerateShortcutKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasAlt()
        && !keyEvent.hasCtrl()
        && (keyEvent.character() == 'g' || keyEvent.character() == 'G');
  }

  private static boolean isFavoriteToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == 'f' || keyEvent.character() == 'F');
  }

  private static boolean isClearSelectedExtensionsKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == 'x' || keyEvent.character() == 'X');
  }

  private static boolean isCategoryFilterCycleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == 'v' || keyEvent.character() == 'V');
  }

  private static boolean isJumpToFavoriteKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasCtrl()
        && (keyEvent.character() == 'j' || keyEvent.character() == 'J');
  }

  private static boolean isFavoritesFilterToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasCtrl()
        && (keyEvent.character() == 'k' || keyEvent.character() == 'K');
  }

  private static boolean isCategoryCollapseToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && keyEvent.character() == 'c';
  }

  private static boolean isExpandAllCategoriesKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && keyEvent.character() == 'C';
  }

  private static boolean isSectionJumpUpKey(KeyEvent keyEvent) {
    return keyEvent.isPageUp();
  }

  private static boolean isSectionJumpDownKey(KeyEvent keyEvent) {
    return keyEvent.isPageDown();
  }

  private static boolean isSectionHierarchyLeftKey(KeyEvent keyEvent) {
    return keyEvent.isLeft() || isVimLeftKey(keyEvent);
  }

  private static boolean isSectionHierarchyRightKey(KeyEvent keyEvent) {
    return keyEvent.isRight() || isVimRightKey(keyEvent);
  }

  private static boolean isCommandPaletteToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == 'p' || keyEvent.character() == 'P');
  }

  private static boolean isHelpOverlayToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && keyEvent.character() == '?';
  }

  private static boolean shouldToggleHelpOverlay(KeyEvent keyEvent, FocusTarget currentFocus) {
    if (!isHelpOverlayToggleKey(keyEvent)) {
      return false;
    }
    if (currentFocus == FocusTarget.EXTENSION_SEARCH) {
      return true;
    }
    return !isTextInputFocus(currentFocus);
  }

  private static boolean shouldQuitKeyEvent(KeyEvent keyEvent) {
    return keyEvent.isCancel() || keyEvent.isCtrlC();
  }

  private boolean shouldClearExtensionSearchOnCancel(KeyEvent keyEvent) {
    if (!keyEvent.isCancel() || isGenerationInProgress()) {
      return false;
    }
    if (focusTarget != FocusTarget.EXTENSION_SEARCH && focusTarget != FocusTarget.EXTENSION_LIST) {
      return false;
    }
    return !inputStates.get(FocusTarget.EXTENSION_SEARCH).text().isBlank();
  }

  private boolean shouldExitExtensionSearchOnCancel(KeyEvent keyEvent) {
    if (!keyEvent.isCancel() || isGenerationInProgress()) {
      return false;
    }
    if (focusTarget != FocusTarget.EXTENSION_SEARCH) {
      return false;
    }
    return inputStates.get(FocusTarget.EXTENSION_SEARCH).text().isBlank();
  }

  private boolean shouldDisableFavoritesFilterOnCancel(KeyEvent keyEvent) {
    if (!keyEvent.isCancel() || isGenerationInProgress()) {
      return false;
    }
    if (focusTarget != FocusTarget.EXTENSION_SEARCH && focusTarget != FocusTarget.EXTENSION_LIST) {
      return false;
    }
    if (!inputStates.get(FocusTarget.EXTENSION_SEARCH).text().isBlank()) {
      return false;
    }
    return extensionCatalogState.favoritesOnlyFilterEnabled();
  }

  private boolean shouldDisableCategoryFilterOnCancel(KeyEvent keyEvent) {
    if (!keyEvent.isCancel() || isGenerationInProgress()) {
      return false;
    }
    if (focusTarget != FocusTarget.EXTENSION_SEARCH && focusTarget != FocusTarget.EXTENSION_LIST) {
      return false;
    }
    if (!inputStates.get(FocusTarget.EXTENSION_SEARCH).text().isBlank()) {
      return false;
    }
    if (extensionCatalogState.favoritesOnlyFilterEnabled()) {
      return false;
    }
    return !extensionCatalogState.activeCategoryFilterTitle().isBlank();
  }

  private static boolean isErrorDetailsToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasCtrl()
        && (keyEvent.character() == 'e' || keyEvent.character() == 'E');
  }

  private static boolean isUpNavigation(KeyEvent keyEvent) {
    return keyEvent.isUp() || isVimUpKey(keyEvent);
  }

  private static boolean isVimUpKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'k', 'K');
  }

  private static boolean isVimDownKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'j', 'J');
  }

  private static boolean isVimLeftKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'h', 'H');
  }

  private static boolean isVimRightKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'l', 'L');
  }

  private static boolean isPlainChar(KeyEvent keyEvent, char lower, char upper) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == lower || keyEvent.character() == upper);
  }

  private static boolean isDigitKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && Character.isDigit(keyEvent.character());
  }

  private static boolean shouldFocusExtensionSearch(KeyEvent keyEvent, FocusTarget currentFocus) {
    if (keyEvent.code() != dev.tamboui.tui.event.KeyCode.CHAR) {
      return false;
    }
    if (!keyEvent.hasCtrl() && !keyEvent.hasAlt() && keyEvent.character() == '/') {
      return !isTextInputFocus(currentFocus) && currentFocus != FocusTarget.EXTENSION_SEARCH;
    }
    return keyEvent.hasCtrl() && (keyEvent.character() == 'f' || keyEvent.character() == 'F');
  }

  private static boolean shouldFocusExtensionList(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && keyEvent.hasCtrl()
        && (keyEvent.character() == 'l' || keyEvent.character() == 'L');
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
      case SNAPSHOT -> "Loaded extension catalog from bundled snapshot";
    };
  }

  @FunctionalInterface
  public interface ProjectGenerationRunner {
    CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<GenerationProgressUpdate> progressListener);
  }

  public enum GenerationProgressStep {
    REQUESTING_ARCHIVE,
    EXTRACTING_ARCHIVE,
    FINALIZING
  }

  public record GenerationProgressUpdate(GenerationProgressStep step, String message) {
    public GenerationProgressUpdate {
      step = Objects.requireNonNull(step);
      message = message == null ? "" : message.strip();
    }

    public static GenerationProgressUpdate requestingArchive(String message) {
      return new GenerationProgressUpdate(GenerationProgressStep.REQUESTING_ARCHIVE, message);
    }

    public static GenerationProgressUpdate extractingArchive(String message) {
      return new GenerationProgressUpdate(GenerationProgressStep.EXTRACTING_ARCHIVE, message);
    }

    public static GenerationProgressUpdate finalizing(String message) {
      return new GenerationProgressUpdate(GenerationProgressStep.FINALIZING, message);
    }
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
    if (event.isDeleteBackward() || event.isKey(dev.tamboui.tui.event.KeyCode.BACKSPACE)) {
      state.deleteBackward();
      return true;
    }
    if (event.isDeleteForward() || event.isKey(dev.tamboui.tui.event.KeyCode.DELETE)) {
      state.deleteForward();
      return true;
    }
    if (event.isLeft() || event.isKey(dev.tamboui.tui.event.KeyCode.LEFT)) {
      state.moveCursorLeft();
      return true;
    }
    if (event.isRight() || event.isKey(dev.tamboui.tui.event.KeyCode.RIGHT)) {
      state.moveCursorRight();
      return true;
    }
    if (event.isHome() || event.isKey(dev.tamboui.tui.event.KeyCode.HOME)) {
      state.moveCursorToStart();
      return true;
    }
    if (event.isEnd() || event.isKey(dev.tamboui.tui.event.KeyCode.END)) {
      state.moveCursorToEnd();
      return true;
    }
    if (event.code() == dev.tamboui.tui.event.KeyCode.CHAR && !event.hasCtrl() && !event.hasAlt()) {
      state.insert(event.character());
      return true;
    }
    return false;
  }

  public record UiAction(boolean handled, boolean shouldQuit) {
    static UiAction ignored() {
      return new UiAction(false, false);
    }

    static UiAction handled(boolean shouldQuit) {
      return new UiAction(true, shouldQuit);
    }
  }

  public enum PostGenerationExitAction {
    OPEN_IDE,
    OPEN_TERMINAL,
    QUIT,
    GENERATE_AGAIN
  }

  public record PostGenerationExitPlan(
      PostGenerationExitAction action, Path projectDirectory, String nextCommand) {
    public PostGenerationExitPlan {
      action = Objects.requireNonNull(action);
      nextCommand = nextCommand == null ? "" : nextCommand.strip();
    }
  }

  private record CommandPaletteEntry(String label, String shortcut, CommandPaletteAction action) {
    CommandPaletteEntry {
      label = Objects.requireNonNull(label);
      shortcut = Objects.requireNonNull(shortcut);
      action = Objects.requireNonNull(action);
    }
  }

  private enum CommandPaletteAction {
    FOCUS_EXTENSION_SEARCH,
    FOCUS_EXTENSION_LIST,
    TOGGLE_FAVORITES_FILTER,
    JUMP_TO_FAVORITE,
    CYCLE_CATEGORY_FILTER,
    TOGGLE_CATEGORY,
    OPEN_ALL_CATEGORIES,
    RELOAD_CATALOG,
    TOGGLE_ERROR_DETAILS
  }
}
