package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.util.List;

/**
 * Maps post-generation key input onto reducer commands using the reducer-owned post-generation
 * view.
 */
final class PostGenerationMenuState {
  private final List<UiTextConstants.PostGenerationAction> actions;

  PostGenerationMenuState(List<UiTextConstants.PostGenerationAction> actions) {
    this.actions = List.copyOf(actions);
  }

  UiState.PostGenerationView initialView() {
    return new UiState.PostGenerationView(false, false, 0, 0, actions, null, "", null);
  }

  /**
   * Handles a key event while the post-generation menu is visible.
   *
   * @return a normalized reducer command, or {@code null} if the menu is not visible.
   */
  UiIntent.PostGenerationCommand handleKey(UiState.PostGenerationView view, KeyEvent keyEvent) {
    if (!view.visible()) {
      return null;
    }
    if (view.githubVisibilityVisible()) {
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
      if (selected >= 0 && selected < view.actions().size()) {
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
