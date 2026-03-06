package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class CoreUiRenderAdapter implements UiRenderer.Adapter {
  private static final int METADATA_PANEL_HEIGHT_COMPACT = 4;
  private static final int METADATA_PANEL_HEIGHT_NARROW = 10;

  record RenderContext(
      MetadataFieldRenderContext metadataFields,
      TextInputState extensionSearchState,
      ListState extensionListState,
      ExtensionFlagLookup selectedLookup,
      ExtensionFlagLookup favoriteLookup) {
    RenderContext {
      metadataFields = Objects.requireNonNull(metadataFields);
      extensionSearchState = Objects.requireNonNull(extensionSearchState);
      extensionListState = Objects.requireNonNull(extensionListState);
      selectedLookup = Objects.requireNonNull(selectedLookup);
      favoriteLookup = Objects.requireNonNull(favoriteLookup);
    }
  }

  private final UiTheme theme;
  private final BodyPanelRenderer bodyPanelRenderer;
  private final FooterLinesComposer footerLinesComposer;
  private final CompactFieldRenderer compactFieldRenderer;
  private RenderContext renderContext;

  CoreUiRenderAdapter(
      UiTheme theme,
      BodyPanelRenderer bodyPanelRenderer,
      FooterLinesComposer footerLinesComposer,
      CompactFieldRenderer compactFieldRenderer) {
    this.theme = Objects.requireNonNull(theme);
    this.bodyPanelRenderer = Objects.requireNonNull(bodyPanelRenderer);
    this.footerLinesComposer = Objects.requireNonNull(footerLinesComposer);
    this.compactFieldRenderer = Objects.requireNonNull(compactFieldRenderer);
  }

  void updateContext(RenderContext renderContext) {
    this.renderContext = Objects.requireNonNull(renderContext);
  }

  @Override
  public List<String> composeFooterLines(int width, FooterSnapshot footerSnapshot) {
    return footerLinesComposer.compose(width, footerSnapshot);
  }

  @Override
  public int estimateFooterHeight(List<String> lines, int availableWidth) {
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
  public void renderHeader(Frame frame, Rect area) {
    frame.renderWidget(
        Paragraph.builder()
            .text("  QUARKUS FORGE  ─  Keyboard-first project generator")
            .style(Style.EMPTY.fg(theme.color("accent")).bold())
            .overflow(Overflow.ELLIPSIS)
            .build(),
        area);
  }

  @Override
  public void renderBody(
      Frame frame,
      Rect area,
      MetadataPanelSnapshot metadataPanelSnapshot,
      ExtensionsPanelSnapshot extensionsPanelSnapshot) {
    RenderContext context = requireContext();
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
        compactFieldRenderer,
        context.metadataFields(),
        CoreUiRenderAdapter::panelTitle,
        this::panelBorderStyle);
    bodyPanelRenderer.renderExtensionsPanel(
        frame,
        bodyLayout.get(1),
        extensionsPanelSnapshot,
        context.extensionSearchState(),
        context.extensionListState(),
        CoreUiRenderAdapter::panelTitle,
        this::panelBorderStyle,
        context.selectedLookup(),
        context.favoriteLookup());
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
    frame.renderWidget(
        Paragraph.builder()
            .text(sb.toString())
            .style(Style.EMPTY.fg(theme.color("muted")))
            .overflow(Overflow.WRAP_WORD)
            .build(),
        area);
  }

  @Override
  public void renderGenerationOverlay(
      Frame frame, Rect viewport, UiState.GenerationView generation) {
    OverlayRenderer.renderGenerationOverlay(
        frame, viewport, theme, generation.progressRatio(), generation.progressPhase());
  }

  @Override
  public void renderCommandPalette(Frame frame, Rect viewport, int selection) {
    OverlayRenderer.renderCommandPalette(
        frame, viewport, theme, UiTextConstants.COMMAND_PALETTE_ENTRIES, selection);
  }

  @Override
  public void renderHelpOverlay(Frame frame, Rect viewport, UiState state) {
    OverlayRenderer.renderHelpOverlay(
        frame, viewport, theme, helpOverlayLines(state), helpOverlayTitle(state));
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

  @Override
  public void renderStartupStatusOverlay(
      Frame frame, Rect viewport, UiState.StartupOverlayView startupOverlay) {
    OverlayRenderer.renderStartupOverlay(frame, viewport, theme, startupOverlay.statusLines());
  }

  private RenderContext requireContext() {
    if (renderContext == null) {
      throw new IllegalStateException("render context must be configured before rendering");
    }
    return renderContext;
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

  private static String panelTitle(String baseTitle, boolean focused) {
    return focused ? baseTitle + " [focus]" : baseTitle;
  }

  private static String helpOverlayTitle(UiState state) {
    return "Help [focus] - " + contextHelpTitle(state);
  }

  private static List<String> helpOverlayLines(UiState state) {
    List<String> lines = new ArrayList<>(UiTextConstants.GLOBAL_HELP_LINES);
    lines.add("");
    lines.add("Context (" + contextHelpTitle(state) + ")");
    lines.addAll(contextHelpLines(state));
    return lines;
  }

  private static String contextHelpTitle(UiState state) {
    if (state.postGeneration().githubVisibilityVisible()) {
      return "github visibility";
    }
    if (state.postGeneration().visible()) {
      return "post-generate";
    }
    if (state.overlays().generationVisible()) {
      return "generation";
    }
    return switch (state.focusTarget()) {
      case EXTENSION_SEARCH -> "extension search";
      case EXTENSION_LIST -> "extension list";
      case SUBMIT -> "submit";
      default -> "metadata";
    };
  }

  private static List<String> contextHelpLines(UiState state) {
    if (state.postGeneration().githubVisibilityVisible()) {
      return List.of(
          "  Up/Down or j/k  : choose repository visibility",
          "  Enter           : confirm and publish",
          "  Esc             : back to post-generate actions");
    }
    if (state.postGeneration().visible()) {
      return List.of(
          "  Up/Down or j/k  : choose post-generate action",
          "  Enter           : run selected action",
          "  Esc             : quit");
    }
    if (state.overlays().generationVisible()) {
      return List.of(
          "  Esc / Ctrl+C    : cancel current generation",
          "  Enter           : disabled while generation is active");
    }
    return switch (state.focusTarget()) {
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
}
