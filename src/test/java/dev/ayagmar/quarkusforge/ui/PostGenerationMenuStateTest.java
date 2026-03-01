package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.ui.PostGenerationMenuState.MenuKeyResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PostGenerationMenuStateTest {
  private PostGenerationMenuState state;

  @BeforeEach
  void setUp() {
    state = new PostGenerationMenuState();
  }

  @Test
  void initiallyNotVisible() {
    assertThat(state.isVisible()).isFalse();
    assertThat(state.handleKey(KeyEvent.ofKey(KeyCode.ENTER))).isNull();
  }

  @Test
  void showAfterSuccessMakesVisible() {
    state.showAfterSuccess(Path.of("/tmp/project"), "mvn quarkus:dev");
    assertThat(state.isVisible()).isTrue();
    assertThat(state.lastGeneratedProjectPath()).isEqualTo(Path.of("/tmp/project"));
    assertThat(state.actionSelection()).isZero();
  }

  @Test
  void successHintCombinesPathAndCommand() {
    state.showAfterSuccess(Path.of("/tmp/project"), "mvn quarkus:dev");
    assertThat(state.successHint()).isEqualTo("cd /tmp/project && mvn quarkus:dev");
  }

  @Test
  void successHintEmptyWhenNoNextCommand() {
    state.showAfterSuccess(Path.of("/tmp/project"), "");
    assertThat(state.successHint()).isEmpty();
  }

  @Test
  void successHintHandlesNullNextCommand() {
    state.showAfterSuccess(Path.of("/tmp/project"), null);
    assertThat(state.successHint()).isEmpty();
  }

  @Test
  void successHintQuotesPathWithSpaces() {
    state.showAfterSuccess(Path.of("/tmp/my project"), "mvn quarkus:dev");
    assertThat(state.successHint()).isEqualTo("cd \"/tmp/my project\" && mvn quarkus:dev");
  }

  @Test
  void resetClearsAllState() {
    state.showAfterSuccess(Path.of("/tmp/project"), "cmd");
    state.reset();
    assertThat(state.isVisible()).isFalse();
    assertThat(state.lastGeneratedProjectPath()).isNull();
    assertThat(state.exitPlan()).isNull();
    assertThat(state.actionSelection()).isZero();
  }

  @Test
  void hideAfterFailureClearsExitPlan() {
    state.showAfterSuccess(Path.of("/tmp/project"), "cmd");
    state.hideAfterFailureOrCancel();
    assertThat(state.isVisible()).isFalse();
    assertThat(state.exitPlan()).isNull();
  }

  @Nested
  class ActionMenuNavigation {
    @BeforeEach
    void show() {
      state.showAfterSuccess(Path.of("/tmp/p"), "cmd");
    }

    @Test
    void downKeyMovesSelectionForward() {
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
      assertThat(result).isInstanceOf(MenuKeyResult.Handled.class);
      assertThat(state.actionSelection()).isEqualTo(1);
    }

    @Test
    void upKeyWrapsToLast() {
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.UP));
      assertThat(result).isInstanceOf(MenuKeyResult.Handled.class);
      int lastIndex = UiTextConstants.POST_GENERATION_ACTION_LABELS.size() - 1;
      assertThat(state.actionSelection()).isEqualTo(lastIndex);
    }

    @Test
    void vimJMovesDown() {
      state.handleKey(KeyEvent.ofChar('j'));
      assertThat(state.actionSelection()).isEqualTo(1);
    }

    @Test
    void ctrlCQuitsAndSetsExitPlan() {
      MenuKeyResult result = state.handleKey(KeyEvent.ofChar('c', KeyModifiers.CTRL));
      assertThat(result).isInstanceOf(MenuKeyResult.Quit.class);
      assertThat(state.exitPlan()).isNotNull();
      assertThat(state.exitPlan().action()).isEqualTo(PostGenerationExitAction.QUIT);
    }

    @Test
    void escapeQuitsAndSetsExitPlan() {
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
      assertThat(result).isInstanceOf(MenuKeyResult.Quit.class);
    }
  }

  @Nested
  class ActionSelection {
    @BeforeEach
    void show() {
      state.showAfterSuccess(Path.of("/tmp/p"), "cmd");
    }

    @Test
    void enterOnExportRecipeReturnsExportResult() {
      // Index 0 is Export Forgefile + forge.lock
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
      assertThat(result).isInstanceOf(MenuKeyResult.ExportRecipe.class);
    }

    @Test
    void enterOnPublishGithubShowsVisibilityMenu() {
      state.handleKey(KeyEvent.ofKey(KeyCode.DOWN)); // → 1 (Publish GitHub)
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
      assertThat(result).isInstanceOf(MenuKeyResult.Handled.class);
      assertThat(state.isGithubVisibilityMenuVisible()).isTrue();
    }

    @Test
    void enterOnGenerateAgainResetsAndReturns() {
      // Navigate to "Generate again" (index 4)
      for (int i = 0; i < 4; i++) {
        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
      }
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
      assertThat(result).isInstanceOf(MenuKeyResult.GenerateAgain.class);
      assertThat(state.isVisible()).isFalse();
    }

    @Test
    void enterOnQuitSetsExitPlan() {
      // Navigate to "Quit" (index 5)
      for (int i = 0; i < 5; i++) {
        state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
      }
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
      assertThat(result).isInstanceOf(MenuKeyResult.Quit.class);
      assertThat(state.exitPlan().action()).isEqualTo(PostGenerationExitAction.QUIT);
    }

    @Test
    void digitKeySelectsDirectly() {
      // Digit '1' → index 0 → export recipe
      MenuKeyResult result = state.handleKey(KeyEvent.ofChar('1'));
      assertThat(result).isInstanceOf(MenuKeyResult.ExportRecipe.class);
    }

    @Test
    void invalidDigitKeyHandled() {
      // Digit '9' → out of range → handled
      MenuKeyResult result = state.handleKey(KeyEvent.ofChar('9'));
      assertThat(result).isInstanceOf(MenuKeyResult.Handled.class);
    }
  }

  @Nested
  class GithubVisibilityMenu {
    @BeforeEach
    void showVisibilityMenu() {
      state.showAfterSuccess(Path.of("/tmp/p"), "cmd");
      state.handleKey(KeyEvent.ofKey(KeyCode.DOWN)); // → Publish GitHub
      state.handleKey(KeyEvent.ofKey(KeyCode.ENTER)); // → opens visibility menu
    }

    @Test
    void visibilityMenuIsVisible() {
      assertThat(state.isGithubVisibilityMenuVisible()).isTrue();
      assertThat(state.githubVisibilitySelection()).isZero();
    }

    @Test
    void escapeClosesVisibilityMenu() {
      state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
      assertThat(state.isGithubVisibilityMenuVisible()).isFalse();
      assertThat(state.isVisible()).isTrue();
    }

    @Test
    void enterOnPrivateQuitsWithPrivateVisibility() {
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
      assertThat(result).isInstanceOf(MenuKeyResult.Quit.class);
      assertThat(state.exitPlan().action()).isEqualTo(PostGenerationExitAction.PUBLISH_GITHUB);
      assertThat(state.exitPlan().githubVisibility()).isEqualTo(GitHubVisibility.PRIVATE);
    }

    @Test
    void navigateToPublicAndConfirm() {
      state.handleKey(KeyEvent.ofKey(KeyCode.DOWN)); // → Public
      MenuKeyResult result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));
      assertThat(result).isInstanceOf(MenuKeyResult.Quit.class);
      assertThat(state.exitPlan().githubVisibility()).isEqualTo(GitHubVisibility.PUBLIC);
    }

    @Test
    void ctrlCQuitsFromVisibilityMenu() {
      MenuKeyResult result = state.handleKey(KeyEvent.ofChar('c', KeyModifiers.CTRL));
      assertThat(result).isInstanceOf(MenuKeyResult.Quit.class);
      assertThat(state.exitPlan().action()).isEqualTo(PostGenerationExitAction.QUIT);
    }
  }
}
