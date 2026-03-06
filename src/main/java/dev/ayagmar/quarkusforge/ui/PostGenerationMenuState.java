package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;

/**
 * Encapsulates the post-generation action menu state and key handling. Manages the action selection
 * list, GitHub visibility sub-menu, and exit plan construction.
 */
final class PostGenerationMenuState {
  private List<UiTextConstants.PostGenerationAction> actions = List.of();
  private UiState.PostGenerationView state =
      new UiState.PostGenerationView(false, false, 0, 0, List.of(), null, "", null);

  void setActions(List<UiTextConstants.PostGenerationAction> actions) {
    this.actions = actions;
    state =
        new UiState.PostGenerationView(
            state.visible(),
            state.githubVisibilityVisible(),
            state.actionSelection(),
            state.githubVisibilitySelection(),
            actions,
            state.lastGeneratedProjectPath(),
            state.lastGeneratedNextCommand(),
            state.exitPlan());
  }

  void apply(UiState.PostGenerationView nextState) {
    state =
        new UiState.PostGenerationView(
            nextState.visible(),
            nextState.githubVisibilityVisible(),
            nextState.actionSelection(),
            nextState.githubVisibilitySelection(),
            actions,
            nextState.lastGeneratedProjectPath(),
            nextState.lastGeneratedNextCommand(),
            nextState.exitPlan());
  }

  UiState.PostGenerationView snapshot() {
    return state;
  }

  List<String> actionLabels() {
    return state.actionLabels();
  }

  boolean isVisible() {
    return state.visible();
  }

  boolean isGithubVisibilityMenuVisible() {
    return state.githubVisibilityVisible();
  }

  int actionSelection() {
    return state.actionSelection();
  }

  int githubVisibilitySelection() {
    return state.githubVisibilitySelection();
  }

  Path lastGeneratedProjectPath() {
    return state.lastGeneratedProjectPath();
  }

  PostGenerationExitPlan exitPlan() {
    return state.exitPlan();
  }

  String successHint() {
    return state.successHint();
  }

  void reset() {
    apply(new UiState.PostGenerationView(false, false, 0, 0, actions, null, "", null));
  }

  void showAfterSuccess(Path generatedPath, String nextCommand) {
    apply(
        new UiState.PostGenerationView(
            true, false, 0, 0, actions, generatedPath, nextCommand, null));
  }

  void hideAfterFailureOrCancel() {
    apply(
        new UiState.PostGenerationView(
            false,
            false,
            state.actionSelection(),
            0,
            actions,
            state.lastGeneratedProjectPath(),
            state.lastGeneratedNextCommand(),
            null));
  }

  /**
   * Handles a key event while the post-generation menu is visible.
   *
   * @return a normalized reducer command, or {@code null} if the menu is not visible.
   */
  UiIntent.PostGenerationCommand handleKey(KeyEvent keyEvent) {
    if (!state.visible()) {
      return null;
    }
    if (state.githubVisibilityVisible()) {
      return handleGithubVisibilityKey(keyEvent);
    }
    if (keyEvent.isCtrlC()) {
      return new UiIntent.PostGenerationCommand.Quit();
    }
    if (keyEvent.isCancel()) {
      return new UiIntent.PostGenerationCommand.Quit();
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      return new UiIntent.PostGenerationCommand.MoveActionSelection(-1);
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      return new UiIntent.PostGenerationCommand.MoveActionSelection(1);
    }
    if (keyEvent.isFocusPrevious()) {
      return new UiIntent.PostGenerationCommand.MoveActionSelection(-1);
    }
    if (keyEvent.isFocusNext()) {
      return new UiIntent.PostGenerationCommand.MoveActionSelection(1);
    }
    if (UiKeyMatchers.isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < state.actions().size()) {
        return new UiIntent.PostGenerationCommand.SelectActionIndex(selected);
      }
      return new UiIntent.PostGenerationCommand.Noop();
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      return new UiIntent.PostGenerationCommand.ConfirmSelection();
    }
    return new UiIntent.PostGenerationCommand.Noop();
  }

  private UiIntent.PostGenerationCommand handleGithubVisibilityKey(KeyEvent keyEvent) {
    if (keyEvent.isCtrlC()) {
      return new UiIntent.PostGenerationCommand.Quit();
    }
    if (keyEvent.isCancel()) {
      return new UiIntent.PostGenerationCommand.CancelGithubVisibility();
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      return new UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection(-1);
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      return new UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection(1);
    }
    if (keyEvent.isFocusPrevious()) {
      return new UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection(-1);
    }
    if (keyEvent.isFocusNext()) {
      return new UiIntent.PostGenerationCommand.MoveGithubVisibilitySelection(1);
    }
    if (UiKeyMatchers.isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < UiTextConstants.GITHUB_VISIBILITY_LABELS.size()) {
        return new UiIntent.PostGenerationCommand.SelectGithubVisibilityIndex(selected);
      }
      return new UiIntent.PostGenerationCommand.Noop();
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      return new UiIntent.PostGenerationCommand.ConfirmSelection();
    }
    return new UiIntent.PostGenerationCommand.Noop();
  }
}
