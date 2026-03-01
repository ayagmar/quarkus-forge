package dev.ayagmar.quarkusforge.ui;

import java.nio.file.Path;

interface GenerationFlowCallbacks {
  void beforeGenerationStart();

  boolean transitionTo(CoreTuiController.GenerationState targetState);

  CoreTuiController.GenerationState currentState();

  String generationStateLabel();

  void onSubmitIgnored(String stateLabel);

  void scheduleOnRenderThread(Runnable task);

  void onProgress(GenerationProgressUpdate progressUpdate);

  void onGenerationSuccess(Path generatedPath);

  void onGenerationCancelled();

  void onGenerationFailed(Throwable cause);

  void onCancellationRequested();
}
