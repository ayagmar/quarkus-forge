package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.ResizeEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CoreTuiShellPilotTest {
  @Test
  void focusTraversalCyclesWithTabAndShiftTab() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void listNavigationAndSpaceToggleAreRoutedWhenListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    CoreTuiController.UiAction downAction = controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    CoreTuiController.UiAction toggleAction = controller.onEvent(KeyEvent.ofChar(' '));

    assertThat(downAction.handled()).isTrue();
    assertThat(toggleAction.handled()).isTrue();
    assertThat(controller.selectedExtensionIds()).hasSize(1);
  }

  @Test
  void vimListMotionsJAndKNavigateCatalogRows() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    String firstId = controller.focusedListExtensionId();

    controller.onEvent(KeyEvent.ofChar('j'));
    String secondId = controller.focusedListExtensionId();
    controller.onEvent(KeyEvent.ofChar('k'));

    assertThat(secondId).isNotEqualTo(firstId);
    assertThat(controller.focusedListExtensionId()).isEqualTo(firstId);
  }

  @Test
  void enterSubmitsWhenValidAndBlocksWhenValidationFails() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.statusMessage()).contains("Submit requested");

    moveFocusTo(controller, FocusTarget.GROUP_ID);
    for (int i = 0; i < 30; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.statusMessage()).contains("Submit blocked");
  }

  @Test
  void altGSubmitsWithoutChangingFocusedField() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    String originalGroupId = controller.request().groupId();

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('g', KeyModifiers.ALT));

    assertThat(action.handled()).isTrue();
    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(controller.request().groupId()).isEqualTo(originalGroupId);
    assertThat(controller.statusMessage()).contains("Submit requested");
  }

  @Test
  void enterSubmitsInsteadOfTogglingWhenExtensionListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.selectedExtensionIds()).isEmpty();
    assertThat(controller.statusMessage()).contains("Submit requested");
  }

  @Test
  void fixingInputWithoutChangingFocusClearsBlockedSubmitErrorFromFooter() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofChar('X'));
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(renderToString(controller)).contains("Error:");

    controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));

    assertThat(controller.validation().isValid()).isTrue();
    assertThat(controller.statusMessage()).contains("Validation restored");
    assertThat(renderToString(controller)).doesNotContain("Error:");
  }

  @Test
  void blockedSubmitFeedbackRecoversEvenIfStatusMessageChangesBeforeFix() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofChar('X'));
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(renderToString(controller)).contains("Error:");

    controller.onEvent(ResizeEvent.of(90, 30));
    controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));

    assertThat(controller.validation().isValid()).isTrue();
    assertThat(controller.statusMessage()).contains("Validation restored");
    assertThat(renderToString(controller)).doesNotContain("Error:");
  }

  @Test
  void slashShortcutJumpsFocusToExtensionSearch() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofChar('/'));

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(controller.statusMessage()).contains("Focus moved to extensionSearch");
  }

  @Test
  void questionMarkTogglesHelpOverlay() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofChar('?'));
    String opened = renderToString(controller);
    assertThat(opened).contains("Help [focus]");
    assertThat(opened).contains("Global");
    assertThat(controller.helpOverlayVisible()).isTrue();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    String closed = renderToString(controller);
    assertThat(closed).doesNotContain("Help [focus]");
    assertThat(controller.helpOverlayVisible()).isFalse();
  }

  @Test
  void ctrlPTogglesCommandPaletteOverlay() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(KeyEvent.ofChar('p', KeyModifiers.CTRL));
    String opened = renderToString(controller);
    assertThat(opened).contains("Command Palette [focus]");
    assertThat(opened).contains("Focus extension search");
    assertThat(controller.commandPaletteVisible()).isTrue();

    controller.onEvent(KeyEvent.ofChar('p', KeyModifiers.CTRL));
    String closed = renderToString(controller);
    assertThat(closed).doesNotContain("Command Palette [focus]");
    assertThat(controller.commandPaletteVisible()).isFalse();
  }

  @Test
  void commandPaletteRunsSelectedAction() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofChar('p', KeyModifiers.CTRL));
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.commandPaletteVisible()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.statusMessage()).contains("Focus moved to extensionList");
  }

  @Test
  void slashIsInsertedInOutputDirectoryWithoutStealingFocus() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.OUTPUT_DIR);

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('/'));

    assertThat(action.handled()).isTrue();
    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.OUTPUT_DIR);
    assertThat(controller.request().outputDirectory()).endsWith("/");
  }

  @Test
  void questionMarkIsInsertedInGroupIdWithoutOpeningHelp() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofChar('?'));

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(controller.request().groupId()).endsWith("?");
    assertThat(controller.helpOverlayVisible()).isFalse();
  }

  @Test
  void ctrlFAndCtrlLShortcutsJumpBetweenSearchAndList() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    controller.onEvent(KeyEvent.ofChar('f', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofChar('l', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
  }

  @Test
  void submitFocusSupportsVimJkTraversal() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofChar('k'));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    moveFocusTo(controller, FocusTarget.SUBMIT);
    controller.onEvent(KeyEvent.ofChar('j'));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void searchAndListSupportDirectArrowHandoff() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.UP));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
  }

  @Test
  void searchInputTitleShowsLiveMatchCounters() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    assertThat(renderToString(controller)).contains("Search Extensions (7/7)");

    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(renderToString(controller)).contains("Search Extensions (1/7)");
  }

  @Test
  void escapeClearsExtensionSearchBeforeQuitWhenSearchInputIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);

    CoreTuiController.UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(controller.statusMessage()).contains("Extension search cleared");

    CoreTuiController.UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    CoreTuiController.UiAction thirdEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(thirdEscape.handled()).isTrue();
    assertThat(thirdEscape.shouldQuit()).isTrue();
  }

  @Test
  void escapeClearsExtensionSearchBeforeQuitWhenListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    CoreTuiController.UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(controller.statusMessage()).contains("Extension search cleared");

    CoreTuiController.UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isTrue();
  }

  @Test
  void escapeDisablesFavoritesFilterBeforeQuitWhenListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    assertThat(controller.favoritesOnlyFilterEnabled()).isTrue();

    CoreTuiController.UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.favoritesOnlyFilterEnabled()).isFalse();
    assertThat(controller.statusMessage()).contains("Favorites filter disabled");

    CoreTuiController.UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isTrue();
  }

  @Test
  void xClearsSelectedExtensionsWhenListIsFocused() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar(' '));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(controller.selectedExtensionIds()).hasSize(2);

    CoreTuiController.UiAction clearAction = controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(clearAction.handled()).isTrue();
    assertThat(clearAction.shouldQuit()).isFalse();
    assertThat(controller.selectedExtensionIds()).isEmpty();
    assertThat(controller.statusMessage()).contains("Cleared 2 selected extensions");

    controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(controller.statusMessage()).contains("No selected extensions to clear");
  }

  @Test
  void vCyclesCategoryFilterAcrossVisibleCategories() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));
    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web", "Data");

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core");
    assertThat(controller.statusMessage()).contains("Category filter: Core");

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Web");
    assertThat(controller.statusMessage()).contains("Category filter: Web");

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Data");
    assertThat(controller.statusMessage()).contains("Category filter: Data");
    assertThat(renderToString(controller)).contains("Category filter: Data");

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web", "Data");
    assertThat(controller.statusMessage()).contains("Category filter cleared");
  }

  @Test
  void qNoLongerTriggersQuitByDefault() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('q'));

    assertThat(action.shouldQuit()).isFalse();
    assertThat(action.handled()).isTrue();
    assertThat(controller.request().groupId()).endsWith("q");
  }

  @Test
  void ctrlCStillQuitsFromShell() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());

    CoreTuiController.UiAction action = controller.onEvent(KeyEvent.ofChar('c', KeyModifiers.CTRL));

    assertThat(action.shouldQuit()).isTrue();
    assertThat(action.handled()).isTrue();
  }

  @Test
  void listNavigationSkipsSectionHeadersAcrossCategoryBoundaries() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-rest");

    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-jdbc-postgresql");

    controller.onEvent(KeyEvent.ofKey(KeyCode.UP));
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-rest");

    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(controller.selectedExtensionIds()).containsExactly("io.quarkus:quarkus-rest");
  }

  @Test
  void favoriteQuickActionsToggleJumpAndFilterRemainDeterministic() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20)))));

    controller.onEvent(KeyEvent.ofChar('j', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.statusMessage()).contains("No favorite extension");

    controller.onEvent(KeyEvent.ofChar('f'));
    assertThat(controller.favoriteExtensionCount()).isEqualTo(1);

    moveFocusTo(controller, FocusTarget.GROUP_ID);
    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    assertThat(controller.favoritesOnlyFilterEnabled()).isTrue();
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    assertThat(controller.favoritesOnlyFilterEnabled()).isFalse();
    assertThat(controller.filteredExtensionCount()).isEqualTo(2);

    moveFocusTo(controller, FocusTarget.GROUP_ID);
    controller.onEvent(KeyEvent.ofChar('j', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
  }

  @Test
  void categoryCloseAndOpenAllKeysWorkWhileBrowsingList() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.focusedListExtensionId()).isEmpty();
    assertThat(controller.statusMessage()).contains("Closed category: Core");
    assertThat(renderToString(controller)).contains("[+] Core (1 hidden)");
    assertThat(renderToString(controller)).doesNotContain("CDI");

    controller.onEvent(KeyEvent.ofChar('C'));

    assertThat(controller.statusMessage()).contains("Opened 1 category");
    assertThat(renderToString(controller)).contains("CDI");
  }

  @Test
  void leftAndRightSupportSectionHierarchyNavigation() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofKey(KeyCode.LEFT));
    assertThat(controller.focusedListExtensionId()).isEmpty();
    assertThat(controller.statusMessage()).contains("Moved to section: Core");

    controller.onEvent(KeyEvent.ofChar('l'));
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
    assertThat(controller.statusMessage()).contains("Moved to first item in section: Core");

    controller.onEvent(KeyEvent.ofChar('h'));
    controller.onEvent(KeyEvent.ofKey(KeyCode.LEFT));
    assertThat(controller.statusMessage()).contains("Closed category: Core");
    assertThat(renderToString(controller)).contains("[+] Core (1 hidden)");

    controller.onEvent(KeyEvent.ofKey(KeyCode.RIGHT));
    assertThat(controller.statusMessage()).contains("Opened category: Core");
    assertThat(renderToString(controller)).contains("CDI");
  }

  @Test
  void spaceOnCategoryHeaderReopensClosedCategory() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(renderToString(controller)).contains("[+] Core (1 hidden)");

    controller.onEvent(KeyEvent.ofChar(' '));

    assertThat(controller.statusMessage()).contains("Opened category: Core");
    assertThat(renderToString(controller)).contains("CDI");
  }

  @Test
  void pageUpAndPageDownJumpBetweenCategorySections() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
    assertThat(controller.statusMessage()).contains("Jumped to category: Web");
    assertThat(controller.focusedListExtensionId()).isEmpty();

    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.statusMessage()).contains("Closed category: Web");

    controller.onEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
    assertThat(controller.statusMessage()).contains("Jumped to category: Data");

    controller.onEvent(KeyEvent.ofKey(KeyCode.PAGE_UP));
    assertThat(controller.statusMessage()).contains("Jumped to category: Web");
  }

  @Test
  void categoryCloseCanBeUndoneWithSameKeyAndListNavigationRemainsPredictable() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.statusMessage()).contains("Closed category: Core");
    assertThat(controller.focusedListExtensionId()).isEmpty();
    assertThat(renderToString(controller)).contains("[+] Core (1 hidden)");

    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.statusMessage()).contains("Opened category: Core");
    assertThat(renderToString(controller)).contains("CDI");
    assertThat(renderToString(controller)).contains("[-] Core");

    controller.onEvent(KeyEvent.ofChar('j'));
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
  }

  @Test
  void allCollapsedCategoriesStillSupportHeaderNavigationAndReopen() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                CoreTuiController.ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar('c'));
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('c'));
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('c'));

    String allCollapsed = renderToString(controller);
    assertThat(allCollapsed).contains("[+] Core (1 hidden)");
    assertThat(allCollapsed).contains("[+] Web (1 hidden)");
    assertThat(allCollapsed).contains("[+] Data (1 hidden)");
    assertThat(allCollapsed).doesNotContain("CDI");
    assertThat(allCollapsed).doesNotContain("REST");
    assertThat(allCollapsed).doesNotContain("JDBC PostgreSQL");
    assertThat(controller.focusedListExtensionId()).isEmpty();

    controller.onEvent(KeyEvent.ofChar('k'));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.statusMessage()).contains("Opened category: Web");
    assertThat(renderToString(controller)).contains("REST");
  }

  @Test
  void metadataSelectorsCycleFromLoadedOptionsAndBlockFreeTextEdits() {
    CoreTuiController controller =
        CoreTuiController.from(UiTestFixtureFactory.defaultForgeUiState());
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                new CoreTuiController.ExtensionCatalogLoadResult(
                    List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")),
                    CatalogSource.LIVE,
                    false,
                    "",
                    selectorMetadata())));

    moveFocusTo(controller, FocusTarget.PLATFORM_STREAM);
    assertThat(controller.request().platformStream()).isEqualTo("io.quarkus.platform:3.31");
    assertThat(renderToString(controller)).contains("(*)");

    controller.onEvent(KeyEvent.ofChar('l'));
    assertThat(controller.request().platformStream()).isEqualTo("io.quarkus.platform:3.20");
    assertThat(controller.validation().isValid()).isFalse();
    assertThat(controller.validation().errors().getFirst().field()).isEqualTo("compatibility");

    moveFocusTo(controller, FocusTarget.JAVA_VERSION);
    controller.onEvent(KeyEvent.ofChar('h'));
    assertThat(controller.request().javaVersion()).isEqualTo("21");
    assertThat(controller.validation().isValid()).isTrue();

    moveFocusTo(controller, FocusTarget.BUILD_TOOL);
    String originalBuildTool = controller.request().buildTool();
    controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(controller.request().buildTool()).isEqualTo(originalBuildTool);

    controller.onEvent(KeyEvent.ofChar('l'));
    assertThat(controller.request().buildTool()).isEqualTo("gradle");
  }

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static String renderToString(CoreTuiController controller) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, 120, 32));
    Frame frame = Frame.forTesting(buffer);
    controller.render(frame);
    return buffer.toAnsiStringTrimmed();
  }

  private static MetadataDto selectorMetadata() {
    return new MetadataDto(
        List.of("17", "21", "25"),
        List.of("maven", "gradle"),
        Map.of("maven", List.of("17", "21", "25"), "gradle", List.of("21", "25")),
        List.of(
            new MetadataDto.PlatformStream(
                "io.quarkus.platform:3.31", "3.31", true, List.of("17", "21", "25")),
            new MetadataDto.PlatformStream(
                "io.quarkus.platform:3.20", "3.20", false, List.of("17", "21"))));
  }
}
