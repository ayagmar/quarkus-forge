package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
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
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.ResizeEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.common.SizedWidget;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListWidget;
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
  private final MetadataCompatibilityContext metadataCompatibility;
  private final UiScheduler scheduler;
  private final ExtensionCatalogState extensionCatalogState;
  private final ProjectGenerationRunner projectGenerationRunner;

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
  private volatile boolean asyncOperationsCancelled;
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
    generationState = GenerationState.IDLE;
    generationFuture = null;
    generationCancelRequested = false;
    generationToken = 0L;
    asyncOperationsCancelled = false;
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
    asyncOperationsCancelled = false;
    statusMessage = "Loading extension catalog...";

    loader
        .load()
        .whenComplete(
            (extensions, throwable) -> {
              if (asyncOperationsCancelled) {
                return;
              }
              scheduler.schedule(
                  Duration.ZERO,
                  () -> {
                    if (asyncOperationsCancelled) {
                      return;
                    }
                    if (throwable != null) {
                      errorMessage = "Catalog load failed: " + throwable.getMessage();
                      statusMessage = "Using fallback extension catalog";
                      return;
                    }

                    List<ExtensionCatalogItem> items =
                        extensions.stream()
                            .map(
                                extension ->
                                    new ExtensionCatalogItem(
                                        extension.id(), extension.name(), extension.shortName()))
                            .toList();
                    if (items.isEmpty()) {
                      errorMessage = "Catalog load returned no extensions";
                      statusMessage = "Using fallback extension catalog";
                      return;
                    }

                    statusMessage = "Searching extensions...";
                    extensionCatalogState.replaceCatalog(
                        items,
                        inputStates.get(FocusTarget.EXTENSION_SEARCH).text(),
                        this::updateExtensionFilterStatus);
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

    if (keyEvent.isQuit() || keyEvent.isCancel()) {
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
      if (!validation.isValid()) {
        submitBlockedByValidation = true;
        errorMessage = firstValidationError(validation);
        statusMessage = "Submit blocked: invalid input";
      } else {
        submitBlockedByValidation = false;
        errorMessage = "";
        if (projectGenerationRunner == NOOP_PROJECT_GENERATION_RUNNER) {
          statusMessage =
              "Submit requested with "
                  + extensionCatalogState.selectedExtensionCount()
                  + " extension(s), but generation service is not configured.";
        } else {
          startGenerationFlow();
        }
      }
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
    int footerHeight = errorMessage.isBlank() ? 5 : 6;
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
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
            .build();
    String text = "Keyboard-first Quarkus project generator";
    Paragraph header = Paragraph.builder().text(text).block(block).build();
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
    Block panelBlock =
        Block.builder()
            .title(panelTitle("Project Metadata", isMetadataFocused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
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
    Block panelBlock =
        Block.builder()
            .title(panelTitle("Extensions", isExtensionPanelFocused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.CYAN))
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
    List<SizedWidget> items = new ArrayList<>();
    for (ExtensionCatalogItem extension : filteredExtensions) {
      boolean selected = extensionCatalogState.isSelected(extension.id());
      String prefix = selected ? "[x] " : "[ ] ";
      items.add(
          ListItem.from(prefix + extension.name() + " (" + extension.shortName() + ")")
              .toSizedWidget());
    }

    Block listBlock =
        Block.builder()
            .title(panelTitle("Catalog", focusTarget == FocusTarget.EXTENSION_LIST))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
            .build();

    if (items.isEmpty()) {
      Paragraph empty =
          Paragraph.builder()
              .text("No extension matches current filter")
              .block(listBlock)
              .style(Style.EMPTY.fg(Color.YELLOW))
              .build();
      frame.renderWidget(empty, area);
      return;
    }

    ListWidget listWidget =
        ListWidget.builder()
            .items(items)
            .highlightSymbol("> ")
            .highlightStyle(Style.EMPTY.reversed())
            .block(listBlock)
            .build();
    frame.renderStatefulWidget(listWidget, area, extensionCatalogState.listState());
  }

  private void renderSelectedSummary(Frame frame, Rect area) {
    List<String> selectedExtensionIds = extensionCatalogState.selectedExtensionIds();
    String summary;
    if (selectedExtensionIds.isEmpty()) {
      summary = "Selected: none";
    } else {
      summary =
          "Selected: "
              + selectedExtensionIds.size()
              + " -> "
              + String.join(", ", selectedExtensionIds);
    }
    if (focusTarget == FocusTarget.SUBMIT) {
      summary += "\nSubmit focus active - press Enter to submit";
    }

    Paragraph paragraph =
        Paragraph.builder()
            .text(summary)
            .block(
                Block.builder()
                    .title(panelTitle("Selection", focusTarget == FocusTarget.SUBMIT))
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
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
            .borderStyle(Style.EMPTY.fg(focused ? Color.CYAN : Color.DARK_GRAY))
            .build();

    TextInput input =
        TextInput.builder()
            .placeholder(label)
            .block(inputBlock)
            .placeholderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
            .build();
    frame.renderStatefulWidget(input, area, inputStates.get(target));
  }

  private void renderFooter(Frame frame, Rect area) {
    String footerText =
        "Tab/Shift+Tab: focus | Up/Down: list nav | Space: toggle extension | Enter: submit | Esc: cancel/quit\n"
            + "Status: "
            + statusMessage
            + "\n"
            + "Validation: "
            + (validation.isValid() ? "OK" : firstValidationError(validation))
            + " | Focus: "
            + focusTargetName(focusTarget);
    footerText += "\nGeneration: " + generationStateLabel();
    if (!errorMessage.isBlank()) {
      footerText += "\nError: " + errorMessage;
    }
    if (!successHint.isBlank()) {
      footerText += "\nNext: " + successHint;
    }

    Paragraph footer =
        Paragraph.builder()
            .text(footerText)
            .block(
                Block.builder()
                    .title("Status")
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(Color.CYAN))
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
    extensionCatalogState.cancelPendingAsync();
  }

  private void startGenerationFlow() {
    successHint = "";
    extensionCatalogState.cancelPendingAsync();
    generatedProjectCleanup();
    generationCancelRequested = false;
    long token = ++generationToken;
    generationState = GenerationState.GENERATING;
    statusMessage = "Generation in progress: preparing request...";

    GenerationRequest generationRequest = toGenerationRequest();
    Path outputDirectory = resolveGeneratedProjectDirectory();

    generationFuture =
        projectGenerationRunner.generate(
            generationRequest,
            outputDirectory,
            () -> generationCancelRequested || token != generationToken,
            progressMessage ->
                scheduler.schedule(
                    Duration.ZERO, () -> onGenerationProgress(token, progressMessage)));

    generationFuture.whenComplete(
        (generatedPath, throwable) ->
            scheduler.schedule(
                Duration.ZERO, () -> onGenerationCompleted(token, generatedPath, throwable)));
  }

  private void onGenerationProgress(long token, String progressMessage) {
    if (token != generationToken || generationState != GenerationState.GENERATING) {
      return;
    }
    statusMessage = "Generation in progress: " + progressMessage;
  }

  private void onGenerationCompleted(long token, Path generatedPath, Throwable throwable) {
    if (token != generationToken) {
      return;
    }
    generationFuture = null;

    Throwable cause = unwrapCompletionCause(throwable);
    if (cause == null && generatedPath != null) {
      generationState = GenerationState.SUCCESS;
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
      generationState = GenerationState.CANCELLED;
      statusMessage = "Generation cancelled. Update inputs and press Enter to retry.";
      errorMessage = "";
      successHint = "";
      return;
    }

    generationState = GenerationState.ERROR;
    statusMessage = "Generation failed.";
    errorMessage = userFriendlyError(cause);
    successHint = "";
  }

  private void requestGenerationCancellation() {
    generationCancelRequested = true;
    statusMessage = "Cancellation requested. Waiting for cleanup...";
    errorMessage = "";
    if (generationFuture != null) {
      generationFuture.cancel(true);
    }
  }

  private boolean isGenerationInProgress() {
    return generationState == GenerationState.GENERATING;
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

  private void generatedProjectCleanup() {
    if (generationState == GenerationState.SUCCESS
        || generationState == GenerationState.ERROR
        || generationState == GenerationState.CANCELLED) {
      generationState = GenerationState.IDLE;
    }
  }

  private String generationStateLabel() {
    return switch (generationState) {
      case IDLE -> "idle";
      case GENERATING -> "running (Esc to cancel)";
      case SUCCESS -> "success";
      case ERROR -> "failed";
      case CANCELLED -> "cancelled";
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

  private static String nextStepCommand(String buildTool) {
    return "gradle".equalsIgnoreCase(buildTool) ? "./gradlew quarkusDev" : "mvn quarkus:dev";
  }

  @FunctionalInterface
  public interface ExtensionCatalogLoader {
    CompletableFuture<List<ExtensionDto>> load();
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

  @FunctionalInterface
  public interface ProjectGenerationRunner {
    CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<String> progressListener);
  }

  private enum GenerationState {
    IDLE,
    GENERATING,
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
