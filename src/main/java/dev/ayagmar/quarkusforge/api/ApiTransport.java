package dev.ayagmar.quarkusforge.api;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

interface ApiTransport extends AutoCloseable {
  CompletableFuture<ApiStringResponse> sendStringAsync(ApiRequest request);

  CompletableFuture<ApiFileResponse> sendFileAsync(ApiRequest request, Path destinationFile);

  @Override
  void close();
}
