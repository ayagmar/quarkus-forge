package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.ApiContractException;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityValidator;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationError;
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
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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

  private static final List<ExtensionItem> DEFAULT_EXTENSIONS =
      List.of(
          new ExtensionItem("io.quarkus:quarkus-rest", "REST", "rest"),
          new ExtensionItem("io.quarkus:quarkus-resteasy-jackson", "REST Jackson", "rest-jackson"),
          new ExtensionItem(
              "io.quarkus:quarkus-jdbc-postgresql", "JDBC PostgreSQL", "jdbc-postgresql"),
          new ExtensionItem("io.quarkus:quarkus-hibernate-orm", "Hibernate ORM", "hibernate-orm"),
          new ExtensionItem("io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi"),
          new ExtensionItem("io.quarkus:quarkus-arc", "CDI", "cdi"),
          new ExtensionItem("io.quarkus:quarkus-junit5", "JUnit 5", "junit5"));

  private static final int NARROW_WIDTH_THRESHOLD = 100;

  private final EnumMap<FocusTarget, TextInputState> inputStates = new EnumMap<>(FocusTarget.class);
  private final ListState extensionListState = new ListState();
  private final Set<String> selectedExtensionIds = new LinkedHashSet<>();
  private final ProjectRequestValidator requestValidator = new ProjectRequestValidator();
  private final MetadataCompatibilityValidator compatibilityValidator =
      new MetadataCompatibilityValidator();

  private final MetadataDto metadataSnapshot;
  private final String metadataLoadError;

  private ProjectRequest request;
  private ValidationReport validation;
  private FocusTarget focusTarget;
  private List<ExtensionItem> filteredExtensions;
  private String statusMessage;
  private String errorMessage;
  private boolean submitRequested;

  private CoreTuiController(ForgeUiState initialState) {
    Objects.requireNonNull(initialState);
    request = initialState.request();
    validation = initialState.validation();
    focusTarget = FocusTarget.GROUP_ID;
    statusMessage = "Ready";
    errorMessage = "";
    submitRequested = false;

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

    MetadataDto loadedSnapshot = null;
    String loadedError = null;
    try {
      loadedSnapshot = MetadataSnapshotLoader.loadDefault();
    } catch (ApiContractException contractException) {
      loadedError = contractException.getMessage();
    }
    metadataSnapshot = loadedSnapshot;
    metadataLoadError = loadedError;

    refreshFilteredExtensions();
    revalidate();
  }

  public static CoreTuiController from(ForgeUiState initialState) {
    return new CoreTuiController(initialState);
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
      return UiAction.handled(true);
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

    if (focusTarget == FocusTarget.EXTENSION_LIST && handleExtensionListKeys(keyEvent)) {
      return UiAction.handled(false);
    }

    if (isTextInputFocus(focusTarget)
        && handleTextInputKey(inputStates.get(focusTarget), keyEvent)) {
      if (focusTarget == FocusTarget.EXTENSION_SEARCH) {
        refreshFilteredExtensions();
      } else {
        rebuildRequestFromInputs();
        revalidate();
      }
      return UiAction.handled(false);
    }

    if (keyEvent.isConfirm()) {
      submitRequested = true;
      if (!validation.isValid()) {
        errorMessage = firstValidationError(validation);
        statusMessage = "Submit blocked: invalid input";
      } else {
        errorMessage = "";
        statusMessage =
            "Submit requested with "
                + selectedExtensionIds.size()
                + " extension(s). Generation flow lands in ISSUE-P1-02.";
      }
      return UiAction.handled(false);
    }

    return UiAction.ignored();
  }

  public void render(Frame frame) {
    Rect area = frame.area();
    List<Rect> rootLayout =
        Layout.vertical()
            .constraints(Constraint.length(3), Constraint.fill(), Constraint.length(4))
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
    return List.copyOf(selectedExtensionIds);
  }

  String statusMessage() {
    return statusMessage;
  }

  boolean submitRequested() {
    return submitRequested;
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
    List<ListItem> items = new ArrayList<>();
    for (ExtensionItem extension : filteredExtensions) {
      boolean selected = selectedExtensionIds.contains(extension.id());
      String prefix = selected ? "[x] " : "[ ] ";
      items.add(ListItem.from(prefix + extension.name() + " (" + extension.shortName() + ")"));
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
    frame.renderStatefulWidget(listWidget, area, extensionListState);
  }

  private void renderSelectedSummary(Frame frame, Rect area) {
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
        "Tab/Shift+Tab: focus | Up/Down: list nav | Space: toggle extension | Enter: submit | Esc: quit\n"
            + "Status: "
            + statusMessage
            + "\n"
            + "Validation: "
            + (validation.isValid() ? "OK" : firstValidationError(validation))
            + " | Focus: "
            + focusTargetName(focusTarget);
    if (!errorMessage.isBlank()) {
      footerText += "\nError: " + errorMessage;
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

  private boolean handleExtensionListKeys(KeyEvent keyEvent) {
    int size = filteredExtensions.size();
    if (size == 0) {
      return false;
    }
    if (keyEvent.isUp()) {
      extensionListState.selectPrevious();
      return true;
    }
    if (keyEvent.isDown()) {
      extensionListState.selectNext(size);
      return true;
    }
    if (keyEvent.isHome()) {
      extensionListState.selectFirst();
      return true;
    }
    if (keyEvent.isEnd()) {
      extensionListState.selectLast(size);
      return true;
    }
    if (keyEvent.isSelect()) {
      Integer selected = extensionListState.selected();
      if (selected == null || selected < 0 || selected >= size) {
        return false;
      }
      ExtensionItem extension = filteredExtensions.get(selected);
      if (!selectedExtensionIds.add(extension.id())) {
        selectedExtensionIds.remove(extension.id());
      }
      statusMessage = "Toggled extension: " + extension.shortName();
      return true;
    }
    return false;
  }

  private void moveFocus(int offset) {
    int index = FOCUS_ORDER.indexOf(focusTarget);
    int size = FOCUS_ORDER.size();
    int nextIndex = Math.floorMod(index + offset, size);
    focusTarget = FOCUS_ORDER.get(nextIndex);
    statusMessage = "Focus moved to " + focusTargetName(focusTarget);
    errorMessage = "";
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

  private void refreshFilteredExtensions() {
    String query =
        inputStates.get(FocusTarget.EXTENSION_SEARCH).text().toLowerCase(Locale.ROOT).trim();
    if (query.isBlank()) {
      filteredExtensions = DEFAULT_EXTENSIONS;
    } else {
      filteredExtensions =
          DEFAULT_EXTENSIONS.stream()
              .filter(
                  extension ->
                      extension.id().toLowerCase(Locale.ROOT).contains(query)
                          || extension.name().toLowerCase(Locale.ROOT).contains(query)
                          || extension.shortName().toLowerCase(Locale.ROOT).contains(query))
              .toList();
    }

    if (filteredExtensions.isEmpty()) {
      extensionListState.select(null);
    } else if (extensionListState.selected() == null
        || extensionListState.selected() >= filteredExtensions.size()) {
      extensionListState.selectFirst();
    }
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
    if (metadataLoadError != null) {
      report =
          report.merge(
              new ValidationReport(List.of(new ValidationError("metadata", metadataLoadError))));
    } else if (metadataSnapshot != null) {
      report = report.merge(compatibilityValidator.validate(request, metadataSnapshot));
    }
    validation = report;
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

  private record ExtensionItem(String id, String name, String shortName) {}

  public record UiAction(boolean handled, boolean shouldQuit) {
    static UiAction ignored() {
      return new UiAction(false, false);
    }

    static UiAction handled(boolean shouldQuit) {
      return new UiAction(true, shouldQuit);
    }
  }
}
