package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.CatalogSource;
import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import dev.tamboui.tui.event.ResizeEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CoreTuiShellPilotTest {
  @Test
  void focusTraversalCyclesWithTabAndShiftTab() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB, KeyModifiers.SHIFT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void listNavigationAndSpaceToggleAreRoutedWhenListIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    UiAction downAction = controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    UiAction toggleAction = controller.onEvent(KeyEvent.ofChar(' '));

    assertThat(downAction.handled()).isTrue();
    assertThat(toggleAction.handled()).isTrue();
    assertThat(controller.selectedExtensionIds()).hasSize(1);
  }

  @Test
  void altSTogglesSelectedOnlyQuickView() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(controller.selectedExtensionIds()).hasSize(1);

    controller.onEvent(KeyEvent.ofChar('s', KeyModifiers.ALT));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.statusMessage()).isEqualTo("Selected-only view enabled");
    assertThat(UiControllerTestHarness.renderToString(controller))
        .contains("Selected-only view")
        .contains("[sel]")
        .doesNotContain("Recently Selected");

    controller.onEvent(KeyEvent.ofChar('s', KeyModifiers.ALT));
    assertThat(controller.filteredExtensionCount()).isGreaterThan(1);
    assertThat(controller.statusMessage()).isEqualTo("Selected-only view disabled");
  }

  @Test
  void vimListMotionsJAndKNavigateCatalogRows() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    String firstId = controller.focusedListExtensionId();

    controller.onEvent(KeyEvent.ofChar('j'));
    String secondId = controller.focusedListExtensionId();
    controller.onEvent(KeyEvent.ofChar('k'));

    assertThat(secondId).isNotEqualTo(firstId);
    assertThat(controller.focusedListExtensionId()).isEqualTo(firstId);
  }

  @Test
  void enterSubmitsWhenValidAndBlocksWhenValidationFails() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.statusMessage()).contains("Submit requested");

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    for (int i = 0; i < 30; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.statusMessage()).contains("Submit blocked");
  }

  @Test
  void blockedSubmitMovesFocusToFirstInvalidField() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    for (int i = 0; i < 30; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.validation().isValid()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(controller.statusMessage()).contains("fix groupId");
  }

  @Test
  void submitButtonShowsBlockedLabelWhenValidationFails() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.ARTIFACT_ID);
    controller.onEvent(KeyEvent.ofChar('X'));
    assertThat(controller.validation().isValid()).isFalse();

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 34);

    assertThat(rendered).contains("Generate Project (blocked: 1 issue)");
  }

  @Test
  void altNAndAltPCycleInvalidFields() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    for (int i = 0; i < 30; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.ARTIFACT_ID);
    controller.onEvent(KeyEvent.ofChar('X'));
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofChar('n', KeyModifiers.ALT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofChar('n', KeyModifiers.ALT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofChar('p', KeyModifiers.ALT));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void footerShowsPreGeneratePlanLine() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 34);

    assertThat(rendered).contains("Plan:");
    assertThat(rendered).contains("| Java ");
    assertThat(rendered).contains("| 0 ext");
  }

  @Test
  void preGeneratePlanDoesNotCrashWhenOutputDirectoryIsInvalidPath() {
    MetadataCompatibilityContext metadata = MetadataCompatibilityContext.loadDefault();
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "\0invalid-output",
            "maven",
            "25");
    ForgeUiState state =
        new ForgeUiState(
            request,
            new ProjectRequestValidator().validate(request).merge(metadata.validate(request)),
            metadata);
    CoreTuiController controller =
        CoreTuiController.from(state, UiScheduler.immediate(), java.time.Duration.ZERO);

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 34);

    assertThat(rendered).contains("Plan:");
  }

  @Test
  void metadataPanelRendersOutputDirectoryAsAbsolutePath() {
    Path homePath = Path.of("target", "qf-home").toAbsolutePath().normalize();
    String expectedOutputPath = homePath.resolve("Projects").resolve("Quarkus").toString();
    String expectedOutputPrefix =
        expectedOutputPath.substring(0, Math.min(expectedOutputPath.length(), 48));
    String expectedPlanPath =
        homePath.resolve("Projects").resolve("Quarkus").resolve("forge-app").toString();
    withSystemProperty(
        "user.home",
        homePath.toString(),
        () -> {
          MetadataCompatibilityContext metadata = MetadataCompatibilityContext.loadDefault();
          ProjectRequest request =
              new ProjectRequest(
                  "com.example",
                  "forge-app",
                  "1.0.0-SNAPSHOT",
                  "com.example.forge.app",
                  "~/Projects/Quarkus",
                  "maven",
                  "25");
          ForgeUiState state =
              new ForgeUiState(
                  request,
                  new ProjectRequestValidator().validate(request).merge(metadata.validate(request)),
                  metadata);
          CoreTuiController controller =
              CoreTuiController.from(state, UiScheduler.immediate(), java.time.Duration.ZERO);

          String rendered = UiControllerTestHarness.renderToString(controller, 260, 34);

          assertThat(rendered).doesNotContain("Output: ~/Projects/Quarkus");
          assertThat(rendered).contains("Output: " + expectedOutputPrefix);
          assertThat(rendered).contains("Plan: " + expectedPlanPath);
        });
  }

  @Test
  void altGSubmitsWithoutChangingFocusedField() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    UiAction action = controller.onEvent(KeyEvent.ofChar('g', KeyModifiers.ALT));

    assertThat(action.handled()).isTrue();
    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(controller.statusMessage()).contains("Submit requested");
  }

  @Test
  void enterSubmitsInsteadOfTogglingWhenExtensionListIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.submitRequested()).isTrue();
    assertThat(controller.selectedExtensionIds()).isEmpty();
    assertThat(controller.statusMessage()).contains("Submit requested");
  }

  @Test
  void fixingInputWithoutChangingFocusClearsBlockedSubmitErrorFromFooter() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofChar('X'));
    assertThat(controller.validation().isValid()).isFalse();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Error:");

    controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));

    assertThat(controller.validation().isValid()).isTrue();
    assertThat(controller.statusMessage()).contains("Validation restored");
    assertThat(UiControllerTestHarness.renderToString(controller)).doesNotContain("Error:");
  }

  @Test
  void blockedSubmitFeedbackRecoversEvenIfStatusMessageChangesBeforeFix() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.ARTIFACT_ID);

    controller.onEvent(KeyEvent.ofChar('X'));
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Error:");

    controller.onEvent(ResizeEvent.of(90, 30));
    controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));

    assertThat(controller.validation().isValid()).isTrue();
    assertThat(controller.statusMessage()).contains("Validation restored");
    assertThat(UiControllerTestHarness.renderToString(controller)).doesNotContain("Error:");
  }

  @Test
  void slashShortcutJumpsFocusToExtensionSearch() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofChar('/'));

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(controller.statusMessage()).contains("Focus moved to extensionSearch");
  }

  @Test
  void questionMarkTogglesHelpOverlay() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofChar('?'));
    String opened = UiControllerTestHarness.renderToString(controller);
    assertThat(opened).contains("Help [focus]");
    assertThat(opened).contains("Global");
    assertThat(opened).contains("Help [focus] - submit");
    assertThat(controller.helpOverlayVisible()).isTrue();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    String closed = UiControllerTestHarness.renderToString(controller);
    assertThat(closed).doesNotContain("Help [focus]");
    assertThat(controller.helpOverlayVisible()).isFalse();
  }

  @Test
  void questionMarkOpensHelpWhenExtensionSearchIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofChar('?'));

    assertThat(controller.helpOverlayVisible()).isTrue();
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
  }

  @Test
  void ctrlPTogglesCommandPaletteOverlay() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    controller.onEvent(KeyEvent.ofChar('p', KeyModifiers.CTRL));
    String opened = UiControllerTestHarness.renderToString(controller);
    assertThat(opened).contains("Command Palette [focus]");
    assertThat(opened).contains("Focus extension search");
    assertThat(controller.commandPaletteVisible()).isTrue();

    controller.onEvent(KeyEvent.ofChar('p', KeyModifiers.CTRL));
    String closed = UiControllerTestHarness.renderToString(controller);
    assertThat(closed).doesNotContain("Command Palette [focus]");
    assertThat(controller.commandPaletteVisible()).isFalse();
  }

  @Test
  void commandPaletteRunsSelectedAction() {
    CoreTuiController controller = UiControllerTestHarness.controller();
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
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.OUTPUT_DIR);

    UiAction action = controller.onEvent(KeyEvent.ofChar('/'));

    assertThat(action.handled()).isTrue();
    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.OUTPUT_DIR);
    assertThat(controller.request().outputDirectory()).endsWith("/");
  }

  @Test
  void questionMarkIsInsertedInGroupIdWithoutOpeningHelp() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    controller.onEvent(KeyEvent.ofChar('?'));

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(controller.request().groupId()).endsWith("?");
    assertThat(controller.helpOverlayVisible()).isFalse();
  }

  @Test
  void ctrlFAndCtrlLShortcutsJumpBetweenSearchAndList() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    controller.onEvent(KeyEvent.ofChar('f', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofChar('l', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
  }

  @Test
  void submitFocusSupportsVimJkTraversal() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.SUBMIT);

    controller.onEvent(KeyEvent.ofChar('k'));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.SUBMIT);
    controller.onEvent(KeyEvent.ofChar('j'));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);
  }

  @Test
  void vimCharsAreInsertedAsTextAcrossAllTextInputs() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    List<FocusTarget> textInputs =
        List.of(
            FocusTarget.GROUP_ID,
            FocusTarget.ARTIFACT_ID,
            FocusTarget.VERSION,
            FocusTarget.PACKAGE_NAME,
            FocusTarget.OUTPUT_DIR,
            FocusTarget.EXTENSION_SEARCH);
    List<Character> vimChars = List.of('h', 'j', 'k', 'l', 'g', 'G');

    for (FocusTarget target : textInputs) {
      UiControllerTestHarness.moveFocusTo(controller, target);
      for (char character : vimChars) {
        controller.onEvent(KeyEvent.ofChar(character));
      }
    }

    assertThat(controller.request().groupId()).endsWith("hjklgG");
    assertThat(controller.request().artifactId()).endsWith("hjklgG");
    assertThat(controller.request().version()).endsWith("hjklgG");
    assertThat(controller.request().packageName()).endsWith("hjklgG");
    assertThat(controller.request().outputDirectory()).endsWith("hjklgG");
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .contains("Search: [hjklgG");
  }

  @Test
  void searchAndListSupportDirectArrowHandoff() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.UP));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
  }

  @Test
  void searchInputTitleShowsLiveMatchCounters() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    assertThat(UiControllerTestHarness.renderToString(controller)).contains("7 shown");

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(UiControllerTestHarness.renderToString(controller)).contains("1 shown");
  }

  @Test
  void escapeClearsExtensionSearchBeforeQuitWhenSearchInputIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);

    UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(controller.statusMessage()).contains("Extension search cleared");

    UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    UiAction thirdEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(thirdEscape.handled()).isTrue();
    assertThat(thirdEscape.shouldQuit()).isTrue();
  }

  @Test
  void escapeClearsExtensionSearchBeforeQuitWhenListIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    assertThat(controller.statusMessage()).contains("Extension search cleared");

    UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isTrue();
  }

  @Test
  void escapeDisablesFavoritesFilterBeforeQuitWhenListIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    assertThat(controller.favoritesOnlyFilterEnabled()).isTrue();

    UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.favoritesOnlyFilterEnabled()).isFalse();
    assertThat(controller.statusMessage()).contains("Favorites filter disabled");

    UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isTrue();
  }

  @Test
  void escapeClearsCategoryFilterBeforeQuitWhenListIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core");

    UiAction firstEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(firstEscape.handled()).isTrue();
    assertThat(firstEscape.shouldQuit()).isFalse();
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web", "Data");
    assertThat(controller.statusMessage()).contains("Category filter cleared");

    UiAction secondEscape = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(secondEscape.handled()).isTrue();
    assertThat(secondEscape.shouldQuit()).isTrue();
  }

  @Test
  void extensionEscapeUnwindsInStrictPriorityOrderBeforeQuit() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                new ExtensionCatalogLoadResult(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20)),
                    CatalogSource.LIVE,
                    false,
                    "",
                    null,
                    Map.of(
                        "core", List.of("io.quarkus:quarkus-arc"),
                        "web", List.of("io.quarkus:quarkus-rest")))));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar('f'));
    controller.onEvent(KeyEvent.ofChar(' '));
    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    controller.onEvent(KeyEvent.ofChar('s', KeyModifiers.ALT));
    controller.onEvent(KeyEvent.ofChar('y', KeyModifiers.CTRL));
    controller.onEvent(KeyEvent.ofChar('v'));
    controller.onEvent(KeyEvent.ofChar('f', KeyModifiers.CTRL));
    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Search: [c");
    assertThat(controller.uiState().extensions().searchQuery()).isEqualTo("c");
    assertThat(controller.uiState().extensions().favoritesOnlyEnabled()).isTrue();
    assertThat(controller.uiState().extensions().selectedOnlyEnabled()).isTrue();
    assertThat(controller.uiState().extensions().activePresetFilterName()).isNotBlank();
    assertThat(controller.uiState().extensions().activeCategoryFilterTitle()).isNotBlank();

    UiAction clearSearch = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(clearSearch.handled()).isTrue();
    assertThat(clearSearch.shouldQuit()).isFalse();
    assertThat(controller.statusMessage()).contains("Extension search cleared");
    assertThat(controller.uiState().extensions().searchQuery()).isEmpty();

    UiAction disableFavorites = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(disableFavorites.handled()).isTrue();
    assertThat(disableFavorites.shouldQuit()).isFalse();
    assertThat(controller.statusMessage()).contains("Favorites filter disabled");
    assertThat(controller.favoritesOnlyFilterEnabled()).isFalse();

    UiAction disableSelectedOnly = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(disableSelectedOnly.handled()).isTrue();
    assertThat(disableSelectedOnly.shouldQuit()).isFalse();
    assertThat(controller.statusMessage()).contains("Selected-only view disabled");
    assertThat(controller.uiState().extensions().selectedOnlyEnabled()).isFalse();

    UiAction clearPreset = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(clearPreset.handled()).isTrue();
    assertThat(clearPreset.shouldQuit()).isFalse();
    assertThat(controller.statusMessage()).contains("Preset filter disabled");
    assertThat(controller.uiState().extensions().activePresetFilterName()).isBlank();

    UiAction clearCategory = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(clearCategory.handled()).isTrue();
    assertThat(clearCategory.shouldQuit()).isFalse();
    assertThat(controller.statusMessage()).contains("Category filter cleared");
    assertThat(controller.uiState().extensions().activeCategoryFilterTitle()).isBlank();
    assertThat(controller.catalogSectionHeaders()).contains("Core", "Web");

    UiAction moveToList = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(moveToList.handled()).isTrue();
    assertThat(moveToList.shouldQuit()).isFalse();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);

    UiAction quit = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(quit.handled()).isTrue();
    assertThat(quit.shouldQuit()).isTrue();
  }

  @Test
  void xClearsSelectedExtensionsWhenListIsFocused() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar(' '));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(controller.selectedExtensionIds()).hasSize(2);

    UiAction clearAction = controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(clearAction.handled()).isTrue();
    assertThat(clearAction.shouldQuit()).isFalse();
    assertThat(controller.selectedExtensionIds()).isEmpty();
    assertThat(controller.statusMessage()).contains("Cleared 2 selected extensions");

    controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(controller.statusMessage()).contains("No selected extensions to clear");
  }

  @Test
  void vCyclesCategoryFilterAcrossVisibleCategories() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

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
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Filter: Data");

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web", "Data");
    assertThat(controller.statusMessage()).contains("Category filter cleared");
  }

  @Test
  void categoryFilterStacksWithSearchQuery() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20)))));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar('v'));
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Filter: Core");

    controller.onEvent(KeyEvent.ofChar('f', KeyModifiers.CTRL));
    controller.onEvent(KeyEvent.ofChar('r'));
    controller.onEvent(KeyEvent.ofChar('e'));
    controller.onEvent(KeyEvent.ofChar('s'));
    controller.onEvent(KeyEvent.ofChar('t'));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.catalogSectionHeaders()).containsExactly("Web");
    assertThat(UiControllerTestHarness.renderToString(controller)).doesNotContain("Filter: Core");

    controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(controller.filteredExtensionCount()).isEqualTo(2);
    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web");
  }

  @Test
  void collapsedCategoryStateSurvivesCategoryFilterCycle() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofKey(KeyCode.PAGE_DOWN));
    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▶ Web");

    controller.onEvent(KeyEvent.ofChar('v'));
    controller.onEvent(KeyEvent.ofChar('v'));
    controller.onEvent(KeyEvent.ofChar('v'));
    controller.onEvent(KeyEvent.ofChar('v'));

    assertThat(controller.catalogSectionHeaders()).containsExactly("Core", "Web", "Data");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▶ Web ");
  }

  @Test
  void qNoLongerTriggersQuitByDefault() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);

    UiAction action = controller.onEvent(KeyEvent.ofChar('q'));

    assertThat(action.shouldQuit()).isFalse();
    assertThat(action.handled()).isTrue();
    assertThat(controller.request().groupId()).endsWith("q");
  }

  @Test
  void ctrlCStillQuitsFromShell() {
    CoreTuiController controller = UiControllerTestHarness.controller();

    UiAction action = controller.onEvent(KeyEvent.ofChar('c', KeyModifiers.CTRL));

    assertThat(action.shouldQuit()).isTrue();
    assertThat(action.handled()).isTrue();
  }

  @Test
  void listNavigationSkipsSectionHeadersAcrossCategoryBoundaries() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
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
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20)))));

    controller.onEvent(KeyEvent.ofChar('j', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.statusMessage()).contains("No favorite extension");

    controller.onEvent(KeyEvent.ofChar('f'));
    assertThat(controller.favoriteExtensionCount()).isEqualTo(1);

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    assertThat(controller.favoritesOnlyFilterEnabled()).isTrue();
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofChar('k', KeyModifiers.CTRL));
    assertThat(controller.favoritesOnlyFilterEnabled()).isFalse();
    assertThat(controller.filteredExtensionCount()).isEqualTo(2);

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.GROUP_ID);
    controller.onEvent(KeyEvent.ofChar('j', KeyModifiers.CTRL));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
  }

  @Test
  void categoryCloseAndOpenAllKeysWorkWhileBrowsingList() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.focusedListExtensionId()).isEmpty();
    assertThat(controller.statusMessage()).contains("Closed category: Core");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▶ Core ");
    assertThat(UiControllerTestHarness.renderToString(controller)).doesNotContain("CDI");

    controller.onEvent(KeyEvent.ofChar('C'));

    assertThat(controller.statusMessage()).contains("Opened 1 category");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("CDI");
  }

  @Test
  void leftAndRightSupportSectionHierarchyNavigation() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
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
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▶ Core ");

    controller.onEvent(KeyEvent.ofKey(KeyCode.RIGHT));
    assertThat(controller.statusMessage()).contains("Opened category: Core");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("CDI");
  }

  @Test
  void spaceOnCategoryHeaderReopensClosedCategory() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▶ Core ");

    controller.onEvent(KeyEvent.ofChar(' '));

    assertThat(controller.statusMessage()).contains("Opened category: Core");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("CDI");
  }

  @Test
  void pageUpAndPageDownJumpBetweenCategorySections() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
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
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");

    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.statusMessage()).contains("Closed category: Core");
    assertThat(controller.focusedListExtensionId()).isEmpty();
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▶ Core ");

    controller.onEvent(KeyEvent.ofChar('c'));
    assertThat(controller.statusMessage()).contains("Opened category: Core");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("CDI");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("▼ Core");

    controller.onEvent(KeyEvent.ofChar('j'));
    assertThat(controller.focusedListExtensionId()).isEqualTo("io.quarkus:quarkus-arc");
  }

  @Test
  void allCollapsedCategoriesStillSupportHeaderNavigationAndReopen() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                ExtensionCatalogLoadResult.live(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                        new ExtensionDto(
                            "io.quarkus:quarkus-jdbc-postgresql",
                            "JDBC PostgreSQL",
                            "jdbc-postgresql",
                            "Data",
                            30)))));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar('c'));
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('c'));
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('c'));

    String allCollapsed = UiControllerTestHarness.renderToString(controller);
    assertThat(allCollapsed).contains("▶ Core ");
    assertThat(allCollapsed).contains("▶ Web ");
    assertThat(allCollapsed).contains("▶ Data ");
    assertThat(allCollapsed).doesNotContain("CDI");
    assertThat(allCollapsed).doesNotContain("REST");
    assertThat(allCollapsed).doesNotContain("JDBC PostgreSQL");
    assertThat(controller.focusedListExtensionId()).isEmpty();

    controller.onEvent(KeyEvent.ofChar('k'));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.statusMessage()).contains("Opened category: Web");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("REST");
  }

  @Test
  void categoryCloseFromRecentlySelectedItemDoesNotCollapseUnderlyingCategory() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Recently Selected");

    controller.onEvent(KeyEvent.ofKey(KeyCode.HOME));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.statusMessage()).contains("No category selected to close");
    assertThat(UiControllerTestHarness.renderToString(controller)).doesNotContain("▶ Core");
  }

  @Test
  void pageUpSkipsRecentlySelectedPseudoSection() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar(' '));
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Recently Selected");

    controller.onEvent(KeyEvent.ofKey(KeyCode.LEFT));
    assertThat(controller.statusMessage()).contains("Moved to section: Core");

    controller.onEvent(KeyEvent.ofKey(KeyCode.PAGE_UP));
    assertThat(controller.statusMessage()).contains("No previous category section");
  }

  @Test
  void metadataSelectorsCycleFromLoadedOptionsAndBlockFreeTextEdits() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                new ExtensionCatalogLoadResult(
                    List.of(new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest")),
                    CatalogSource.LIVE,
                    false,
                    "",
                    selectorMetadata())));

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.PLATFORM_STREAM);
    assertThat(controller.request().platformStream()).isEqualTo("io.quarkus.platform:3.31");
    // Selector uses "Platform:" prefix in compact mode
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("Platform:");

    controller.onEvent(KeyEvent.ofChar('l'));
    assertThat(controller.request().platformStream()).isEqualTo("io.quarkus.platform:3.20");
    assertThat(controller.validation().isValid()).isFalse();
    assertThat(controller.validation().errors().getFirst().field()).isEqualTo("compatibility");

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.JAVA_VERSION);
    controller.onEvent(KeyEvent.ofChar('h'));
    assertThat(controller.request().javaVersion()).isEqualTo("21");
    assertThat(controller.validation().isValid()).isTrue();

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.BUILD_TOOL);
    String originalBuildTool = controller.request().buildTool();
    controller.onEvent(KeyEvent.ofChar('x'));
    assertThat(controller.request().buildTool()).isEqualTo(originalBuildTool);

    controller.onEvent(KeyEvent.ofChar('l'));
    assertThat(controller.request().buildTool()).isEqualTo("gradle");
  }

  @Test
  void focusedSelectorRetainsValidationHintForInvalidPrefill() {
    ProjectRequest invalidRequest =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "./generated",
            "gradle.kotlin",
            "25");
    MetadataCompatibilityContext unavailableMetadata =
        MetadataCompatibilityContext.failure("metadata unavailable");
    ForgeUiState initialState =
        new ForgeUiState(
            invalidRequest,
            new ProjectRequestValidator()
                .validate(invalidRequest)
                .merge(unavailableMetadata.validate(invalidRequest)),
            unavailableMetadata);
    CoreTuiController controller = CoreTuiController.from(initialState);
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.BUILD_TOOL);

    assertThat(controller.validation().isValid()).isFalse();
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34)).contains("⚠");
  }

  @Test
  void ctrlYCyclesPresetFilterFromApiPresets() {
    CoreTuiController controller = UiControllerTestHarness.controller();
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                new ExtensionCatalogLoadResult(
                    List.of(
                        new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                        new ExtensionDto("io.quarkus:quarkus-jdbc-postgresql", "JDBC", "jdbc")),
                    CatalogSource.LIVE,
                    false,
                    "",
                    null,
                    Map.of(
                        "web", List.of("io.quarkus:quarkus-rest"),
                        "data", List.of("io.quarkus:quarkus-jdbc-postgresql")))));
    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_LIST);

    controller.onEvent(KeyEvent.ofChar('y', KeyModifiers.CTRL));
    assertThat(controller.statusMessage()).contains("Preset filter:");
    assertThat(UiControllerTestHarness.renderToString(controller)).contains("[preset:");

    controller.onEvent(KeyEvent.ofChar('y', KeyModifiers.CTRL));
    assertThat(controller.statusMessage()).contains("Preset filter:");

    controller.onEvent(KeyEvent.ofChar('y', KeyModifiers.CTRL));
    assertThat(controller.statusMessage()).isEqualTo("Preset filter disabled");
  }

  private static MetadataDto selectorMetadata() {
    return new MetadataDto(
        List.of("17", "21", "25"),
        List.of("maven", "gradle"),
        Map.of("maven", List.of("17", "21", "25"), "gradle", List.of("21", "25")),
        List.of(
            new PlatformStream("io.quarkus.platform:3.31", "3.31", true, List.of("17", "21", "25")),
            new PlatformStream("io.quarkus.platform:3.20", "3.20", false, List.of("17", "21"))));
  }

  private static void withSystemProperty(String key, String value, Runnable runnable) {
    String previous = System.getProperty(key);
    try {
      System.setProperty(key, value);
      runnable.run();
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
  }
}
