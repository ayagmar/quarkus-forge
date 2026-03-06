package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostGenerationMenuStateTest {
  private PostGenerationMenuState state;

  @BeforeEach
  void setUp() {
    state = new PostGenerationMenuState();
    state.setActions(UiTextConstants.postGenerationActions(List.of()));
  }

  @Test
  void initiallyNotVisible() {
    assertThat(state.isVisible()).isFalse();
    assertThat(state.handleKey(KeyEvent.ofKey(KeyCode.ENTER))).isNull();
  }

  @Test
  void snapshotExposesSuccessHint() {
    state.showAfterSuccess(Path.of("/tmp/project"), "mvn quarkus:dev");

    assertThat(state.snapshot().visible()).isTrue();
    assertThat(state.snapshot().successHint()).isEqualTo("cd /tmp/project && mvn quarkus:dev");
  }

  @Test
  void applyRehydratesReducerOwnedState() {
    state.apply(
        new UiState.PostGenerationView(
            true,
            true,
            2,
            1,
            UiTextConstants.postGenerationActions(List.of()),
            Path.of("/tmp/project"),
            "mvn quarkus:dev",
            new PostGenerationExitPlan(
                PostGenerationExitAction.PUBLISH_GITHUB,
                Path.of("/tmp/project"),
                "mvn quarkus:dev",
                GitHubVisibility.PUBLIC,
                null)));

    assertThat(state.isVisible()).isTrue();
    assertThat(state.isGithubVisibilityMenuVisible()).isTrue();
    assertThat(state.actionSelection()).isEqualTo(2);
    assertThat(state.githubVisibilitySelection()).isEqualTo(1);
    assertThat(state.exitPlan()).isNotNull();
  }

  @Test
  void actionMenuNavigationReturnsReducerCommandsWithoutMutatingState() {
    state.showAfterSuccess(Path.of("/tmp/p"), "cmd");

    UiIntent.PostGenerationCommand down = state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
    UiIntent.PostGenerationCommand up = state.handleKey(KeyEvent.ofKey(KeyCode.UP));

    assertThat(down).isEqualTo(new UiIntent.PostGenerationCommand.MoveActionSelection(1));
    assertThat(up).isEqualTo(new UiIntent.PostGenerationCommand.MoveActionSelection(-1));
    assertThat(state.actionSelection()).isZero();
  }

  @Test
  void confirmOnExportReturnsConfirmCommandUntilReducerExecutesIt() {
    state.showAfterSuccess(Path.of("/tmp/p"), "cmd");
    state.apply(
        new UiState.PostGenerationView(
            true,
            false,
            state.actionLabels().indexOf("Export Forgefile"),
            0,
            UiTextConstants.postGenerationActions(List.of()),
            Path.of("/tmp/p"),
            "cmd",
            null));

    UiIntent.PostGenerationCommand result = state.handleKey(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(result).isInstanceOf(UiIntent.PostGenerationCommand.ConfirmSelection.class);
    assertThat(state.isVisible()).isTrue();
  }

  @Test
  void digitSelectionReturnsDirectSelectCommand() {
    state.showAfterSuccess(Path.of("/tmp/p"), "cmd");

    UiIntent.PostGenerationCommand result = state.handleKey(KeyEvent.ofChar('1'));

    assertThat(result).isEqualTo(new UiIntent.PostGenerationCommand.SelectActionIndex(0));
    assertThat(state.actionSelection()).isZero();
  }

  @Test
  void invalidDigitReturnsNoopCommand() {
    state.showAfterSuccess(Path.of("/tmp/p"), "cmd");

    UiIntent.PostGenerationCommand result = state.handleKey(KeyEvent.ofChar('9'));

    assertThat(result).isInstanceOf(UiIntent.PostGenerationCommand.Noop.class);
  }

  @Test
  void quitKeysReturnQuitCommandWithoutPreReducerExitPlanMutation() {
    state.showAfterSuccess(Path.of("/tmp/p"), "cmd");

    UiIntent.PostGenerationCommand ctrlC = state.handleKey(KeyEvent.ofChar('c', KeyModifiers.CTRL));
    UiIntent.PostGenerationCommand escape = state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(ctrlC).isInstanceOf(UiIntent.PostGenerationCommand.Quit.class);
    assertThat(escape).isInstanceOf(UiIntent.PostGenerationCommand.Quit.class);
    assertThat(state.exitPlan()).isNull();
    assertThat(state.isVisible()).isTrue();
  }

  @Test
  void githubVisibilityMenuReturnsVisibilityCommandsWithoutMutation() {
    state.apply(
        new UiState.PostGenerationView(
            true,
            true,
            0,
            0,
            UiTextConstants.postGenerationActions(List.of()),
            Path.of("/tmp/p"),
            "cmd",
            null));

    UiIntent.PostGenerationCommand down = state.handleKey(KeyEvent.ofKey(KeyCode.DOWN));
    UiIntent.PostGenerationCommand escape = state.handleKey(KeyEvent.ofKey(KeyCode.ESCAPE));
    UiIntent.PostGenerationCommand choosePublic = state.handleKey(KeyEvent.ofChar('2'));

    assertThat(down).isEqualTo(new UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection(1));
    assertThat(escape).isInstanceOf(UiIntent.PostGenerationCommand.CancelGithubVisibility.class);
    assertThat(choosePublic)
        .isEqualTo(new UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex(1));
    assertThat(state.githubVisibilitySelection()).isZero();
  }
}
