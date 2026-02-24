package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.common.ScrollBarPolicy;
import dev.tamboui.widgets.common.SizedWidget;
import dev.tamboui.widgets.list.ListItem;
import dev.tamboui.widgets.list.ListState;
import dev.tamboui.widgets.list.ListWidget;
import dev.tamboui.widgets.list.ScrollMode;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class BodyPanelRenderer {
  private static final int NARROW_WIDTH_THRESHOLD = 100;

  private final UiTheme theme;

  BodyPanelRenderer(UiTheme theme) {
    this.theme = Objects.requireNonNull(theme);
  }

  void renderMetadataPanel(
      Frame frame,
      Rect area,
      MetadataPanelSnapshot snapshot,
      InputRenderer inputRenderer,
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver) {
    Objects.requireNonNull(snapshot);
    Objects.requireNonNull(inputRenderer);
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

    List<Constraint> constraints = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      constraints.add(Constraint.length(3));
    }
    constraints.add(Constraint.fill());
    List<Rect> rows = Layout.vertical().constraints(constraints).split(inner);

    inputRenderer.render(frame, rows.get(0), "Group Id", FocusTarget.GROUP_ID);
    inputRenderer.render(frame, rows.get(1), "Artifact Id", FocusTarget.ARTIFACT_ID);
    inputRenderer.render(frame, rows.get(2), "Version", FocusTarget.VERSION);
    inputRenderer.render(frame, rows.get(3), "Quarkus Platform", FocusTarget.PLATFORM_STREAM);
    inputRenderer.render(frame, rows.get(4), "Build Tool", FocusTarget.BUILD_TOOL);
    inputRenderer.render(frame, rows.get(5), "Java Version", FocusTarget.JAVA_VERSION);
    inputRenderer.render(frame, rows.get(6), "Package Name", FocusTarget.PACKAGE_NAME);
    inputRenderer.render(frame, rows.get(7), "Output Dir", FocusTarget.OUTPUT_DIR);
  }

  void renderExtensionsPanel(
      Frame frame,
      Rect area,
      ExtensionsPanelSnapshot snapshot,
      ListState listState,
      InputRenderer inputRenderer,
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver,
      ExtensionFlagLookup selectedLookup,
      ExtensionFlagLookup favoriteLookup) {
    Objects.requireNonNull(snapshot);
    Objects.requireNonNull(listState);
    Objects.requireNonNull(inputRenderer);
    Objects.requireNonNull(panelTitleFormatter);
    Objects.requireNonNull(panelBorderStyleResolver);
    Objects.requireNonNull(selectedLookup);
    Objects.requireNonNull(favoriteLookup);

    boolean extensionError = !snapshot.catalogErrorMessage().isBlank();
    Block panelBlock =
        Block.builder()
            .title(panelTitleFormatter.format(snapshot.title(), snapshot.panelFocused()))
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

    List<Rect> sections =
        Layout.vertical()
            .constraints(Constraint.length(3), Constraint.fill(), Constraint.length(4))
            .split(inner);

    inputRenderer.render(
        frame, sections.get(0), searchInputLabel(snapshot), FocusTarget.EXTENSION_SEARCH);
    renderExtensionList(
        frame,
        sections.get(1),
        snapshot,
        listState,
        panelTitleFormatter,
        panelBorderStyleResolver,
        selectedLookup,
        favoriteLookup);
    renderSelectedSummary(
        frame, sections.get(2), snapshot, panelTitleFormatter, panelBorderStyleResolver);
  }

  private void renderExtensionList(
      Frame frame,
      Rect area,
      ExtensionsPanelSnapshot snapshot,
      ListState listState,
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver,
      ExtensionFlagLookup selectedLookup,
      ExtensionFlagLookup favoriteLookup) {
    boolean extensionError = !snapshot.catalogErrorMessage().isBlank();
    Block listBlock =
        Block.builder()
            .title(panelTitleFormatter.format("Catalog", snapshot.listFocused()))
            .borders(Borders.ALL)
            .borderType(BorderType.ROUNDED)
            .borderStyle(
                panelBorderStyleResolver.resolve(
                    snapshot.listFocused(), extensionError, snapshot.loading()))
            .build();

    if (snapshot.loading()) {
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
    for (ExtensionCatalogRow row : snapshot.filteredRows()) {
      if (row.isSectionHeader()) {
        items.add(ListItem.from(sectionHeaderLabel(row)).toSizedWidget());
        continue;
      }
      ExtensionCatalogItem extension = row.extension();
      boolean selected = selectedLookup.matches(extension.id());
      boolean favorite = favoriteLookup.matches(extension.id());
      String checkedPrefix = selected ? "[x] " : "[ ] ";
      String favoritePrefix = favorite ? "* " : "  ";
      String displayLabel = extensionDisplayLabel(extension);
      items.add(ListItem.from(checkedPrefix + favoritePrefix + displayLabel).toSizedWidget());
    }

    if (items.isEmpty()) {
      String emptyMessage;
      if (extensionError) {
        emptyMessage = "Catalog unavailable - using fallback snapshot";
      } else if (snapshot.favoritesOnlyFilterEnabled()) {
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
    frame.renderStatefulWidget(listWidget, area, listState);
  }

  private void renderSelectedSummary(
      Frame frame,
      Rect area,
      ExtensionsPanelSnapshot snapshot,
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver) {
    String summary = selectedExtensionsSummary(snapshot.selectedExtensionIds(), area.width());
    if (!snapshot.activeCategoryFilterTitle().isBlank()) {
      summary += "\nCategory filter: " + snapshot.activeCategoryFilterTitle();
    }
    summary += "\nCatalog: " + snapshot.catalogSource();
    if (snapshot.catalogStale()) {
      summary += " [stale]";
    }
    if (!snapshot.catalogErrorMessage().isBlank()) {
      summary += " | error: " + snapshot.catalogErrorMessage();
    }
    summary += "\nFavorites: " + snapshot.favoriteCount();
    if (snapshot.favoritesOnlyFilterEnabled()) {
      summary += " [filter:on]";
    }
    if (snapshot.submitFocused()) {
      summary += "\nSubmit focus active - press Enter to submit";
    }

    Style summaryStyle = Style.EMPTY.fg(theme.color("text"));
    if (!snapshot.catalogErrorMessage().isBlank()) {
      summaryStyle = summaryStyle.fg(theme.color("warning"));
    }

    Paragraph paragraph =
        Paragraph.builder()
            .text(summary)
            .style(summaryStyle)
            .overflow(Overflow.ELLIPSIS)
            .block(
                Block.builder()
                    .title(panelTitleFormatter.format("Selection", snapshot.submitFocused()))
                    .borders(Borders.ALL)
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(
                        panelBorderStyleResolver.resolve(snapshot.submitFocused(), false, false))
                    .build())
            .build();
    frame.renderWidget(paragraph, area);
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

  private static String extensionDisplayLabel(ExtensionCatalogItem extension) {
    return extension.name();
  }

  private static String searchInputLabel(ExtensionsPanelSnapshot snapshot) {
    return "Search Extensions ("
        + snapshot.filteredExtensionCount()
        + "/"
        + snapshot.totalCatalogExtensionCount()
        + ")";
  }

  private static String sectionHeaderLabel(ExtensionCatalogRow row) {
    String prefix = row.collapsed() ? "[+]" : "[-]";
    String suffix = row.collapsed() ? " (" + row.hiddenCount() + " hidden)" : "";
    return "-- " + prefix + " " + row.label() + suffix + " --";
  }

  @FunctionalInterface
  interface InputRenderer {
    void render(Frame frame, Rect area, String label, FocusTarget target);
  }

  @FunctionalInterface
  interface PanelTitleFormatter {
    String format(String baseTitle, boolean focused);
  }

  @FunctionalInterface
  interface PanelBorderStyleResolver {
    Style resolve(boolean focused, boolean hasError, boolean isLoading);
  }

  @FunctionalInterface
  interface ExtensionFlagLookup {
    boolean matches(String extensionId);
  }

  record MetadataPanelSnapshot(String title, boolean focused, boolean invalid) {
    MetadataPanelSnapshot {
      title = Objects.requireNonNull(title);
    }
  }

  record ExtensionsPanelSnapshot(
      String title,
      boolean panelFocused,
      boolean listFocused,
      boolean submitFocused,
      boolean loading,
      String catalogErrorMessage,
      String catalogSource,
      boolean catalogStale,
      boolean favoritesOnlyFilterEnabled,
      int favoriteCount,
      String activeCategoryFilterTitle,
      int filteredExtensionCount,
      int totalCatalogExtensionCount,
      List<ExtensionCatalogRow> filteredRows,
      List<String> selectedExtensionIds) {
    ExtensionsPanelSnapshot {
      title = Objects.requireNonNull(title);
      catalogErrorMessage = catalogErrorMessage == null ? "" : catalogErrorMessage;
      catalogSource = catalogSource == null ? "" : catalogSource;
      activeCategoryFilterTitle =
          activeCategoryFilterTitle == null ? "" : activeCategoryFilterTitle;
      filteredExtensionCount = Math.max(0, filteredExtensionCount);
      totalCatalogExtensionCount = Math.max(0, totalCatalogExtensionCount);
      filteredRows = List.copyOf(Objects.requireNonNull(filteredRows));
      selectedExtensionIds = List.copyOf(Objects.requireNonNull(selectedExtensionIds));
    }
  }
}
