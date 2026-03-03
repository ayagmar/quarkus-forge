package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CatalogRowBuilderTest {

  static ExtensionCatalogItem item(String id, String name, String category) {
    return new ExtensionCatalogItem(id, name, name, category, null);
  }

  @Nested
  class BuildRows {
    @Test
    void emptyItemsReturnsEmptyList() {
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(List.of(), Set.of(), List.of(), "", false, false, "");
      assertThat(rows).isEmpty();
    }

    @Test
    void singleCategoryProducesSectionAndItems() {
      List<ExtensionCatalogItem> items =
          List.of(item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Web"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), List.of(), "", false, false, "");

      assertThat(rows).hasSize(3);
      assertThat(rows.get(0).isSectionHeader()).isTrue();
      assertThat(rows.get(0).label()).isEqualTo("Web");
      assertThat(rows.get(1).extension().id()).isEqualTo("a:b");
      assertThat(rows.get(2).extension().id()).isEqualTo("a:c");
    }

    @Test
    void multipleCategoriesCreateSeparateSections() {
      List<ExtensionCatalogItem> items =
          List.of(item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Data"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), List.of(), "", false, false, "");

      long sectionCount = rows.stream().filter(ExtensionCatalogRow::isSectionHeader).count();
      assertThat(sectionCount).isEqualTo(2);
    }

    @Test
    void collapsedCategoryHidesItems() {
      List<ExtensionCatalogItem> items =
          List.of(
              item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Web"), item("a:d", "ExtC", "Data"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of("Web"), List.of(), "", false, false, "");

      // Web section header (collapsed) + Data section header + ExtC
      assertThat(rows).hasSize(3);
      assertThat(rows.get(0).collapsed()).isTrue();
      assertThat(rows.get(0).hiddenCount()).isEqualTo(2);
      assertThat(rows.get(1).isSectionHeader()).isTrue();
      assertThat(rows.get(1).label()).isEqualTo("Data");
    }
  }

  @Nested
  class RecentItems {
    @Test
    void recentItemsPrependedWhenNoFilter() {
      List<ExtensionCatalogItem> items =
          List.of(item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Data"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), List.of("a:c"), "", false, false, "");

      assertThat(rows.get(0).label()).isEqualTo(CatalogRowBuilder.RECENT_SECTION_TITLE);
      assertThat(rows.get(1).extension().id()).isEqualTo("a:c");
    }

    @Test
    void recentItemsNotShownWhenSearchActive() {
      List<ExtensionCatalogItem> items = List.of(item("a:b", "ExtA", "Web"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), List.of("a:b"), "search", false, false, "");

      assertThat(
              rows.stream()
                  .noneMatch(r -> r.label().equals(CatalogRowBuilder.RECENT_SECTION_TITLE)))
          .isTrue();
    }

    @Test
    void recentItemsNotShownWhenFavoritesOnly() {
      List<ExtensionCatalogItem> items = List.of(item("a:b", "ExtA", "Web"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), List.of("a:b"), "", true, false, "");

      assertThat(
              rows.stream()
                  .noneMatch(r -> r.label().equals(CatalogRowBuilder.RECENT_SECTION_TITLE)))
          .isTrue();
    }

    @Test
    void recentItemsNotShownWhenSelectedOnly() {
      List<ExtensionCatalogItem> items = List.of(item("a:b", "ExtA", "Web"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), List.of("a:b"), "", false, true, "");

      assertThat(
              rows.stream()
                  .noneMatch(r -> r.label().equals(CatalogRowBuilder.RECENT_SECTION_TITLE)))
          .isTrue();
    }

    @Test
    void duplicateRecentIdsDeduped() {
      List<ExtensionCatalogItem> items = List.of(item("a:b", "ExtA", "Web"));
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(
              items, Set.of(), List.of("a:b", "a:b", "a:b"), "", false, false, "");

      assertThat(rows.get(0).label()).isEqualTo(CatalogRowBuilder.RECENT_SECTION_TITLE);
      assertThat(rows.get(1).extension().id()).isEqualTo("a:b");
      assertThat(rows.get(1).isSectionHeader()).isFalse();
      // After recent section: category section + item
      assertThat(rows.get(2).isSectionHeader()).isTrue();
      assertThat(rows.get(2).label()).isEqualTo("Web");
    }

    @Test
    void recentItemsCappedAtMaxSelections() {
      List<ExtensionCatalogItem> items = new java.util.ArrayList<>();
      List<String> recentIds = new java.util.ArrayList<>();
      for (int i = 0; i < 15; i++) {
        String id = "ext:" + i;
        items.add(item(id, "Ext" + i, "Web"));
        recentIds.add(id);
      }
      List<ExtensionCatalogRow> rows =
          CatalogRowBuilder.buildRows(items, Set.of(), recentIds, "", false, false, "");

      long recentCount =
          rows.stream()
              .takeWhile(
                  r ->
                      r.label().equals(CatalogRowBuilder.RECENT_SECTION_TITLE)
                          || r.extension() != null)
              .filter(r -> !r.isSectionHeader())
              .count();
      assertThat(recentCount).isEqualTo(CatalogRowBuilder.MAX_RECENT_SELECTIONS);
    }
  }

  @Nested
  class CategoryTitleResolution {
    @Test
    void knownCategoryKeysResolveCorrectly() {
      assertThat(CatalogRowBuilder.resolveCategoryTitle("core", "")).isEqualTo("Core");
      assertThat(CatalogRowBuilder.resolveCategoryTitle("web", "")).isEqualTo("Web");
      assertThat(CatalogRowBuilder.resolveCategoryTitle("data", "")).isEqualTo("Data");
    }

    @Test
    void unknownCategoryKeyFallsBackToTitleCase() {
      assertThat(CatalogRowBuilder.resolveCategoryTitle("unknown", "custom category"))
          .isEqualTo("Custom category");
    }

    @Test
    void unknownCategoryKeyUsesNormalizedKeyNotRawCase() {
      assertThat(CatalogRowBuilder.resolveCategoryTitle("web services", "Web Services"))
          .isEqualTo("Web services");
      assertThat(CatalogRowBuilder.resolveCategoryTitle("web services", "web services"))
          .isEqualTo("Web services");
    }

    @Test
    void blankCategoryResolvesToOther() {
      assertThat(CatalogRowBuilder.resolveCategoryTitle("unknown", "")).isEqualTo("Other");
    }
  }

  @Nested
  class IndexBuilders {
    @Test
    void selectableIndexesExcludeSectionHeaders() {
      List<ExtensionCatalogRow> rows =
          List.of(
              ExtensionCatalogRow.section("Web"),
              ExtensionCatalogRow.item(item("a:b", "ExtA", "Web")),
              ExtensionCatalogRow.section("Data"),
              ExtensionCatalogRow.item(item("a:c", "ExtB", "Data")));

      List<Integer> selectable = CatalogRowBuilder.buildSelectableIndexes(rows);
      assertThat(selectable).containsExactly(1, 3);
    }

    @Test
    void allRowIndexesIncludesEverything() {
      List<ExtensionCatalogRow> rows =
          List.of(
              ExtensionCatalogRow.section("Web"),
              ExtensionCatalogRow.item(item("a:b", "ExtA", "Web")));

      List<Integer> all = CatalogRowBuilder.buildAllRowIndexes(rows);
      assertThat(all).containsExactly(0, 1);
    }

    @Test
    void rowIndexByExtensionIdMapsCorrectly() {
      List<ExtensionCatalogRow> rows =
          List.of(
              ExtensionCatalogRow.section("Web"),
              ExtensionCatalogRow.item(item("a:b", "ExtA", "Web")),
              ExtensionCatalogRow.item(item("a:c", "ExtB", "Web")));

      var index = CatalogRowBuilder.buildRowIndexByExtensionId(rows);
      assertThat(index).containsEntry("a:b", 1).containsEntry("a:c", 2);
    }
  }

  @Test
  void availableCategoryTitlesPreservesInsertionOrder() {
    List<ExtensionCatalogItem> items =
        List.of(
            item("a:b", "ExtA", "Data"), item("a:c", "ExtB", "Web"), item("a:d", "ExtC", "Data"));

    Set<String> titles = CatalogRowBuilder.availableCategoryTitles(items);
    assertThat(titles).containsExactly("Data", "Web");
  }

  @Test
  void recentItemsNotShownWhenPresetActive() {
    List<ExtensionCatalogItem> items =
        List.of(item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Core"));

    List<ExtensionCatalogRow> rows =
        CatalogRowBuilder.buildRows(
            items, Set.of(), List.of("a:b"), "", false, false, "some-preset");

    assertThat(
            rows.stream().noneMatch(r -> CatalogRowBuilder.RECENT_SECTION_TITLE.equals(r.label())))
        .isTrue();
  }

  @Test
  void resolveCategoryTitleHandlesSingleCharCategory() {
    assertThat(CatalogRowBuilder.resolveCategoryTitle("x", "x")).isEqualTo("X");
  }

  @Test
  void collapsedCategoryHeaderShowsHiddenAndTotalCount() {
    List<ExtensionCatalogItem> items =
        List.of(
            item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Web"), item("a:d", "ExtC", "Core"));

    List<ExtensionCatalogRow> rows =
        CatalogRowBuilder.buildRows(items, Set.of("Web"), List.of(), "", false, false, "");

    ExtensionCatalogRow webHeader =
        rows.stream()
            .filter(r -> r.isSectionHeader() && "Web".equals(r.label()))
            .findFirst()
            .orElseThrow();

    assertThat(webHeader.collapsed()).isTrue();
    assertThat(webHeader.hiddenCount()).isEqualTo(2);
    assertThat(webHeader.totalCount()).isEqualTo(2);
  }

  @Test
  void interleavedSameCategoryRendersSingleSectionHeader() {
    List<ExtensionCatalogItem> items =
        List.of(
            item("a:web-1", "Web One", "Web"),
            item("a:data-1", "Data One", "Data"),
            item("a:web-2", "Web Two", "Web"));

    List<ExtensionCatalogRow> rows =
        CatalogRowBuilder.buildRows(items, Set.of(), List.of(), "we", false, false, "");

    assertThat(
            rows.stream()
                .filter(ExtensionCatalogRow::isSectionHeader)
                .map(ExtensionCatalogRow::label))
        .containsExactly("Web", "Data");
    assertThat(rows.stream().filter(r -> r.isSectionHeader() && "Web".equals(r.label())).count())
        .isEqualTo(1);
  }

  @Test
  void recentItemsNotFoundInCatalogAreSkipped() {
    List<ExtensionCatalogItem> items = List.of(item("a:b", "ExtA", "Web"));

    List<ExtensionCatalogRow> rows =
        CatalogRowBuilder.buildRows(
            items, Set.of(), List.of("nonexistent-id"), "", false, false, "");

    assertThat(
            rows.stream().noneMatch(r -> CatalogRowBuilder.RECENT_SECTION_TITLE.equals(r.label())))
        .isTrue();
  }

  @Test
  void uncollapsedCategorySectionHeaderShowsZeroHiddenCount() {
    List<ExtensionCatalogItem> items =
        List.of(item("a:b", "ExtA", "Web"), item("a:c", "ExtB", "Web"));

    List<ExtensionCatalogRow> rows =
        CatalogRowBuilder.buildRows(items, Set.of(), List.of(), "", false, false, "");

    ExtensionCatalogRow webHeader =
        rows.stream()
            .filter(r -> r.isSectionHeader() && "Web".equals(r.label()))
            .findFirst()
            .orElseThrow();

    assertThat(webHeader.collapsed()).isFalse();
    assertThat(webHeader.hiddenCount()).isZero();
  }
}
