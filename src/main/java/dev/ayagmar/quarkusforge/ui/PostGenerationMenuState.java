package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;

/**
 * Encapsulates the post-generation action menu state and key handling. Manages the action selection
 * list, GitHub visibility sub-menu, and exit plan construction.
 */
final class PostGenerationMenuState {
  private boolean visible;
  private int actionSelection;
  private boolean githubVisibilityMenuVisible;
  private int githubVisibilitySelection;
  private Path lastGeneratedProjectPath;
  private String lastGeneratedNextCommand = "";
  private PostGenerationExitPlan exitPlan;
  private List<UiTextConstants.PostGenerationAction> actions = List.of();
  private List<String> actionLabels = List.of();

  void setActions(List<UiTextConstants.PostGenerationAction> actions) {
    this.actions = actions;
    this.actionLabels = UiTextConstants.postGenerationActionLabels(actions);
  }

  List<String> actionLabels() {
    return actionLabels;
  }

  boolean isVisible() {
    return visible;
  }

  boolean isGithubVisibilityMenuVisible() {
    return githubVisibilityMenuVisible;
  }

  int actionSelection() {
    return actionSelection;
  }

  int githubVisibilitySelection() {
    return githubVisibilitySelection;
  }

  Path lastGeneratedProjectPath() {
    return lastGeneratedProjectPath;
  }

  PostGenerationExitPlan exitPlan() {
    return exitPlan;
  }

  String successHint() {
    if (lastGeneratedProjectPath == null || lastGeneratedNextCommand.isEmpty()) {
      return "";
    }
    String path = lastGeneratedProjectPath.toString();
    String quotedPath = path.contains(" ") ? "\"" + path + "\"" : path;
    return "cd " + quotedPath + " && " + lastGeneratedNextCommand;
  }

  void reset() {
    visible = false;
    actionSelection = 0;
    githubVisibilityMenuVisible = false;
    githubVisibilitySelection = 0;
    exitPlan = null;
    lastGeneratedProjectPath = null;
    lastGeneratedNextCommand = "";
  }

  void showAfterSuccess(Path generatedPath, String nextCommand) {
    lastGeneratedProjectPath = generatedPath;
    lastGeneratedNextCommand = nextCommand == null ? "" : nextCommand;
    exitPlan = null;
    visible = true;
    actionSelection = 0;
    githubVisibilityMenuVisible = false;
    githubVisibilitySelection = 0;
  }

  void hideAfterFailureOrCancel() {
    visible = false;
    githubVisibilityMenuVisible = false;
    exitPlan = null;
  }

