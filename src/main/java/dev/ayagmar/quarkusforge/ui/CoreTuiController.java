package dev.ayagmar.quarkusforge.ui;

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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class CoreTuiController {
  private static final int LIST_SHORT_NAME_MAX_LENGTH = 36;
  private static final List<FocusTarget> FOCUS_ORDER =
      List.of(
          FocusTarget.GROUP_ID,
          FocusTarget.ARTIFACT_ID,
          FocusTarget.VERSION,
          FocusTarget.PACKAGE_NAME,
          FocusTarget.OUTPUT_DIR,
          FocusTarget.BUILD_TOOL,
          FocusTarget.JAVA_VERSION,
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

  private CoreTuiController(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner) {
    Objects.requireNonNull(initialState);
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(debounceDelay);
    Objects.requireNonNull(projectGenerationRunner);
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

    for (FocusTarget target : FocusTarget.values()) {
      inputStates.put(target, new TextInputState(""));
    }
    inputStates.get(FocusTarget.GROUP_ID).setText(request.groupId());
    inputStates.get(FocusTarget.ARTIFACT_ID).setText(request.artifactId());
    inputStates.get(FocusTarget.VERSION).setText(request.version());
    inputStates.get(FocusTarget.PACKAGE_NAME).setText(request.packageName());
    inputStates.get(FocusTarget.OUTPUT_DIR).setText(request.outputDirectory());
    inputStates.get(FocusTarget.BUILD_TOOL).setText(request.buildTool());
    inputStates.get(FocusTarget.JAVA_VERSION).setText(request.javaVersion());
    for (FocusTarget target : FOCUS_ORDER) {
      if (isTextInputFocus(target)) {
        inputStates.get(target).moveCursorToEnd();
      }
    }
    extensionCatalogState =
        new ExtensionCatalogState(
            scheduler, debounceDelay, inputStates.get(FocusTarget.EXTENSION_SEARCH).text());

    revalidate();
  }

  public static CoreTuiController from(ForgeUiState initialState) {
    return from(
        initialState, UiScheduler.immediate(), Duration.ZERO, NOOP_PROJECT_GENERATION_RUNNER);
  }

  public static CoreTuiController from(
      ForgeUiState initialState, UiScheduler scheduler, Duration debounceDelay) {
    return from(initialState, scheduler, debounceDelay, NOOP_PROJECT_GENERATION_RUNNER);
  }

  public static CoreTuiController from(
      ForgeUiState initialState,
      UiScheduler scheduler,
      Duration debounceDelay,
      ProjectGenerationRunner projectGenerationRunner) {
    return new CoreTuiController(initialState, scheduler, debounceDelay, projectGenerationRunner);
  }

  public void loadExtensionCatalogAsync(ExtensionCatalogLoader loader) {
    Objects.requireNonNull(loader);
    extensionCatalogLoader = loader;
    asyncOperationsCancelled = false;
    long loadToken = ++extensionCatalogLoadToken;
    extensionCatalogLoading = true;
    extensionCatalogErrorMessage = "";
    extensionCatalogSource = "live";
    extensionCatalogStale = false;
    statusMessage = "Loading extension catalog...";

    loader
        .load()
        .whenComplete(
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
                      extensionCatalogSource = "snapshot";
                      extensionCatalogStale = false;
                      errorMessage = message;
                      statusMessage = "Using fallback extension catalog";
                      return;
                    }

                    if (result == null) {
                      extensionCatalogLoading = false;
                      extensionCatalogErrorMessage = "Catalog load failed: empty load result";
                      extensionCatalogSource = "snapshot";
                      extensionCatalogStale = false;
                      errorMessage = "Catalog load failed: empty load result";
                      statusMessage = "Using fallback extension catalog";
                      return;
                    }

                    List<ExtensionCatalogItem> items =
                        result.extensions().stream()
                            .map(
                                extension ->
                                    new ExtensionCatalogItem(
                                        extension.id(), extension.name(), extension.shortName()))
                            .toList();
                    if (items.isEmpty()) {
                      extensionCatalogLoading = false;
                      extensionCatalogErrorMessage = "Catalog load returned no extensions";
                      extensionCatalogSource = "snapshot";
                      extensionCatalogStale = false;
                      errorMessage = "Catalog load returned no extensions";
                      statusMessage = "Using fallback extension catalog";
                      return;
                    }

                    if (result.metadata() != null) {
                      metadataCompatibility =
                          MetadataCompatibilityContext.success(result.metadata());
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
    if (shouldFocusExtensionSearch(keyEvent)) {
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
        && keyEvent.isUp()
        && extensionCatalogState.isSelectionAtTop()) {
      focusExtensionSearch();
      return UiAction.handled(false);
    }

    if (focusTarget == FocusTarget.EXTENSION_LIST
        && extensionCatalogState.handleListKeys(
            keyEvent,
            toggledShortName -> statusMessage = "Toggled extension: " + toggledShortName)) {
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
    Rect area = frame.area();
    int footerHeight = 6;
    if (!errorMessage.isBlank()) {
      footerHeight += 1;
    }
    footerHeight += 1;
    if (!successHint.isBlank()) {
      footerHeight += 1;
    }
    List<Rect> rootLayout =
        Layout.vertical()
            .constraints(Constraint.length(3), Constraint.fill(), Constraint.length(footerHeight))
            .split(area);

    renderHeader(frame, rootLayout.get(0));
    renderBody(frame, rootLayout.get(1));
    renderFooter(frame, rootLayout.get(2));
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
    for (int i = 0; i < 7; i++) {
      constraints.add(Constraint.length(3));
    }
    constraints.add(Constraint.fill());
    List<Rect> rows = Layout.vertical().constraints(constraints).split(inner);

    renderInput(frame, rows.get(0), "Group Id", FocusTarget.GROUP_ID);
    renderInput(frame, rows.get(1), "Artifact Id", FocusTarget.ARTIFACT_ID);
    renderInput(frame, rows.get(2), "Version", FocusTarget.VERSION);
    renderInput(frame, rows.get(3), "Package Name", FocusTarget.PACKAGE_NAME);
    renderInput(frame, rows.get(4), "Output Dir", FocusTarget.OUTPUT_DIR);
    renderInput(frame, rows.get(5), "Build Tool", FocusTarget.BUILD_TOOL);
    renderInput(frame, rows.get(6), "Java Version", FocusTarget.JAVA_VERSION);
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
    List<ExtensionCatalogItem> filteredExtensions = extensionCatalogState.filteredExtensions();
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
    for (ExtensionCatalogItem extension : filteredExtensions) {
      boolean selected = extensionCatalogState.isSelected(extension.id());
      String prefix = selected ? "[x] " : "[ ] ";
      String displayLabel = extensionDisplayLabel(extension);
      items.add(ListItem.from(prefix + displayLabel).toSizedWidget());
    }

    if (items.isEmpty()) {
      String emptyMessage =
          extensionError
              ? "Catalog unavailable - using fallback snapshot"
              : "No extension matches current filter";
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

  private void renderFooter(Frame frame, Rect area) {
    String footerText =
        footerHintLine(area.width())
            + "\n"
            + "Mode: "
            + footerModeLabel()
            + "\n"
            + "Status: "
            + statusMessage
            + "\n"
            + "Validation: "
            + validationLabel()
            + " | Focus: "
            + focusTargetName(focusTarget);
    footerText += "\nGeneration: " + generationStateLabel();
    if (!errorMessage.isBlank()) {
      footerText += "\nError: " + errorMessage;
    }
    if (!successHint.isBlank()) {
      footerText += "\nNext: " + truncate(successHint, Math.max(24, area.width() - 16));
    }

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
    if (!isTextInputFocus(target) || target == FocusTarget.EXTENSION_SEARCH) {
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
          ? "Up/Down: nav | Space: toggle | Ctrl+R: reload"
          : "Up/Down: list nav | Space: toggle extension | Ctrl+R: reload | Enter: submit | /: search";
    }
    if (focusTarget == FocusTarget.EXTENSION_SEARCH) {
      return width < NARROW_WIDTH_THRESHOLD
          ? "Type: filter | Down: list | Enter: submit"
          : "Type: filter extensions | Down: list | Ctrl+R: reload | Enter: submit | Esc: cancel/quit";
    }
    return width < NARROW_WIDTH_THRESHOLD
        ? "Tab: focus | Enter: submit | Esc: quit"
        : "Tab/Shift+Tab: focus | Enter: submit | /: search | Esc: cancel/quit";
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

  private void scheduleFilteredExtensionsRefresh() {
    String query = inputStates.get(FocusTarget.EXTENSION_SEARCH).text();
    statusMessage = "Searching extensions...";
    extensionCatalogState.scheduleRefresh(query, this::updateExtensionFilterStatus);
  }

  private void rebuildRequestFromInputs() {
    CliPrefill prefill =
        new CliPrefill(
            inputStates.get(FocusTarget.GROUP_ID).text(),
            inputStates.get(FocusTarget.ARTIFACT_ID).text(),
            inputStates.get(FocusTarget.VERSION).text(),
            inputStates.get(FocusTarget.PACKAGE_NAME).text(),
            inputStates.get(FocusTarget.OUTPUT_DIR).text(),
            inputStates.get(FocusTarget.BUILD_TOOL).text(),
            inputStates.get(FocusTarget.JAVA_VERSION).text());
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

    generationFuture =
        projectGenerationRunner.generate(
            generationRequest,
            outputDirectory,
            () -> generationCancelRequested || token != generationToken,
            progressMessage ->
                scheduleOnRenderThread(() -> onGenerationProgress(token, progressMessage)));

    generationFuture.whenComplete(
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
    if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
      return throwable.getMessage();
    }
    return throwable.getClass().getSimpleName();
  }

  private static String catalogLoadFailureMessage(Throwable throwable) {
    String message = userFriendlyError(throwable);
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
    return focusTarget != FocusTarget.EXTENSION_LIST && focusTarget != FocusTarget.SUBMIT;
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
      case BUILD_TOOL -> "buildTool";
      case JAVA_VERSION -> "javaVersion";
      case EXTENSION_SEARCH -> "extensionSearch";
      case EXTENSION_LIST -> "extensionList";
      case SUBMIT -> "submit";
    };
  }

  private static String extensionDisplayLabel(ExtensionCatalogItem extension) {
    if (extension.shortName().equalsIgnoreCase(extension.name())) {
      return extension.name();
    }
    return extension.name()
        + " ("
        + truncate(extension.shortName(), LIST_SHORT_NAME_MAX_LENGTH)
        + ")";
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

  private static boolean shouldQuitKeyEvent(KeyEvent keyEvent) {
    return keyEvent.isCancel() || keyEvent.isCtrlC();
  }

  private static boolean shouldFocusExtensionSearch(KeyEvent keyEvent) {
    if (keyEvent.code() != dev.tamboui.tui.event.KeyCode.CHAR) {
      return false;
    }
    if (!keyEvent.hasCtrl() && !keyEvent.hasAlt() && keyEvent.character() == '/') {
      return true;
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
