package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.common.SizedWidget;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.toggle.Toggle;
import dev.tamboui.widgets.toggle.ToggleState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class BodyPanelRenderer {

  private final UiTheme theme;
  private final TextInput extensionSearchInputWidget;

  BodyPanelRenderer(UiTheme theme) {
    this.theme = Objects.requireNonNull(theme);
    Style focusStyle = UiInputStyles.focusedField(theme, false);
    extensionSearchInputWidget =
        TextInput.builder().style(focusStyle).cursorStyle(UiInputStyles.cursor(theme)).build();
  }

  void renderMetadataPanel(
      Frame frame,
      Rect area,
      MetadataPanelSnapshot snapshot,
      CompactInputRenderer inputRenderer,
      MetadataFieldRenderContext renderContext,
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver) {
    Objects.requireNonNull(snapshot);
    Objects.requireNonNull(inputRenderer);
    Objects.requireNonNull(renderContext);
    Objects.requireNonNull(panelTitleFormatter);
    Objects.requireNonNull(panelBorderStyleResolver);

    Block panelBlock =
        Block.builder()
            .title(panelTitleFormatter.format(snapshot.title(), snapshot.focused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(
                panelBorderStyleResolver.resolve(snapshot.focused(), snapshot.invalid(), false))
            .build();
    frame.renderWidget(panelBlock, area);

    Rect inner = panelBlock.inner(area);
    if (inner.isEmpty()) {
      return;
    }

    if (inner.width() < UiLayoutConstants.NARROW_WIDTH_THRESHOLD || inner.height() < 2) {
      renderMetadataPanelNarrow(frame, inner, snapshot, inputRenderer, renderContext);
      return;
    }

    renderMetadataPanelWide(frame, inner, snapshot, inputRenderer, renderContext);
  }

  private void renderMetadataPanelNarrow(
      Frame frame,
      Rect area,
      MetadataPanelSnapshot snapshot,
      CompactInputRenderer inputRenderer,
      MetadataFieldRenderContext renderContext) {
    List<Constraint> constraints = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      constraints.add(Constraint.length(1));
    }
    constraints.add(Constraint.fill());
    List<Rect> rows = Layout.vertical().constraints(constraints).split(area);

    int rowIdx = 0;
    inputRenderer.renderCompactText(
        frame,
        rows.get(rowIdx++),
        "Group",
        snapshot.groupId(),
        renderContext,
        FocusTarget.GROUP_ID);
    inputRenderer.renderCompactText(
        frame,
        rows.get(rowIdx++),
        "Artifact",
        snapshot.artifactId(),
        renderContext,
        FocusTarget.ARTIFACT_ID);
    inputRenderer.renderCompactSelector(
        frame,
        rows.get(rowIdx++),
        "Build",
        snapshot.buildTool(),
        renderContext,
        FocusTarget.BUILD_TOOL,
        snapshot.buildToolInfo().selectedIndex(),
        snapshot.buildToolInfo().totalOptions());
    inputRenderer.renderCompactSelector(
        frame,
        rows.get(rowIdx++),
        "Platform",
        snapshot.platformStream(),
        renderContext,
        FocusTarget.PLATFORM_STREAM,
        snapshot.platformStreamInfo().selectedIndex(),
        snapshot.platformStreamInfo().totalOptions());
    inputRenderer.renderCompactText(
        frame,
        rows.get(rowIdx++),
        "Version",
        snapshot.version(),
        renderContext,
        FocusTarget.VERSION);
    inputRenderer.renderCompactText(
        frame,
        rows.get(rowIdx++),
        "Package",
        snapshot.packageName(),
        renderContext,
        FocusTarget.PACKAGE_NAME);
    inputRenderer.renderCompactSelector(
        frame,
        rows.get(rowIdx++),
        "Java",
        snapshot.javaVersion(),
        renderContext,
        FocusTarget.JAVA_VERSION,
        snapshot.javaVersionInfo().selectedIndex(),
        snapshot.javaVersionInfo().totalOptions());
    inputRenderer.renderCompactText(
        frame,
        rows.get(rowIdx),
        "Output",
        snapshot.outputDir(),
        renderContext,
        FocusTarget.OUTPUT_DIR);
  }

  private void renderMetadataPanelWide(
      Frame frame,
      Rect area,
      MetadataPanelSnapshot snapshot,
      CompactInputRenderer inputRenderer,
      MetadataFieldRenderContext renderContext) {
    List<Rect> rows =
        Layout.vertical()
            .constraints(Constraint.length(1), Constraint.length(1), Constraint.fill())
            .split(area);

    List<Rect> topRow =
        Layout.horizontal()
            .constraints(
                Constraint.ratio(1, 4),
                Constraint.ratio(1, 4),
                Constraint.ratio(1, 4),
                Constraint.ratio(1, 4))
            .split(rows.get(0));
    List<Rect> bottomRow =
        Layout.horizontal()
            .constraints(
                Constraint.ratio(1, 4),
                Constraint.ratio(1, 4),
                Constraint.ratio(1, 4),
                Constraint.ratio(1, 4))
            .split(rows.get(1));

    inputRenderer.renderCompactText(
        frame, topRow.get(0), "Group", snapshot.groupId(), renderContext, FocusTarget.GROUP_ID);
    inputRenderer.renderCompactText(
        frame,
        topRow.get(1),
        "Artifact",
        snapshot.artifactId(),
        renderContext,
        FocusTarget.ARTIFACT_ID);
    inputRenderer.renderCompactSelector(
        frame,
        topRow.get(2),
        "Build",
        snapshot.buildTool(),
        renderContext,
        FocusTarget.BUILD_TOOL,
        snapshot.buildToolInfo().selectedIndex(),
        snapshot.buildToolInfo().totalOptions());
    inputRenderer.renderCompactSelector(
        frame,
        topRow.get(3),
        "Platform",
        snapshot.platformStream(),
        renderContext,
        FocusTarget.PLATFORM_STREAM,
        snapshot.platformStreamInfo().selectedIndex(),
        snapshot.platformStreamInfo().totalOptions());

    inputRenderer.renderCompactText(
        frame, bottomRow.get(0), "Version", snapshot.version(), renderContext, FocusTarget.VERSION);
    inputRenderer.renderCompactText(
        frame,
        bottomRow.get(1),
        "Package",
        snapshot.packageName(),
        renderContext,
        FocusTarget.PACKAGE_NAME);
    inputRenderer.renderCompactSelector(
        frame,
        bottomRow.get(2),
        "Java",
        snapshot.javaVersion(),
        renderContext,
        FocusTarget.JAVA_VERSION,
        snapshot.javaVersionInfo().selectedIndex(),
        snapshot.javaVersionInfo().totalOptions());
    inputRenderer.renderCompactText(
        frame,
        bottomRow.get(3),
        "Output",
        snapshot.outputDir(),
        renderContext,
        FocusTarget.OUTPUT_DIR);
  }

  void renderExtensionsPanel(
      Frame frame,
      Rect area,
      ExtensionsPanelSnapshot snapshot,
      TextInputState searchInputState,
      ListState listState,
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver,
      ExtensionFlagLookup selectedLookup,
      ExtensionFlagLookup favoriteLookup) {
    Objects.requireNonNull(snapshot);
    Objects.requireNonNull(searchInputState);
    Objects.requireNonNull(listState);
    Objects.requireNonNull(panelTitleFormatter);
    Objects.requireNonNull(panelBorderStyleResolver);
    Objects.requireNonNull(selectedLookup);
    Objects.requireNonNull(favoriteLookup);

    boolean extensionError = !snapshot.catalogErrorMessage().isBlank();
    String title = buildExtensionsTitle(snapshot);
    Block panelBlock =
        Block.builder()
            .title(panelTitleFormatter.format(title, snapshot.panelFocused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(
                panelBorderStyleResolver.resolve(
                    snapshot.panelFocused(), extensionError, snapshot.loading()))
            .build();
    frame.renderWidget(panelBlock, area);

    Rect inner = panelBlock.inner(area);
    if (inner.isEmpty()) {
      return;
    }

    // Layout: Search + Selected Summary + Extension List + Submit Button
    boolean showSearchInput = snapshot.searchFocused();
    boolean hasSelected = !snapshot.selectedExtensionIds().isEmpty();
    boolean showSubmitButton = true;
    boolean showDescription =
        snapshot.listFocused() && !snapshot.focusedExtensionDescription().isBlank();

    List<Constraint> sectionConstraints = new ArrayList<>();
    sectionConstraints.add(Constraint.length(1)); // Search hint
    if (showSearchInput) {
      sectionConstraints.add(Constraint.length(1)); // Search input
    }
    if (hasSelected) {
      sectionConstraints.add(Constraint.length(1)); // Selected extensions summary
    }
    sectionConstraints.add(Constraint.fill()); // Extension list
    if (showDescription) {
      sectionConstraints.add(Constraint.length(1)); // Extension description
    }
    if (showSubmitButton) {
      sectionConstraints.add(Constraint.length(1)); // Submit button
    }
    List<Rect> sections = Layout.vertical().constraints(sectionConstraints).split(inner);

    int idx = 0;
    renderSearchHint(frame, sections.get(idx++), snapshot);
    if (showSearchInput) {
      renderSearchInput(frame, sections.get(idx++), snapshot, searchInputState);
    }
    if (hasSelected) {
      renderSelectedSummary(frame, sections.get(idx++), snapshot);
    }
    renderExtensionList(
        frame, sections.get(idx++), snapshot, listState, selectedLookup, favoriteLookup);
    if (showDescription) {
      renderExtensionDescription(frame, sections.get(idx++), snapshot);
    }
    if (showSubmitButton) {
      renderSubmitButton(frame, sections.get(idx), snapshot);
    }
  }

  private String buildExtensionsTitle(ExtensionsPanelSnapshot snapshot) {
    StringBuilder title = new StringBuilder("Extensions");
    if (snapshot.loading()) {
      title.append(" [loading]");
    } else if (!snapshot.catalogErrorMessage().isBlank()) {
      title.append(" [fallback]");
    }
    int selected = snapshot.selectedExtensionIds().size();
    int total = snapshot.totalCatalogExtensionCount();
    if (selected > 0) {
      title.append(" (").append(selected).append(" selected)");
    } else {
      title.append(" (").append(total).append(" available)");
    }
    if (snapshot.favoritesOnlyFilterEnabled()) {
      title.append(" [fav]");
    }
    if (snapshot.selectedOnlyFilterEnabled()) {
      title.append(" [sel]");
    }
    if (!snapshot.activePresetFilterName().isBlank()) {
      title.append(" [preset:").append(snapshot.activePresetFilterName()).append("]");
    }
    if (!snapshot.activeCategoryFilterTitle().isBlank()) {
      title.append(" [").append(snapshot.activeCategoryFilterTitle()).append("]");
    }
    return title.toString();
  }

  private void renderSearchHint(Frame frame, Rect area, ExtensionsPanelSnapshot snapshot) {
    StringBuilder hint = new StringBuilder();
    if (snapshot.loading()) {
      hint.append("Loading extension catalog...");
    } else if (!snapshot.catalogErrorMessage().isBlank()) {
      hint.append(catalogSourceLabel(snapshot));
      hint.append(" | error: ").append(snapshot.catalogErrorMessage());
    } else {
      int filtered = snapshot.filteredExtensionCount();
      int total = snapshot.totalCatalogExtensionCount();
      if (snapshot.selectedOnlyFilterEnabled()) {
        hint.append("Selected-only view");
        hint.append(" | ").append(filtered).append(" of ").append(total);
      } else if (!snapshot.activeCategoryFilterTitle().isBlank()) {
        hint.append("Filter: ").append(snapshot.activeCategoryFilterTitle());
        hint.append(" | ").append(filtered).append(" of ").append(total);
      } else if (!snapshot.activePresetFilterName().isBlank()) {
        hint.append("Preset: ").append(snapshot.activePresetFilterName());
        hint.append(" | ").append(filtered).append(" of ").append(total);
      } else {
        hint.append(catalogSourceLabel(snapshot));
        hint.append(" | Type '/' to search (").append(filtered).append(" shown)");
      }
      if (snapshot.favoriteCount() > 0) {
        hint.append(" | ").append(snapshot.favoriteCount()).append(" favorites");
      }
    }

    Style style = Style.EMPTY.fg(theme.color("muted"));
    if (!snapshot.catalogErrorMessage().isBlank()) {
      style = Style.EMPTY.fg(theme.color("warning"));
    }

    Paragraph paragraph =
        Paragraph.builder()
            .text("  " + hint.toString())
            .style(style)
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(paragraph, area);
  }

  private void renderSearchInput(
      Frame frame, Rect area, ExtensionsPanelSnapshot snapshot, TextInputState searchInputState) {
    String query = snapshot.searchQuery();
    int filtered = snapshot.filteredExtensionCount();
    int total = snapshot.totalCatalogExtensionCount();
    String matchInfo = query.isBlank() ? "" : "  " + filtered + "/" + total + " matches";
    String prefix = "  Search: [";
    String suffix = "]" + matchInfo + "  (Esc to clear)";
    int inputWidth = area.width() - prefix.length() - suffix.length();
    if (inputWidth < 1) {
      String display = "  Search: [ " + query + " ]" + matchInfo + "  (Esc to clear)";
      frame.renderWidget(
          Paragraph.builder()
              .text(display)
              .style(Style.EMPTY.fg(theme.color("focus")).bold())
              .overflow(Overflow.ELLIPSIS)
              .build(),
          area);
      return;
    }

    Style focusStyle = UiInputStyles.focusedField(theme, false);
    frame.renderWidget(
        Paragraph.builder().text(prefix).style(focusStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(area.left(), area.top(), Math.min(prefix.length(), area.width()), area.height()));

    Rect inputArea = new Rect(area.left() + prefix.length(), area.top(), inputWidth, area.height());
    extensionSearchInputWidget.renderWithCursor(inputArea, frame.buffer(), searchInputState, frame);

    frame.renderWidget(
        Paragraph.builder().text(suffix).style(focusStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(
            inputArea.left() + inputArea.width(),
            area.top(),
            area.width() - prefix.length() - inputArea.width(),
            area.height()));
  }

  private void renderSelectedSummary(Frame frame, Rect area, ExtensionsPanelSnapshot snapshot) {
    List<String> selectedIds = snapshot.selectedExtensionIds();
    if (selectedIds.isEmpty()) {
      return;
    }

    int maxDisplay = Math.min(selectedIds.size(), 5);

    // Build a compact summary of selected extensions.
    // Prefer human-friendly extension names from the currently visible rows.
    Set<String> neededIds = new LinkedHashSet<>(selectedIds.subList(0, maxDisplay));
    Map<String, String> visibleNameById = new LinkedHashMap<>();
    for (ExtensionCatalogRow row : snapshot.filteredRows()) {
      if (row.extension() == null) {
        continue;
      }
      String extensionId = row.extension().id();
      if (!neededIds.contains(extensionId) || visibleNameById.containsKey(extensionId)) {
        continue;
      }
      visibleNameById.put(extensionId, row.extension().name());
      if (visibleNameById.size() >= neededIds.size()) {
        break;
      }
    }

    List<Span> spans = new ArrayList<>();
    spans.add(Span.styled("  Selected: ", Style.EMPTY.fg(theme.color("accent"))));
    for (int i = 0; i < maxDisplay; i++) {
      if (i > 0) {
        spans.add(Span.styled(" ", Style.EMPTY));
      }
      String id = selectedIds.get(i);
      String name = visibleNameById.get(id);
      if (name == null || name.isBlank()) {
        String shortName = id.contains(":") ? id.substring(id.lastIndexOf(':') + 1) : id;
        if (shortName.startsWith("quarkus-")) {
          shortName = shortName.substring(8);
        }
        name = shortName;
      }
      spans.add(
          Span.styled(
              " " + name + " ", Style.EMPTY.fg(theme.color("base")).bg(theme.color("accent"))));
    }
    if (selectedIds.size() > maxDisplay) {
      spans.add(
          Span.styled(
              " +" + (selectedIds.size() - maxDisplay) + " more ",
              Style.EMPTY.fg(theme.color("base")).bg(theme.color("muted"))));
    }
    spans.add(Span.styled("  [x: clear]", Style.EMPTY.fg(theme.color("muted"))));

    Paragraph paragraph =
        Paragraph.builder().text(Text.from(Line.from(spans))).overflow(Overflow.ELLIPSIS).build();
    frame.renderWidget(paragraph, area);
  }

  private void renderExtensionDescription(
      Frame frame, Rect area, ExtensionsPanelSnapshot snapshot) {
    String description = snapshot.focusedExtensionDescription();
    int maxLen = Math.max(10, area.width() - 4);
    if (description.length() > maxLen) {
      description = description.substring(0, maxLen - 3) + "...";
    }
    Paragraph paragraph =
        Paragraph.builder()
            .text("  " + description)
            .style(Style.EMPTY.fg(theme.color("muted")).italic())
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(paragraph, area);
  }

  private void renderSubmitButton(Frame frame, Rect area, ExtensionsPanelSnapshot snapshot) {
    boolean focused = snapshot.submitFocused();
    int blockedIssues = snapshot.validationErrorCount();
    boolean blocked = blockedIssues > 0;
    int selectedCount = snapshot.selectedExtensionIds().size();
    String countLabel;
    if (selectedCount == 0) {
      countLabel = "";
    } else if (selectedCount == 1) {
      countLabel = " (1 extension)";
    } else {
      countLabel = " (" + selectedCount + " extensions)";
    }
    String blockedLabel =
        " (blocked: " + blockedIssues + " issue" + (blockedIssues == 1 ? "" : "s") + ")";
    String focusedLabel =
        blocked
            ? "  [ Generate Project" + blockedLabel + " ]"
            : "  [ Generate Project" + countLabel + " (Enter) ]";
    String unfocusedLabel =
        blocked
            ? "  [ Generate Project" + blockedLabel + " ]"
            : "  [ Generate Project" + countLabel + " (Enter/Alt+G) ]";

    Toggle toggle =
        Toggle.builder()
            .onSymbol(focusedLabel)
            .offSymbol(unfocusedLabel)
            .onColor(blocked ? theme.color("error") : theme.color("focus"))
            .offColor(blocked ? theme.color("warning") : theme.color("accent"))
            .build();
    frame.renderStatefulWidget(toggle, area, new ToggleState(focused));
  }

  private static String catalogSourceLabel(ExtensionsPanelSnapshot snapshot) {
    StringBuilder label = new StringBuilder("Catalog: ");
    String source = snapshot.catalogSource();
    label.append(source.isBlank() ? "snapshot" : source);
    if (snapshot.catalogStale()) {
      label.append(" [stale]");
    }
    return label.toString();
  }

  private void renderExtensionList(
      Frame frame,
      Rect area,
      ExtensionsPanelSnapshot snapshot,
      ListState listState,
      ExtensionFlagLookup selectedLookup,
      ExtensionFlagLookup favoriteLookup) {
    if (snapshot.loading()) {
      return;
    }

    List<SizedWidget> items = new ArrayList<>();
    for (ExtensionCatalogRow row : snapshot.filteredRows()) {
      if (row.isSectionHeader()) {
        items.add(ListItem.from(sectionHeaderLabel(row)).toSizedWidget());
        continue;
      }
      ExtensionCatalogItem extension = row.extension();
      boolean selected = selectedLookup.matches(extension.id());
      boolean favorite = favoriteLookup.matches(extension.id());
      String checkedPrefix = selected ? "[x] " : "[ ] ";
      String favoritePrefix = favorite ? "★ " : "  ";
      String displayLabel = extensionDisplayLabel(extension);
      String query = snapshot.searchQuery();
      if (query.isBlank()) {
        items.add(ListItem.from(checkedPrefix + favoritePrefix + displayLabel).toSizedWidget());
      } else {
        Line line =
            buildHighlightedLine(checkedPrefix + favoritePrefix, displayLabel, query, theme);
        items.add(ListItem.from(line).toSizedWidget());
      }
    }

    boolean extensionError = !snapshot.catalogErrorMessage().isBlank();
    if (items.isEmpty()) {
      String emptyMessage;
      if (extensionError) {
        emptyMessage = "  Catalog unavailable - using fallback snapshot";
      } else if (snapshot.selectedOnlyFilterEnabled()) {
        emptyMessage = "  No selected extensions in current view";
      } else if (snapshot.favoritesOnlyFilterEnabled()) {
        emptyMessage = "  No favorites match current filter";
      } else {
        emptyMessage = "  No extensions match current filter";
      }
      Paragraph empty =
          Paragraph.builder()
              .text(emptyMessage)
              .style(Style.EMPTY.fg(extensionError ? theme.color("error") : theme.color("muted")))
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
            .build();
    frame.renderStatefulWidget(listWidget, area, listState);
  }

  private static String extensionDisplayLabel(ExtensionCatalogItem extension) {
    return extension.name();
  }

  private static Line buildHighlightedLine(
      String prefix, String label, String query, UiTheme theme) {
    List<Span> spans = new ArrayList<>();
    spans.add(Span.raw(prefix));

    String lowerLabel = label.toLowerCase(Locale.ROOT);
    String lowerQuery = query.toLowerCase(Locale.ROOT);
    Style highlightStyle = Style.EMPTY.fg(theme.color("warning")).bold();

    int labelPos = 0;
    int queryPos = 0;
    StringBuilder normal = new StringBuilder();

    while (labelPos < label.length() && queryPos < lowerQuery.length()) {
      if (lowerLabel.charAt(labelPos) == lowerQuery.charAt(queryPos)) {
        if (!normal.isEmpty()) {
          spans.add(Span.raw(normal.toString()));
          normal.setLength(0);
        }
        spans.add(Span.styled(String.valueOf(label.charAt(labelPos)), highlightStyle));
        queryPos++;
      } else {
        normal.append(label.charAt(labelPos));
      }
      labelPos++;
    }
    if (labelPos < label.length()) {
      normal.append(label.substring(labelPos));
    }
    if (!normal.isEmpty()) {
      spans.add(Span.raw(normal.toString()));
    }

    return Line.from(spans);
  }

  private static String sectionHeaderLabel(ExtensionCatalogRow row) {
    String prefix = row.collapsed() ? "▶ " : "▼ ";
    String suffix;
    if (row.collapsed()) {
      suffix = " (" + row.hiddenCount() + " hidden)";
    } else if (row.totalCount() > 0) {
      suffix = " (" + row.totalCount() + ")";
    } else {
      suffix = "";
    }
    return prefix + row.label() + suffix;
  }
}
