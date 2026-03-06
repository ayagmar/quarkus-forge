package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.list.ListState;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ExtensionCatalogNavigation {
  private final ListState listState = new ListState();
  private final Set<String> selectedExtensionIds = new LinkedHashSet<>();

  ListState listState() {
    return listState;
  }

  Integer selectedRow() {
    return listState.selected();
  }

  List<String> selectedExtensionIds() {
    return List.copyOf(selectedExtensionIds);
  }

  Set<String> selectedIdsView() {
    return Collections.unmodifiableSet(selectedExtensionIds);
  }

  int selectedExtensionCount() {
    return selectedExtensionIds.size();
  }

  int clearSelectedExtensions() {
    int clearedCount = selectedExtensionIds.size();
    if (clearedCount > 0) {
      selectedExtensionIds.clear();
    }
    return clearedCount;
  }

  void retainAvailableSelections(Set<String> availableExtensionIds) {
    Objects.requireNonNull(availableExtensionIds);
    selectedExtensionIds.retainAll(availableExtensionIds);
  }

  boolean isSelected(String extensionId) {
    return selectedExtensionIds.contains(extensionId);
  }

  boolean select(String extensionId) {
    return selectedExtensionIds.add(extensionId);
  }

  boolean deselect(String extensionId) {
    return selectedExtensionIds.remove(extensionId);
  }

  void restoreSelection(ExtensionCatalogRows rows, String previouslyFocusedExtensionId) {
    if (rows.allRowIndexes().isEmpty()) {
      listState.select(null);
      return;
    }

    if (rows.selectableRowIndexes().isEmpty()) {
      listState.select(rows.allRowIndexes().getFirst());
      return;
    }

    if (previouslyFocusedExtensionId != null) {
      Integer restoredIndex = rows.rowIndexByExtensionId(previouslyFocusedExtensionId);
      if (restoredIndex != null) {
        listState.select(restoredIndex);
        return;
      }

      int firstSelectable = rows.selectableRowIndexes().getFirst();
      if (selectFirstVisibleSectionHeader(rows, firstSelectable)) {
        return;
      }
    }

    int firstSelectable = rows.selectableRowIndexes().getFirst();
    if (previouslyFocusedExtensionId == null
        && selectFirstVisibleSectionHeader(rows, firstSelectable)) {
      return;
    }

    listState.select(firstSelectable);
  }

  boolean isSelectionAtTop(ExtensionCatalogRows rows) {
    Integer selected = listState.selected();
    List<Integer> navigationRowIndexes = rows.navigationRowIndexes(selected);
    if (selected == null || navigationRowIndexes.isEmpty()) {
      return true;
    }
    return selected <= navigationRowIndexes.getFirst();
  }

  JumpToFavoriteResult jumpToFavorite(ExtensionCatalogRows rows, Set<String> favoriteExtensionIds) {
    if (rows.selectableRowIndexes().isEmpty()) {
      return JumpToFavoriteResult.none();
    }
    List<Integer> favoriteRows = rows.favoriteRowIndexes(favoriteExtensionIds);
    if (favoriteRows.isEmpty()) {
      return JumpToFavoriteResult.none();
    }

    Integer currentRow = listState.selected();
    int nextFavoriteRow = favoriteRows.getFirst();
    if (currentRow != null) {
      for (Integer favoriteRow : favoriteRows) {
        if (favoriteRow > currentRow) {
          nextFavoriteRow = favoriteRow;
          break;
        }
      }
    }

    listState.select(nextFavoriteRow);
    ExtensionCatalogItem focusedItem = rows.itemAtRow(nextFavoriteRow);
    return focusedItem == null
        ? JumpToFavoriteResult.none()
        : new JumpToFavoriteResult(true, focusedItem.name());
  }

  SectionJumpResult jumpToAdjacentSection(ExtensionCatalogRows rows, boolean forward) {
    if (rows.filteredRows().isEmpty()) {
      return SectionJumpResult.none();
    }
    Integer currentSelection = listState.selected();
    int start =
        currentSelection == null ? (forward ? -1 : rows.filteredRows().size()) : currentSelection;
    int step = forward ? 1 : -1;
    for (int i = start + step; i >= 0 && i < rows.filteredRows().size(); i += step) {
      ExtensionCatalogRow row = rows.filteredRows().get(i);
      if (!row.isSectionHeader()) {
        continue;
      }
      if (CatalogRowBuilder.RECENT_SECTION_TITLE.equals(row.label())) {
        continue;
      }
      listState.select(i);
      return new SectionJumpResult(true, row.label());
    }
    return SectionJumpResult.none();
  }

  SectionFocusResult focusParentSectionHeader(ExtensionCatalogRows rows) {
    Integer selectedRow = listState.selected();
    if (selectedRow == null || selectedRow <= 0 || selectedRow >= rows.filteredRows().size()) {
      return SectionFocusResult.none();
    }
    if (rows.filteredRows().get(selectedRow).isSectionHeader()) {
      return SectionFocusResult.none();
    }
    for (int i = selectedRow - 1; i >= 0; i--) {
      ExtensionCatalogRow row = rows.filteredRows().get(i);
      if (!row.isSectionHeader()) {
        continue;
      }
      listState.select(i);
      return new SectionFocusResult(true, row.label());
    }
    return SectionFocusResult.none();
  }

  SectionFocusResult focusFirstVisibleItemInSelectedSection(ExtensionCatalogRows rows) {
    Integer selectedRow = listState.selected();
    if (selectedRow == null
        || selectedRow < 0
        || selectedRow >= rows.filteredRows().size()
        || !rows.filteredRows().get(selectedRow).isSectionHeader()) {
      return SectionFocusResult.none();
    }
    int childIndex = selectedRow + 1;
    if (childIndex >= rows.filteredRows().size()) {
      return SectionFocusResult.none();
    }
    ExtensionCatalogRow childRow = rows.filteredRows().get(childIndex);
    if (childRow.isSectionHeader()) {
      return SectionFocusResult.none();
    }
    listState.select(childIndex);
    return new SectionFocusResult(true, rows.filteredRows().get(selectedRow).label());
  }

  boolean handleNavigationKey(ExtensionCatalogRows rows, KeyEvent keyEvent) {
    Objects.requireNonNull(keyEvent);
    List<Integer> navigationRowIndexes = rows.navigationRowIndexes(listState.selected());
    if (navigationRowIndexes.isEmpty()) {
      return false;
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      selectPrevious(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      selectNext(navigationRowIndexes);
      return true;
    }
    if (keyEvent.isHome() || UiKeyMatchers.isVimHomeKey(keyEvent)) {
      listState.select(navigationRowIndexes.getFirst());
      return true;
    }
    if (keyEvent.isEnd() || UiKeyMatchers.isVimEndKey(keyEvent)) {
      listState.select(navigationRowIndexes.getLast());
      return true;
    }
    return false;
  }

  private void selectPrevious(List<Integer> navigationRowIndexes) {
    int currentPosition = selectedPosition(navigationRowIndexes);
    if (currentPosition <= 0) {
      listState.select(navigationRowIndexes.getFirst());
      return;
    }
    listState.select(navigationRowIndexes.get(currentPosition - 1));
  }

  private void selectNext(List<Integer> navigationRowIndexes) {
    int currentPosition = selectedPosition(navigationRowIndexes);
    int nextPosition = Math.min(currentPosition + 1, navigationRowIndexes.size() - 1);
    listState.select(navigationRowIndexes.get(nextPosition));
  }

  private int selectedPosition(List<Integer> navigationRowIndexes) {
    Integer selected = listState.selected();
    if (selected == null) {
      return -1;
    }
    int position = navigationRowIndexes.indexOf(selected);
    if (position >= 0) {
      return position;
    }

    int nearestPreviousSelectable = -1;
    for (int i = 0; i < navigationRowIndexes.size(); i++) {
      if (navigationRowIndexes.get(i) <= selected) {
        nearestPreviousSelectable = i;
        continue;
      }
      break;
    }
    return nearestPreviousSelectable;
  }

  private boolean selectFirstVisibleSectionHeader(ExtensionCatalogRows rows, int firstSelectable) {
    if (firstSelectable <= 1) {
      return false;
    }
    Integer firstSectionHeader = rows.firstNonRecentSectionHeaderIndex();
    if (firstSectionHeader == null) {
      return false;
    }
    listState.select(firstSectionHeader);
    return true;
  }
}