  /**
   * Handles a key event while the post-generation menu is visible.
   *
   * @return a {@link MenuKeyResult} describing the outcome, or {@code null} if the menu is not
   *     visible.
   */
  MenuKeyResult handleKey(KeyEvent keyEvent) {
    if (!visible) {
      return null;
    }
    if (githubVisibilityMenuVisible) {
      return handleGithubVisibilityKey(keyEvent);
    }
    if (keyEvent.isCtrlC()) {
      selectExit(PostGenerationExitAction.QUIT);
      return MenuKeyResult.quit();
    }
    if (keyEvent.isCancel()) {
      selectExit(PostGenerationExitAction.QUIT);
      return MenuKeyResult.quit();
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      moveActionSelection(-1);
      return MenuKeyResult.handled();
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      moveActionSelection(1);
      return MenuKeyResult.handled();
    }
    if (keyEvent.isFocusPrevious()) {
      moveActionSelection(-1);
      return MenuKeyResult.handled();
    }
    if (keyEvent.isFocusNext()) {
      moveActionSelection(1);
      return MenuKeyResult.handled();
    }
    if (UiKeyMatchers.isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < actionLabels.size()) {
        actionSelection = selected;
        return executeSelection();
      }
      return MenuKeyResult.handled();
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      return executeSelection();
    }
    return MenuKeyResult.handled();
  }

  private MenuKeyResult handleGithubVisibilityKey(KeyEvent keyEvent) {
    if (keyEvent.isCtrlC()) {
      selectExit(PostGenerationExitAction.QUIT);
      return MenuKeyResult.quit();
    }
    if (keyEvent.isCancel()) {
      githubVisibilityMenuVisible = false;
      return MenuKeyResult.handled();
    }
    if (keyEvent.isUp() || UiKeyMatchers.isVimUpKey(keyEvent)) {
      moveGithubVisibilitySelection(-1);
      return MenuKeyResult.handled();
    }
    if (keyEvent.isDown() || UiKeyMatchers.isVimDownKey(keyEvent)) {
      moveGithubVisibilitySelection(1);
      return MenuKeyResult.handled();
    }
    if (keyEvent.isFocusPrevious()) {
      moveGithubVisibilitySelection(-1);
      return MenuKeyResult.handled();
    }
    if (keyEvent.isFocusNext()) {
      moveGithubVisibilitySelection(1);
      return MenuKeyResult.handled();
    }
    if (UiKeyMatchers.isDigitKey(keyEvent)) {
      int selected = Character.digit(keyEvent.character(), 10) - 1;
      if (selected >= 0 && selected < UiTextConstants.GITHUB_VISIBILITY_LABELS.size()) {
        githubVisibilitySelection = selected;
        selectExit(PostGenerationExitAction.PUBLISH_GITHUB, selectedGithubVisibility());
        return MenuKeyResult.quit();
      }
      return MenuKeyResult.handled();
    }
    if (keyEvent.isConfirm() || keyEvent.isSelect()) {
      selectExit(PostGenerationExitAction.PUBLISH_GITHUB, selectedGithubVisibility());
      return MenuKeyResult.quit();
    }
    return MenuKeyResult.handled();
  }

  private MenuKeyResult executeSelection() {
    UiTextConstants.PostGenerationAction selected =
        (actionSelection >= 0 && actionSelection < actions.size())
            ? actions.get(actionSelection)
            : null;
    PostGenerationExitAction action =
        selected != null ? selected.action() : PostGenerationExitAction.QUIT;
    if (action == PostGenerationExitAction.EXPORT_RECIPE_LOCK) {
      return MenuKeyResult.exportRecipe();
    }
    if (action == PostGenerationExitAction.PUBLISH_GITHUB) {
      githubVisibilityMenuVisible = true;
      githubVisibilitySelection = 0;
      return MenuKeyResult.handled();
    }
    if (action == PostGenerationExitAction.GENERATE_AGAIN) {
      return MenuKeyResult.generateAgain();
    }
    selectExit(action, selected != null ? selected.ideCommand() : null);
    return MenuKeyResult.quit();
  }

  private void moveActionSelection(int delta) {
    int size = actionLabels.size();
    if (size > 0) {
      actionSelection = Math.floorMod(actionSelection + delta, size);
    }
  }

  private void moveGithubVisibilitySelection(int delta) {
    int size = UiTextConstants.GITHUB_VISIBILITY_LABELS.size();
    if (size > 0) {
      githubVisibilitySelection = Math.floorMod(githubVisibilitySelection + delta, size);
    }
  }

  private GitHubVisibility selectedGithubVisibility() {
    return switch (githubVisibilitySelection) {
      case 1 -> GitHubVisibility.PUBLIC;
      case 2 -> GitHubVisibility.INTERNAL;
      default -> GitHubVisibility.PRIVATE;
    };
  }

  private void selectExit(PostGenerationExitAction action) {
    selectExit(action, GitHubVisibility.PRIVATE, null);
  }

  private void selectExit(PostGenerationExitAction action, String ideCommand) {
    selectExit(action, GitHubVisibility.PRIVATE, ideCommand);
  }

  private void selectExit(PostGenerationExitAction action, GitHubVisibility visibility) {
    selectExit(action, visibility, null);
  }

  private void selectExit(
      PostGenerationExitAction action, GitHubVisibility visibility, String ideCommand) {
    visible = false;
    actionSelection = 0;
    githubVisibilityMenuVisible = false;
    githubVisibilitySelection = 0;
    exitPlan =
        new PostGenerationExitPlan(
            action, lastGeneratedProjectPath, lastGeneratedNextCommand, visibility, ideCommand);
  }

  /** Result of handling a key event in the post-generation menu. */
  sealed interface MenuKeyResult {
    /** Menu handled the key, no further action needed. */
    static MenuKeyResult handled() {
      return Handled.INSTANCE;
    }

    /** User chose to quit / exit the menu (exit plan is set). */
    static MenuKeyResult quit() {
      return Quit.INSTANCE;
    }

    /** User chose to export recipe/lock files. */
    static MenuKeyResult exportRecipe() {
      return ExportRecipe.INSTANCE;
    }

    /** User chose to generate again (menu is reset). */
    static MenuKeyResult generateAgain() {
      return GenerateAgain.INSTANCE;
    }

    record Handled() implements MenuKeyResult {
      static final Handled INSTANCE = new Handled();
    }

    record Quit() implements MenuKeyResult {
      static final Quit INSTANCE = new Quit();
    }

    record ExportRecipe() implements MenuKeyResult {
      static final ExportRecipe INSTANCE = new ExportRecipe();
    }

    record GenerateAgain() implements MenuKeyResult {
      static final GenerateAgain INSTANCE = new GenerateAgain();
    }
  }
}
