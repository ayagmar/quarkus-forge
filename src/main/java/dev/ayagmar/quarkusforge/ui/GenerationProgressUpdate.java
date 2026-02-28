package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

public record GenerationProgressUpdate(GenerationProgressStep step, String message) {
  public GenerationProgressUpdate {
    step = Objects.requireNonNull(step);
    message = message == null ? "" : message.strip();
  }

  public static GenerationProgressUpdate requestingArchive(String message) {
    return new GenerationProgressUpdate(GenerationProgressStep.REQUESTING_ARCHIVE, message);
  }

  public static GenerationProgressUpdate extractingArchive(String message) {
    return new GenerationProgressUpdate(GenerationProgressStep.EXTRACTING_ARCHIVE, message);
  }

  public static GenerationProgressUpdate finalizing(String message) {
    return new GenerationProgressUpdate(GenerationProgressStep.FINALIZING, message);
  }
}
