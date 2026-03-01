package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@FunctionalInterface
public interface ProjectGenerationRunner {
  CompletableFuture<Path> generate(
      GenerationRequest generationRequest,
      Path outputDirectory,
      BooleanSupplier cancelled,
      Consumer<GenerationProgressUpdate> progressListener);
}
