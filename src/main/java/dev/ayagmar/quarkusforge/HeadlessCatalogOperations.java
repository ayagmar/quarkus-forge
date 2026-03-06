package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.CatalogData;
import dev.ayagmar.quarkusforge.api.GenerationRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

interface HeadlessCatalogOperations extends AutoCloseable {
  CatalogData loadCatalogData(Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException;

  Map<String, List<String>> loadBuiltInPresets(String platformStream, Duration timeout)
      throws ExecutionException, InterruptedException, TimeoutException;

  CompletableFuture<Path> startGeneration(
      GenerationRequest generationRequest, Path outputPath, Consumer<String> progressLineConsumer);

  @Override
  void close();
}
