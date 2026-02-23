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
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.common.SizedWidget;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CoreTuiController {
  private static final List<FocusTarget> FOCUS_ORDER =
      List.of(
          FocusTarget.GROUP_ID,
          FocusTarget.ARTIFACT_ID,
          FocusTarget.VERSION,
          FocusTarget.PLATFORM_STREAM,
          FocusTarget.BUILD_TOOL,
          FocusTarget.JAVA_VERSION,
          FocusTarget.PACKAGE_NAME,
          FocusTarget.OUTPUT_DIR,
          FocusTarget.EXTENSION_SEARCH,
          FocusTarget.EXTENSION_LIST,
          FocusTarget.SUBMIT);

  private static final int NARROW_WIDTH_THRESHOLD = 100;
  private static final ProjectGenerationRunner NOOP_PROJECT_GENERATION_RUNNER =
      (generationRequest, outputDirectory, cancelled, progressListener) ->
          CompletableFuture.failedFuture(
              new IllegalStateException("Generation flow is not configured in this runtime"));
  private final EnumMap<FocusTarget, TextInputState> inputStates = new EnumMap<>(FocusTarget.class);
  private final ProjectRequestValidator requestValidator = new ProjectRequestValidator();
  private MetadataCompatibilityContext metadataCompatibility;
  private final UiScheduler scheduler;
  private final ExtensionCatalogState extensionCatalogState;
  private final ProjectGenerationRunner projectGenerationRunner;
  private final UiTheme theme;
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
  private GenerationState generationState;
  private CompletableFuture<Path> generationFuture;
  private volatile boolean generationCancelRequested;
  private volatile long generationToken;
  private volatile long extensionCatalogLoadToken;
  private volatile boolean asyncOperationsCancelled;
  private boolean extensionCatalogLoading;
  private String extensionCatalogErrorMessage;
  private String extensionCatalogSource;
  private boolean extensionCatalogStale;
  private ExtensionCatalogLoader extensionCatalogLoader;
  private String successHint;
  private boolean showErrorDetails;

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
    focusTarget = FocusTarget.GROUP_ID;
    statusMessage = "Ready";
    errorMessage = "";
    submitRequested = false;
    submitBlockedByValidation = false;
    metadataCompatibility = initialState.metadataCompatibility();
    this.scheduler = scheduler;
    this.projectGenerationRunner = projectGenerationRunner;
    theme = UiTheme.loadDefault();
    generationState = GenerationState.IDLE;
    generationFuture = null;
    generationCancelRequested = false;
    generationToken = 0L;
    extensionCatalogLoadToken = 0L;
    asyncOperationsCancelled = false;
    extensionCatalogLoading = false;
    extensionCatalogErrorMessage = "";
    extensionCatalogSource = "snapshot";
    extensionCatalogStale = false;
    extensionCatalogLoader = null;
    successHint = "";
    showErrorDetails = false;
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
    asyncOperationsCancelled = false;
    long loadToken = ++extensionCatalogLoadToken;
    extensionCatalogLoading = true;
    extensionCatalogErrorMessage = "";
    statusMessage = "Loading extension catalog...";

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
          if (asyncOperationsCancelled || loadToken != extensionCatalogLoadToken) {
            return;
          }
          scheduleOnRenderThread(
              () -> {
                if (asyncOperationsCancelled || loadToken != extensionCatalogLoadToken) {
                  return;
                }
                if (throwable != null) {
                  Throwable cause = unwrapCompletionCause(throwable);
                  String message = catalogLoadFailureMessage(cause);
                  extensionCatalogLoading = false;
                  extensionCatalogErrorMessage = message;
                  errorMessage = message;
                  statusMessage = catalogReloadFailureStatusMessage();
                  return;
                }

                if (result == null) {
                  extensionCatalogLoading = false;
                  extensionCatalogErrorMessage = "Catalog load failed: empty load result";
                  errorMessage = "Catalog load failed: empty load result";
                  statusMessage = catalogReloadFailureStatusMessage();
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
                  extensionCatalogLoading = false;
                  extensionCatalogErrorMessage = "Catalog load returned no extensions";
                  errorMessage = "Catalog load returned no extensions";
                  statusMessage = catalogReloadFailureStatusMessage();
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
                if (result.source() == CatalogSource.LIVE) {
                  extensionCatalogErrorMessage = "";
                  statusMessage =
                      result.detailMessage().isBlank()
                          ? catalogLoadedStatusMessage(result.source(), result.stale())
                          : result.detailMessage();
                } else {
                  extensionCatalogErrorMessage = result.detailMessage();
                  statusMessage = catalogLoadedStatusMessage(result.source(), result.stale());
                }
                extensionCatalogState.replaceCatalog(
                    items, inputStates.get(FocusTarget.EXTENSION_SEARCH).text(), ignored -> {});
              });
        });
  }

  public UiAction onEvent(Event event) {
    reconcileGenerationCompletionIfDone();
    if (event instanceof ResizeEvent resizeEvent) {
      statusMessage = "Terminal resized to " + resizeEvent.width() + "x" + resizeEvent.height();
      return UiAction.handled(false);
    }
    if (!(event instanceof KeyEvent keyEvent)) {
      return UiAction.ignored();
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

    if (keyEvent.isConfirm()) {
      submitRequested = true;
      resetGenerationStateAfterTerminalOutcome();
      if (!transitionGenerationState(GenerationState.VALIDATING)) {
        statusMessage = "Submit ignored in state: " + generationStateLabel();
        return UiAction.handled(false);
      }
      if (!validation.isValid()) {
        submitBlockedByValidation = true;
        errorMessage = firstValidationError(validation);
        statusMessage = "Submit blocked: invalid input";
        transitionGenerationState(GenerationState.ERROR);
      } else {
        submitBlockedByValidation = false;
        errorMessage = "";
        if (projectGenerationRunner == NOOP_PROJECT_GENERATION_RUNNER) {
          statusMessage =
              "Submit requested with "
                  + extensionCatalogState.selectedExtensionCount()
                  + " extension(s), but generation service is not configured.";
          transitionGenerationState(GenerationState.IDLE);
        } else {
          startGenerationFlow();
        }
      }
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
    if (focusTarget == FocusTarget.EXTENSION_LIST && isFavoriteToggleKey(keyEvent)) {
      toggleFavoriteAtSelection();
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

  public void render(Frame frame) {
    reconcileGenerationCompletionIfDone();
    Rect area = frame.area();
    List<String> footerLines = footerLinesForWidth(area.width());
    int footerHeight = Math.max(3, footerLines.size() + 2);
    List<Rect> rootLayout =
        Layout.vertical()
            .constraints(Constraint.length(3), Constraint.fill(), Constraint.length(footerHeight))
            .split(area);

    renderHeader(frame, rootLayout.get(0));
    renderBody(frame, rootLayout.get(1));
    renderFooter(frame, rootLayout.get(2), footerLines);
  }

  FocusTarget focusTarget() {
    return focusTarget;
  }

  ValidationReport validation() {
    return validation;
  }

  ProjectRequest request() {
    return request;
  }

  List<String> selectedExtensionIds() {
    return extensionCatalogState.selectedExtensionIds();
  }

  String statusMessage() {
    return statusMessage;
  }

  boolean submitRequested() {
    return submitRequested;
  }

  GenerationState generationState() {
    return generationState;
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
    Block block =
        Block.builder()
            .title("Quarkus Forge")
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(accentBorderStyle())
            .build();
    String text = "Keyboard-first Quarkus project generator";
    Paragraph header =
        Paragraph.builder()
            .text(text)
            .style(Style.EMPTY.fg(theme.color("text")))
            .overflow(Overflow.ELLIPSIS)
            .block(block)
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

    renderMetadataPanel(frame, bodyLayout.get(0));
    renderExtensionsPanel(frame, bodyLayout.get(1));
  }

  private void renderMetadataPanel(Frame frame, Rect area) {
    boolean metadataInvalid = !validation.isValid();
    Block panelBlock =
        Block.builder()
            .title(panelTitle(metadataPanelTitle(), isMetadataFocused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(panelBorderStyle(isMetadataFocused(), metadataInvalid, false))
            .build();
    frame.renderWidget(panelBlock, area);

    Rect inner = panelBlock.inner(area);
    if (inner.isEmpty()) {
      return;
    }

    List<Constraint> constraints = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      constraints.add(Constraint.length(3));
    }
    constraints.add(Constraint.fill());
    List<Rect> rows = Layout.vertical().constraints(constraints).split(inner);

    renderInput(frame, rows.get(0), "Group Id", FocusTarget.GROUP_ID);
    renderInput(frame, rows.get(1), "Artifact Id", FocusTarget.ARTIFACT_ID);
    renderInput(frame, rows.get(2), "Version", FocusTarget.VERSION);
    renderInput(frame, rows.get(3), "Quarkus Platform", FocusTarget.PLATFORM_STREAM);
    renderInput(frame, rows.get(4), "Build Tool", FocusTarget.BUILD_TOOL);
    renderInput(frame, rows.get(5), "Java Version", FocusTarget.JAVA_VERSION);
    renderInput(frame, rows.get(6), "Package Name", FocusTarget.PACKAGE_NAME);
    renderInput(frame, rows.get(7), "Output Dir", FocusTarget.OUTPUT_DIR);
  }

  private void renderExtensionsPanel(Frame frame, Rect area) {
    boolean extensionError = !extensionCatalogErrorMessage.isBlank();
    Block panelBlock =
        Block.builder()
            .title(panelTitle(extensionsPanelTitle(), isExtensionPanelFocused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(
                panelBorderStyle(
                    isExtensionPanelFocused(), extensionError, extensionCatalogLoading))
            .build();
    frame.renderWidget(panelBlock, area);

    Rect inner = panelBlock.inner(area);
    if (inner.isEmpty()) {
      return;
    }

    List<Rect> sections =
        Layout.vertical()
            .constraints(Constraint.length(3), Constraint.fill(), Constraint.length(4))
            .split(inner);

    renderInput(frame, sections.get(0), "Search Extensions", FocusTarget.EXTENSION_SEARCH);
    renderExtensionList(frame, sections.get(1));
    renderSelectedSummary(frame, sections.get(2));
  }

  private void renderExtensionList(Frame frame, Rect area) {
    List<ExtensionCatalogRow> filteredRows = extensionCatalogState.filteredRows();
    boolean extensionError = !extensionCatalogErrorMessage.isBlank();
    Block listBlock =
        Block.builder()
            .title(panelTitle("Catalog", focusTarget == FocusTarget.EXTENSION_LIST))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(
                panelBorderStyle(
                    focusTarget == FocusTarget.EXTENSION_LIST,
                    extensionError,
                    extensionCatalogLoading))
            .build();

    if (extensionCatalogLoading) {
      Paragraph loading =
          Paragraph.builder()
              .text("Loading extension catalog...")
              .block(listBlock)
              .style(Style.EMPTY.fg(theme.color("warning")).bold())
              .overflow(Overflow.ELLIPSIS)
              .build();
      frame.renderWidget(loading, area);
      return;
    }

    List<SizedWidget> items = new ArrayList<>();
    for (ExtensionCatalogRow row : filteredRows) {
      if (row.isSectionHeader()) {
        items.add(ListItem.from(sectionHeaderLabel(row)).toSizedWidget());
        continue;
      }
      ExtensionCatalogItem extension = row.extension();
      boolean selected = extensionCatalogState.isSelected(extension.id());
      boolean favorite = extensionCatalogState.isFavorite(extension.id());
      String checkedPrefix = selected ? "[x] " : "[ ] ";
      String favoritePrefix = favorite ? "* " : "  ";
      String displayLabel = extensionDisplayLabel(extension);
      items.add(ListItem.from(checkedPrefix + favoritePrefix + displayLabel).toSizedWidget());
    }

    if (items.isEmpty()) {
      String emptyMessage;
      if (extensionError) {
        emptyMessage = "Catalog unavailable - using fallback snapshot";
      } else if (extensionCatalogState.favoritesOnlyFilterEnabled()) {
        emptyMessage = "No favorite extension matches current filter";
      } else {
        emptyMessage = "No extension matches current filter";
      }
      Paragraph empty =
          Paragraph.builder()
              .text(emptyMessage)
              .block(listBlock)
              .style(Style.EMPTY.fg(extensionError ? theme.color("error") : theme.color("warning")))
              .overflow(Overflow.ELLIPSIS)
              .build();
      frame.renderWidget(empty, area);
      return;
    }

    ListWidget listWidget =
        ListWidget.builder()
            .items(items)
            .highlightSymbol("> ")
            .style(Style.EMPTY.fg(theme.color("text")))
            .highlightStyle(Style.EMPTY.fg(theme.color("focus")).reversed().bold())
            .scrollMode(ScrollMode.AUTO_SCROLL)
            .scrollBarPolicy(ScrollBarPolicy.AS_NEEDED)
            .scrollbarThumbStyle(Style.EMPTY.fg(theme.color("focus")).bold())
            .scrollbarTrackStyle(Style.EMPTY.fg(theme.color("muted")))
            .block(listBlock)
            .build();
    frame.renderStatefulWidget(listWidget, area, extensionCatalogState.listState());
  }

  private void renderSelectedSummary(Frame frame, Rect area) {
    List<String> selectedExtensionIds = extensionCatalogState.selectedExtensionIds();
    String summary = selectedExtensionsSummary(selectedExtensionIds, area.width());
    summary += "\nCatalog: " + extensionCatalogSource;
    if (extensionCatalogStale) {
      summary += " [stale]";
    }
    if (!extensionCatalogErrorMessage.isBlank()) {
      summary += " | error: " + extensionCatalogErrorMessage;
    }
    summary += "\nFavorites: " + extensionCatalogState.favoriteExtensionCount();
    if (extensionCatalogState.favoritesOnlyFilterEnabled()) {
      summary += " [filter:on]";
    }
    if (focusTarget == FocusTarget.SUBMIT) {
      summary += "\nSubmit focus active - press Enter to submit";
    }

    Style summaryStyle = Style.EMPTY.fg(theme.color("text"));
    if (!extensionCatalogErrorMessage.isBlank()) {
      summaryStyle = summaryStyle.fg(theme.color("warning"));
    }

    Paragraph paragraph =
        Paragraph.builder()
            .text(summary)
            .style(summaryStyle)
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title(panelTitle("Selection", focusTarget == FocusTarget.SUBMIT))
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(panelBorderStyle(focusTarget == FocusTarget.SUBMIT, false, false))
                    .build())
            .build();
    frame.renderWidget(paragraph, area);
  }

  private void renderInput(Frame frame, Rect area, String label, FocusTarget target) {
    boolean focused = focusTarget == target;
    Block inputBlock =
        Block.builder()
            .title(panelTitle(label, focused))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(inputBorderStyle(target, focused))
            .build();

    TextInput input =
        TextInput.builder()
            .placeholder(label)
            .style(Style.EMPTY.fg(theme.color("text")))
            .block(inputBlock)
            .placeholderStyle(Style.EMPTY.fg(theme.color("muted")))
            .build();
    frame.renderStatefulWidget(input, area, inputStates.get(target));
  }

  private void renderFooter(Frame frame, Rect area, List<String> footerLines) {
    String footerText = String.join("\n", footerLines);

    Paragraph footer =
        Paragraph.builder()
            .text(footerText)
            .style(Style.EMPTY.fg(theme.color("text")))
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title("Status")
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(accentBorderStyle())
                    .build())
            .build();
    frame.renderWidget(footer, area);
  }

  private List<String> footerLinesForWidth(int width) {
    List<String> lines = new ArrayList<>();
    lines.add(footerHintLine(width));
    lines.add("Mode: " + footerModeLabel() + " | Generation: " + generationStateLabel());
    lines.add("Status: " + statusMessage);
    lines.add("Validation: " + validationLabel() + " | Focus: " + focusTargetName(focusTarget));

    String activeError = activeErrorDetails();
    if (!activeError.isBlank()) {
      lines.add("Error: " + activeError);
    }

    int expandedErrorLines = expandedErrorDetailLines(activeError, width);
    if (expandedErrorLines > 0) {
      lines.add("Error details:");
      lines.addAll(wrapToWidth(activeError, Math.max(24, width - 6), expandedErrorLines));
    }

    if (!successHint.isBlank()) {
      lines.add("Next: " + truncate(successHint, Math.max(24, width - 16)));
    }
    return lines;
  }

  private void moveFocus(int offset) {
    int index = FOCUS_ORDER.indexOf(focusTarget);
    int size = FOCUS_ORDER.size();
    int nextIndex = Math.floorMod(index + offset, size);
    focusTarget = FOCUS_ORDER.get(nextIndex);
    statusMessage = "Focus moved to " + focusTargetName(focusTarget);
    errorMessage = "";
    submitBlockedByValidation = false;
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

  private Style accentBorderStyle() {
    return Style.EMPTY.fg(theme.color("accent")).bold();
  }

  private Style inputBorderStyle(FocusTarget target, boolean focused) {
    boolean invalidField = hasValidationErrorFor(target);
    if (invalidField) {
      return Style.EMPTY.fg(theme.color("error")).bold();
    }
    if (focused) {
      return Style.EMPTY.fg(theme.color("focus")).bold();
    }
    return Style.EMPTY.fg(theme.color("muted"));
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

  private static String selectedExtensionsSummary(List<String> selectedExtensionIds, int width) {
    if (selectedExtensionIds.isEmpty()) {
      return "Selected: none";
    }
    int maxVisible = width < NARROW_WIDTH_THRESHOLD ? 1 : 3;
    int visibleCount = Math.min(maxVisible, selectedExtensionIds.size());
    String visible = String.join(", ", selectedExtensionIds.subList(0, visibleCount));
    if (selectedExtensionIds.size() > visibleCount) {
      int remaining = selectedExtensionIds.size() - visibleCount;
      return "Selected ("
          + selectedExtensionIds.size()
          + "): "
          + visible
          + " +"
          + remaining
          + " more";
    }
    return "Selected (" + selectedExtensionIds.size() + "): " + visible;
  }

  private String footerHintLine(int width) {
    if (isGenerationInProgress()) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Esc: cancel generation | Enter disabled"
          : "Esc: cancel generation | Enter disabled while generation is loading";
    }
    if (focusTarget == FocusTarget.EXTENSION_LIST) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Up/Down or j/k: nav | Space: select | F: favorite | c: category"
          : "Up/Down/Home/End or j/k: list nav | Space: select | F: favorite | c: close/open category | C: open all | Ctrl+J: jump favorite | Ctrl+K: favorite filter | Ctrl+R: reload | Ctrl+E: error details";
    }
    if (focusTarget == FocusTarget.EXTENSION_SEARCH) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Type: filter | Down: list | Ctrl+K: fav filter"
          : "Type: filter extensions | Down: list | Ctrl+R: reload | Ctrl+J: jump favorite | Ctrl+K: favorite filter | Ctrl+E: error details";
    }
    if (focusTarget == FocusTarget.SUBMIT) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Enter: submit | j/k: focus | Ctrl+E: error details"
          : "Enter: submit | Tab/Shift+Tab or j/k: focus | Ctrl+E: error details | Esc: cancel/quit";
    }
    if (isMetadataSelectorFocus(focusTarget)) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Left/Right or h/l: pick | Up/Down or j/k: cycle"
          : "Left/Right/Home/End or h/l/j/k: pick value | Tab/Shift+Tab: focus | Enter: submit | Ctrl+E: error details";
    }
    return width < NARROW_WIDTH_THRESHOLD
        ? "Tab: focus | Enter: submit | Ctrl+E: error details"
        : "Tab/Shift+Tab: focus | Enter: submit | /: search | Ctrl+K: favorite filter | Ctrl+E: error details | Esc: cancel/quit";
  }

  private String footerModeLabel() {
    return switch (generationState) {
      case IDLE -> "ready";
      case VALIDATING -> "validating input";
      case LOADING -> "generation loading";
      case SUCCESS -> "last run succeeded";
      case ERROR -> "last run failed";
      case CANCELLED -> "last run cancelled";
    };
  }

  private String validationLabel() {
    if (validation.isValid()) {
      return "OK";
    }
    return "INVALID - " + firstValidationError(validation);
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

  private int expandedErrorDetailLines(String activeError, int width) {
    if (!showErrorDetails || activeError.isBlank()) {
      return 0;
    }
    return wrapToWidth(activeError, Math.max(24, width - 6), 6).size();
  }

  private static List<String> wrapToWidth(String text, int width, int maxLines) {
    List<String> lines = new ArrayList<>();
    if (text == null || text.isBlank() || width <= 0 || maxLines <= 0) {
      return lines;
    }

    String remaining = text.strip();
    while (!remaining.isBlank() && lines.size() < maxLines) {
      if (remaining.length() <= width) {
        lines.add(remaining);
        break;
      }

      int breakIndex = remaining.lastIndexOf(' ', width);
      if (breakIndex <= 0) {
        breakIndex = width;
      }
      lines.add(remaining.substring(0, breakIndex).stripTrailing());
      remaining = remaining.substring(Math.min(remaining.length(), breakIndex + 1)).stripLeading();
    }

    if (!remaining.isBlank() && lines.size() == maxLines) {
      int lastIndex = lines.size() - 1;
      lines.set(lastIndex, truncate(lines.get(lastIndex), Math.max(4, width)));
    }
    return lines;
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
    asyncOperationsCancelled = true;
    extensionCatalogLoading = false;
    extensionCatalogState.cancelPendingAsync();
  }

  private void startGenerationFlow() {
    successHint = "";
    extensionCatalogState.cancelPendingAsync();
    generationCancelRequested = false;
    long token = ++generationToken;
    if (!transitionGenerationState(GenerationState.LOADING)) {
      statusMessage = "Submit ignored in state: " + generationStateLabel();
      return;
    }
    statusMessage = "Generation in progress: preparing request...";

    GenerationRequest generationRequest = toGenerationRequest();
    Path outputDirectory = resolveGeneratedProjectDirectory();

    CompletableFuture<Path> startedFuture;
    try {
      startedFuture =
          projectGenerationRunner.generate(
              generationRequest,
              outputDirectory,
              () -> generationCancelRequested || token != generationToken,
              progressMessage ->
                  scheduleOnRenderThread(() -> onGenerationProgress(token, progressMessage)));
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

  private void onGenerationProgress(long token, String progressMessage) {
    if (token != generationToken || generationState != GenerationState.LOADING) {
      return;
    }
    statusMessage = "Generation in progress: " + progressMessage;
  }

  private void onGenerationCompleted(long token, Path generatedPath, Throwable throwable) {
    if (token != generationToken || generationState != GenerationState.LOADING) {
      return;
    }
    generationFuture = null;

    Throwable cause = unwrapCompletionCause(throwable);
    if (cause == null && generatedPath != null) {
      transitionGenerationState(GenerationState.SUCCESS);
      Path normalizedPath = generatedPath.toAbsolutePath().normalize();
      statusMessage = "Generation succeeded: " + normalizedPath;
      errorMessage = "";
      successHint = "cd " + normalizedPath + " && " + nextStepCommand(request.buildTool());
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
      return;
    }

    transitionGenerationState(GenerationState.ERROR);
    statusMessage = "Generation failed.";
    errorMessage = userFriendlyError(cause);
    successHint = "";
  }

  private void reconcileGenerationCompletionIfDone() {
    if (generationState != GenerationState.LOADING || generationFuture == null) {
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
    if (generationState != GenerationState.LOADING) {
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
    return generationState == GenerationState.LOADING;
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
    if (generationState == GenerationState.SUCCESS
        || generationState == GenerationState.ERROR
        || generationState == GenerationState.CANCELLED) {
      transitionGenerationState(GenerationState.IDLE);
    }
  }

  private String generationStateLabel() {
    return switch (generationState) {
      case IDLE -> "idle";
      case VALIDATING -> "validating";
      case LOADING -> "loading (Esc to cancel)";
      case SUCCESS -> "success";
      case ERROR -> "failed";
      case CANCELLED -> "cancelled";
    };
  }

  private void scheduleOnRenderThread(Runnable task) {
    scheduler.schedule(Duration.ZERO, task);
  }

  private boolean transitionGenerationState(GenerationState targetState) {
    if (!isValidTransition(generationState, targetState)) {
      return false;
    }
    generationState = targetState;
    return true;
  }

  static boolean isValidTransition(GenerationState currentState, GenerationState targetState) {
    if (currentState == targetState) {
      return false;
    }
    return switch (currentState) {
      case IDLE -> targetState == GenerationState.VALIDATING;
      case VALIDATING ->
          targetState == GenerationState.LOADING
              || targetState == GenerationState.ERROR
              || targetState == GenerationState.IDLE;
      case LOADING ->
          targetState == GenerationState.SUCCESS
              || targetState == GenerationState.ERROR
              || targetState == GenerationState.CANCELLED;
      case SUCCESS, ERROR, CANCELLED -> targetState == GenerationState.IDLE;
    };
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
      case OUTPUT_DIR -> "outputDir";
      case PLATFORM_STREAM -> "platformStream";
      case BUILD_TOOL -> "buildTool";
      case JAVA_VERSION -> "javaVersion";
      case EXTENSION_SEARCH -> "extensionSearch";
      case EXTENSION_LIST -> "extensionList";
      case SUBMIT -> "submit";
    };
  }

  private static String extensionDisplayLabel(ExtensionCatalogItem extension) {
    return extension.name();
  }

  private static String sectionHeaderLabel(ExtensionCatalogRow row) {
    String prefix = row.collapsed() ? "[+]" : "[-]";
    String suffix = row.collapsed() ? " (" + row.hiddenCount() + " hidden)" : "";
    return "-- " + prefix + " " + row.label() + suffix + " --";
  }

  private static String truncate(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength - 3) + "...";
  }

  private static String firstValidationError(ValidationReport report) {
    return report.errors().isEmpty()
        ? ""
        : report.errors().getFirst().field() + ": " + report.errors().getFirst().message();
  }

  private void updateExtensionFilterStatus(int filteredCount) {
    if (asyncOperationsCancelled || isGenerationInProgress()) {
      return;
    }
    statusMessage = "Extensions filtered: " + filteredCount;
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

  private static boolean isFavoriteToggleKey(KeyEvent keyEvent) {
    return keyEvent.code() == dev.tamboui.tui.event.KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == 'f' || keyEvent.character() == 'F');
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

  private static boolean shouldQuitKeyEvent(KeyEvent keyEvent) {
    return keyEvent.isCancel() || keyEvent.isCtrlC();
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

  private static boolean shouldFocusExtensionSearch(KeyEvent keyEvent, FocusTarget currentFocus) {
    if (keyEvent.code() != dev.tamboui.tui.event.KeyCode.CHAR) {
      return false;
    }
    if (!keyEvent.hasCtrl() && !keyEvent.hasAlt() && keyEvent.character() == '/') {
      return currentFocus != FocusTarget.OUTPUT_DIR && currentFocus != FocusTarget.EXTENSION_SEARCH;
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
        Consumer<String> progressListener);
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
}
