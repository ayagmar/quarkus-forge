package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@FunctionalInterface
interface HeadlessProjectGenerator {
  CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest,
      Path outputPath,
      BooleanSupplier cancelled,
      Consumer<String> progressLineConsumer);
}
