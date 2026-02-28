package dev.ayagmar.quarkusforge.ui;

import java.nio.file.Path;
import java.util.Objects;

public record PostGenerationExitPlan(
    PostGenerationExitAction action, Path projectDirectory, String nextCommand) {
  public PostGenerationExitPlan {
    action = Objects.requireNonNull(action);
    nextCommand = nextCommand == null ? "" : nextCommand.strip();
  }
}
