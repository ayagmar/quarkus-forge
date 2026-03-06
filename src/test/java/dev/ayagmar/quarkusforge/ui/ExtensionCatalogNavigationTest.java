package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tamboui.tui.event.KeyEvent;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionCatalogNavigationTest {
  private static final ExtensionCatalogItem REST =
      new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 10);
  private static final ExtensionCatalogItem JDBC =
      new ExtensionCatalogItem("io.quarkus:quarkus-jdbc-postgresql", "JDBC", "jdbc", "Data", 20);

  @Test
  void restoreSelectionHandlesEmptyCollapsedAndFocusedRows() {
    ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
    ExtensionCatalogRows emptyRows = new ExtensionCatalogRows();

    navigation.restoreSelection(emptyRows, null);
    assertThat(navigation.selectedRow()).isNull();

    ExtensionCatalogRows collapsedRows = rows(true);
    navigation.restoreSelection(collapsedRows, null);
    assertThat(navigation.selectedRow()).isEqualTo(1);

    ExtensionCatalogRows normalRows = rows(false);
    navigation.restoreSelection(normalRows, REST.id());
    assertThat(navigation.selectedRow()).isEqualTo(3);

    navigation.restoreSelection(normalRows, "missing");
    assertThat(navigation.selectedRow()).isEqualTo(1);
  }

  @Test
  void selectionMutatorsAndFavoriteJumpBehaveConsistently() {
    ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
    ExtensionCatalogRows rows = rows(false);

    assertThat(navigation.select(REST.id())).isTrue();
    assertThat(navigation.select(REST.id())).isFalse();
    assertThat(navigation.isSelected(REST.id())).isTrue();
    assertThat(navigation.selectedExtensionIds()).containsExactly(REST.id());
    assertThat(navigation.selectedExtensionCount()).isEqualTo(1);
    assertThat(navigation.deselect(REST.id())).isTrue();
    assertThat(navigation.clearSelectedExtensions()).isZero();

    navigation.listState().select(1);
    JumpToFavoriteResult jumped = navigation.jumpToFavorite(rows, Set.of(JDBC.id()));
    assertThat(jumped.jumped()).isTrue();
    assertThat(jumped.extensionName()).isEqualTo("JDBC");
    assertThat(navigation.selectedRow()).isEqualTo(5);

    assertThat(navigation.jumpToFavorite(rows, Set.of("missing")).jumped()).isFalse();
  }

  @Test
  void selectedIdsViewIsReadOnlyAndRetainAvailableSelectionsPrunesState() {
    ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();

    navigation.select(REST.id());
    navigation.select(JDBC.id());

    assertThatThrownBy(() -> navigation.selectedIdsView().remove(REST.id()))
        .isInstanceOf(UnsupportedOperationException.class);

    navigation.retainAvailableSelections(Set.of(JDBC.id()));

    assertThat(navigation.selectedExtensionIds()).containsExactly(JDBC.id());
  }

  @Test
  void sectionAndParentNavigationSkipRecentRows() {
    ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
    ExtensionCatalogRows rows = rows(false);

    SectionJumpResult forward = navigation.jumpToAdjacentSection(rows, true);
    assertThat(forward.moved()).isTrue();
    assertThat(forward.categoryTitle()).isEqualTo("Web");
    assertThat(navigation.selectedRow()).isEqualTo(2);

    assertThat(navigation.jumpToAdjacentSection(rows, false).moved()).isFalse();

    navigation.listState().select(5);
    SectionFocusResult parent = navigation.focusParentSectionHeader(rows);
    assertThat(parent.moved()).isTrue();
    assertThat(parent.sectionTitle()).isEqualTo("Data");
    assertThat(navigation.focusFirstVisibleItemInSelectedSection(rows).moved()).isTrue();
    assertThat(navigation.selectedRow()).isEqualTo(5);

    navigation.listState().select(5);
    assertThat(navigation.focusFirstVisibleItemInSelectedSection(rows).moved()).isFalse();
  }

  @Test
  void keyboardNavigationUsesSectionHeadersWhenFocusedAndSelectableRowsOtherwise() {
    ExtensionCatalogNavigation navigation = new ExtensionCatalogNavigation();
    ExtensionCatalogRows rows = rows(false);

    assertThat(navigation.handleNavigationKey(rows, KeyEvent.ofChar('j'))).isTrue();
    assertThat(navigation.selectedRow()).isEqualTo(1);
    assertThat(navigation.isSelectionAtTop(rows)).isTrue();

    assertThat(navigation.handleNavigationKey(rows, KeyEvent.ofChar('G'))).isTrue();
    assertThat(navigation.selectedRow()).isEqualTo(5);
    assertThat(navigation.isSelectionAtTop(rows)).isFalse();

    navigation.listState().select(2);
    assertThat(navigation.handleNavigationKey(rows, KeyEvent.ofChar('g'))).isTrue();
    assertThat(navigation.selectedRow()).isZero();

    assertThat(navigation.handleNavigationKey(rows, KeyEvent.ofChar('x'))).isFalse();
  }

  private static ExtensionCatalogRows rows(boolean collapseWeb) {
    ExtensionCatalogRows rows = new ExtensionCatalogRows();
    ExtensionCatalogFilters filters = new ExtensionCatalogFilters("");
    rows.update(List.of(REST, JDBC), List.of(REST.id()), filters);
    if (collapseWeb) {
      rows.toggleCategoryCollapse("Web");
      rows.update(List.of(REST, JDBC), List.of(REST.id()), filters);
    }
    return rows;
  }
}
