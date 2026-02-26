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
      CompactInputRenderer inputRenderer,
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
    constraints.add(Constraint.length(1));
    for (int i = 0; i < 3; i++) {
      constraints.add(Constraint.length(1));
    }
    constraints.add(Constraint.length(1));
    for (int i = 0; i < 5; i++) {
      constraints.add(Constraint.length(1));
    }
    constraints.add(Constraint.fill());
    List<Rect> rows = Layout.vertical().constraints(constraints).split(inner);

    int rowIdx = 1;
    inputRenderer.renderSelector(
        frame, rows.get(rowIdx++), "Platform", FocusTarget.PLATFORM_STREAM);
    inputRenderer.renderSelector(frame, rows.get(rowIdx++), "Build Tool", FocusTarget.BUILD_TOOL);
    inputRenderer.renderSelector(frame, rows.get(rowIdx++), "Java", FocusTarget.JAVA_VERSION);
    rowIdx++;
    inputRenderer.renderText(frame, rows.get(rowIdx++), "Group", FocusTarget.GROUP_ID);
    inputRenderer.renderText(frame, rows.get(rowIdx++), "Artifact", FocusTarget.ARTIFACT_ID);
    inputRenderer.renderText(frame, rows.get(rowIdx++), "Version", FocusTarget.VERSION);
    inputRenderer.renderText(frame, rows.get(rowIdx++), "Package", FocusTarget.PACKAGE_NAME);
    inputRenderer.renderText(frame, rows.get(rowIdx++), "Output", FocusTarget.OUTPUT_DIR);
  }

  void renderExtensionsPanel(
      Frame frame,
      Rect area,
      ExtensionsPanelSnapshot snapshot,
      ListState listState,
      CompactInputRenderer inputRenderer,
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

    boolean showSearchInput = snapshot.searchFocused();
    boolean showSubmitButton = true;
    List<Constraint> sectionConstraints = new ArrayList<>();
    sectionConstraints.add(Constraint.length(1));
    if (showSearchInput) {
      sectionConstraints.add(Constraint.length(1));
    }
    sectionConstraints.add(Constraint.fill());
    if (showSubmitButton) {
      sectionConstraints.add(Constraint.length(1));
    }
    List<Rect> sections = Layout.vertical().constraints(sectionConstraints).split(inner);

    int idx = 0;
    renderSearchHint(frame, sections.get(idx++), snapshot);
    if (showSearchInput) {
      renderSearchInput(frame, sections.get(idx++), snapshot);
    }
    renderExtensionList(
        frame,
        sections.get(idx++),
        snapshot,
        listState,
        panelTitleFormatter,
        panelBorderStyleResolver,
        selectedLookup,
        favoriteLookup);
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
      title.append(" (").append(total).append(")");
    }
    if (snapshot.favoritesOnlyFilterEnabled()) {
      title.append(" [fav]");
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
      if (!snapshot.activeCategoryFilterTitle().isBlank()) {
        hint.append("Category filter: ").append(snapshot.activeCategoryFilterTitle());
        hint.append(" | ").append(filtered).append("/").append(total);
      } else {
        hint.append(catalogSourceLabel(snapshot));
        hint.append(" | Search Extensions (")
            .append(filtered)
            .append("/")
            .append(total)
            .append(")");
      }
      if (snapshot.favoriteCount() > 0) {
        hint.append(" | Favorites: ").append(snapshot.favoriteCount());
      }
    }

    Style style = Style.EMPTY.fg(theme.color("muted"));
    if (!snapshot.catalogErrorMessage().isBlank()) {
      style = Style.EMPTY.fg(theme.color("warning"));
    }

    Paragraph paragraph =
        Paragraph.builder().text(hint.toString()).style(style).overflow(Overflow.ELLIPSIS).build();
    frame.renderWidget(paragraph, area);
  }

  private void renderSearchInput(Frame frame, Rect area, ExtensionsPanelSnapshot snapshot) {
    String query = snapshot.searchQuery();
    String display = "  / [ " + query + "_ ]";
    Paragraph paragraph =
        Paragraph.builder()
            .text(display)
            .style(Style.EMPTY.fg(theme.color("focus")).bold())
            .overflow(Overflow.ELLIPSIS)
            .build();
    frame.renderWidget(paragraph, area);
  }

  private void renderSubmitButton(Frame frame, Rect area, ExtensionsPanelSnapshot snapshot) {
    boolean focused = snapshot.submitFocused();
    String label = focused ? "  >> [ Generate (Enter/Alt+G) ] <<" : "  [ Generate (Enter/Alt+G) ]";
    Style style =
        focused
            ? Style.EMPTY.fg(theme.color("focus")).bold().reversed()
            : Style.EMPTY.fg(theme.color("accent"));
    Paragraph paragraph =
        Paragraph.builder().text(label).style(style).overflow(Overflow.ELLIPSIS).build();
    frame.renderWidget(paragraph, area);
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
      PanelTitleFormatter panelTitleFormatter,
      PanelBorderStyleResolver panelBorderStyleResolver,
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
      String checkedPrefix = selected ? "● " : "○ ";
      String favoritePrefix = favorite ? "★ " : "  ";
      String displayLabel = extensionDisplayLabel(extension);
      items.add(ListItem.from(checkedPrefix + favoritePrefix + displayLabel).toSizedWidget());
    }

    boolean extensionError = !snapshot.catalogErrorMessage().isBlank();
    if (items.isEmpty()) {
      String emptyMessage;
      if (extensionError) {
        emptyMessage = "  Catalog unavailable - using fallback snapshot";
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

  private static String sectionHeaderLabel(ExtensionCatalogRow row) {
    String prefix = row.collapsed() ? "[+] " : "[-] ";
    String suffix = row.collapsed() ? " (" + row.hiddenCount() + " hidden)" : "";
    return prefix + row.label() + suffix;
  }

  interface CompactInputRenderer {
    void renderSelector(Frame frame, Rect area, String label, FocusTarget target);

    void renderText(Frame frame, Rect area, String label, FocusTarget target);
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
      boolean searchFocused,
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
      List<String> selectedExtensionIds,
      String searchQuery) {
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
      searchQuery = searchQuery == null ? "" : searchQuery;
    }
  }
}
