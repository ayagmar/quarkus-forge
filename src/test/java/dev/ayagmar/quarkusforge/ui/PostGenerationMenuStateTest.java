package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostGenerationMenuStateTest {
  private final PostGenerationMenuState menuState =
      new PostGenerationMenuState(UiTextConstants.postGenerationActions(List.of()));

  @Test
  void initialViewStartsHiddenWithConfiguredActions() {
    UiState.PostGenerationView view = menuState.initialView();

    assertThat(view.visible()).isFalse();
    assertThat(view.actionLabels())
        .containsExactly(
            "Publish to GitHub (requires gh)",
            "Open in IDE",
            "Open in terminal",
            "Generate again",
            "Quit",
            "Export Forgefile");
  }

  @Test
  void hiddenViewDoesNotHandleKeys() {
    assertThat(menuState.handleKey(menuState.initialView(), KeyEvent.ofKey(KeyCode.ENTER)))
        .isNull();
  }

  @Test
  void actionMenuNavigationReturnsReducerCommandsWithoutMutatingView() {
    UiState.PostGenerationView view =
        new UiState.PostGenerationView(
            true, false, 0, 0, UiTextConstants.postGenerationActions(List.of()), null, "", null);

    UiIntent.PostGenerationCommand down = menuState.handleKey(view, KeyEvent.ofKey(KeyCode.DOWN));
    UiIntent.PostGenerationCommand up = menuState.handleKey(view, KeyEvent.ofKey(KeyCode.UP));

    assertThat(down).isEqualTo(new UiIntent.PostGenerationCommand.MoveActionSelection(1));
    assertThat(up).isEqualTo(new UiIntent.PostGenerationCommand.MoveActionSelection(-1));
    assertThat(view.actionSelection()).isZero();
  }

  @Test
  void confirmOnExportReturnsConfirmCommandUntilReducerExecutesIt() {
    UiState.PostGenerationView view =
        new UiState.PostGenerationView(
            true,
            false,
            menuState.initialView().actionLabels().indexOf("Export Forgefile"),
            0,
            UiTextConstants.postGenerationActions(List.of()),
            null,
            "cmd",
            null);

    UiIntent.PostGenerationCommand result =
        menuState.handleKey(view, KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(result).isInstanceOf(UiIntent.PostGenerationCommand.ConfirmSelection.class);
    assertThat(view.visible()).isTrue();
  }

  @Test
  void digitSelectionReturnsDirectSelectCommand() {
    UiState.PostGenerationView view = menuState.initialView().afterSuccess(null, "cmd");

    UiIntent.PostGenerationCommand result = menuState.handleKey(view, KeyEvent.ofChar('1'));

    assertThat(result).isEqualTo(new UiIntent.PostGenerationCommand.SelectActionIndex(0));
    assertThat(view.actionSelection()).isZero();
  }

  @Test
  void invalidDigitReturnsNoopCommand() {
    UiState.PostGenerationView view = menuState.initialView().afterSuccess(null, "cmd");

    UiIntent.PostGenerationCommand result = menuState.handleKey(view, KeyEvent.ofChar('9'));

    assertThat(result).isInstanceOf(UiIntent.PostGenerationCommand.Noop.class);
  }

  @Test
  void quitKeysReturnQuitCommandWithoutMutatingReducerOwnedView() {
    UiState.PostGenerationView view = menuState.initialView().afterSuccess(null, "cmd");

    UiIntent.PostGenerationCommand ctrlC =
        menuState.handleKey(view, KeyEvent.ofChar('c', KeyModifiers.CTRL));
    UiIntent.PostGenerationCommand escape =
        menuState.handleKey(view, KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(ctrlC).isInstanceOf(UiIntent.PostGenerationCommand.Quit.class);
    assertThat(escape).isInstanceOf(UiIntent.PostGenerationCommand.Quit.class);
    assertThat(view.exitPlan()).isNull();
    assertThat(view.visible()).isTrue();
  }

  @Test
  void githubVisibilityMenuReturnsVisibilityCommandsWithoutMutation() {
    UiState.PostGenerationView view =
        new UiState.PostGenerationView(
            true, true, 0, 0, UiTextConstants.postGenerationActions(List.of()), null, "cmd", null);

    UiIntent.PostGenerationCommand down = menuState.handleKey(view, KeyEvent.ofKey(KeyCode.DOWN));
    UiIntent.PostGenerationCommand escape =
        menuState.handleKey(view, KeyEvent.ofKey(KeyCode.ESCAPE));
    UiIntent.PostGenerationCommand choosePublic = menuState.handleKey(view, KeyEvent.ofChar('2'));

    assertThat(down).isEqualTo(new UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection(1));
    assertThat(escape).isInstanceOf(UiIntent.PostGenerationCommand.CancelGithubVisibility.class);
    assertThat(choosePublic)
        .isEqualTo(new UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex(1));
    assertThat(view.githubVisibilitySelection()).isZero();
  }
}
