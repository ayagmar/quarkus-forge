package dev.ayagmar.quarkusforge.ui;

import java.nio.file.Path;
import java.util.Objects;

public record PostGenerationExitPlan(
    PostGenerationExitAction action,
    Path projectDirectory,
    String nextCommand,
    GitHubVisibility githubVisibility) {
  public PostGenerationExitPlan {
    action = Objects.requireNonNull(action);
    nextCommand = nextCommand == null ? "" : nextCommand.strip();
    githubVisibility = githubVisibility == null ? GitHubVisibility.PRIVATE : githubVisibility;
  }

  public PostGenerationExitPlan(
      PostGenerationExitAction action, Path projectDirectory, String nextCommand) {
    this(action, projectDirectory, nextCommand, GitHubVisibility.PRIVATE);
  }
}
