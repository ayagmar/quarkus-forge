package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.ayagmar.quarkusforge.api.ThrowableUnwrapper;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates async generation lifecycle transitions, including token-based stale protection,
 * cancellation handling, and completion reconciliation.
 */
final class GenerationFlowCoordinator {
  private CompletableFuture<Path> generationFuture;
  private long generationStartedAtNanos;
  private volatile boolean generationCancelRequested;
  private volatile long generationToken;

  GenerationFlowCoordinator() {
    generationFuture = null;
    generationStartedAtNanos = 0L;
    generationCancelRequested = false;
    generationToken = 0L;
  }

  void startFlow(
      ProjectGenerationRunner generationRunner,
      GenerationRequest generationRequest,
      Path outputDirectory,
      GenerationFlowCallbacks callbacks) {
    callbacks.beforeGenerationStart();
    if (!callbacks.transitionTo(GenerationState.LOADING)) {
      callbacks.onSubmitIgnored(callbacks.generationStateLabel());
      return;
    }
    generationCancelRequested = false;
    long token = ++generationToken;

    generationStartedAtNanos = System.nanoTime();
    onProgress(
        token,
        GenerationProgressUpdate.requestingArchive(
            "requesting project archive from Quarkus API..."),
        callbacks);

    CompletableFuture<Path> startedFuture;
    try {
      startedFuture =
          generationRunner.generate(
              generationRequest,
              outputDirectory,
              () -> generationCancelRequested || token != generationToken,
              progressUpdate ->
                  callbacks.scheduleOnRenderThread(
                      () -> onProgress(token, progressUpdate, callbacks)));
    } catch (RuntimeException runtimeException) {
      onCompleted(token, null, runtimeException, callbacks);
      return;
    }

    if (startedFuture == null) {
      onCompleted(
          token,
          null,
          new IllegalStateException("Generation service returned null future"),
          callbacks);
      return;
    }

    generationFuture = startedFuture;
    startedFuture.whenComplete(
        (generatedPath, throwable) ->
            callbacks.scheduleOnRenderThread(
                () -> onCompleted(token, generatedPath, throwable, callbacks)));
  }

  void reconcileCompletionIfDone(GenerationFlowCallbacks callbacks) {
    if (callbacks.currentState() != GenerationState.LOADING || generationFuture == null) {
      return;
    }
    if (!generationFuture.isDone()) {
      return;
    }

    Path generatedPath = null;
    Throwable throwable = null;
    try {
      generatedPath = generationFuture.join();
    } catch (RuntimeException completionFailure) {
      throwable = completionFailure;
    }
    onCompleted(generationToken, generatedPath, throwable, callbacks);
  }

  void requestCancellation(GenerationFlowCallbacks callbacks) {
    if (callbacks.currentState() != GenerationState.LOADING) {
      return;
    }
    if (generationFuture != null && generationFuture.isDone()) {
      reconcileCompletionIfDone(callbacks);
      return;
    }

    generationCancelRequested = true;
    callbacks.onCancellationRequested();
    if (generationFuture != null) {
      generationFuture.cancel(true);
    }
  }

  boolean isCancellationRequested() {
    return generationCancelRequested;
  }

  long generationToken() {
    return generationToken;
  }

  long elapsedMillisSinceStart() {
    return Math.max(0L, (System.nanoTime() - generationStartedAtNanos) / 1_000_000L);
  }

  private void onProgress(
      long token, GenerationProgressUpdate progressUpdate, GenerationFlowCallbacks callbacks) {
    if (token != generationToken || callbacks.currentState() != GenerationState.LOADING) {
      return;
    }
    callbacks.onProgress(progressUpdate);
  }

  private void onCompleted(
      long token, Path generatedPath, Throwable throwable, GenerationFlowCallbacks callbacks) {
    if (token != generationToken || callbacks.currentState() != GenerationState.LOADING) {
      return;
    }
    generationFuture = null;

    Throwable cause = ThrowableUnwrapper.unwrapCompletionCause(throwable);
    if (cause == null && generatedPath != null) {
      callbacks.transitionTo(GenerationState.SUCCESS);
      callbacks.onGenerationSuccess(generatedPath.toAbsolutePath().normalize());
      return;
    }

    if (cause == null) {
      cause = new IllegalStateException("Generation finished without an output path");
    }

    if (cause instanceof CancellationException || generationCancelRequested) {
      callbacks.transitionTo(GenerationState.CANCELLED);
      callbacks.onGenerationCancelled();
      return;
    }

    callbacks.transitionTo(GenerationState.ERROR);
    callbacks.onGenerationFailed(cause);
  }
}
