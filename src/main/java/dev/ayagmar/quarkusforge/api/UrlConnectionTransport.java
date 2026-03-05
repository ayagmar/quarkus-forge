package dev.ayagmar.quarkusforge.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class UrlConnectionTransport implements ApiTransport {
  private static final int MAX_THREADS = 4;

  private final ExecutorService executorService;

  UrlConnectionTransport() {
    AtomicInteger threadId = new AtomicInteger(1);
    this.executorService =
        Executors.newFixedThreadPool(
            MAX_THREADS,
            runnable -> {
              Thread thread = new Thread(runnable, "qf-api-http-" + threadId.getAndIncrement());
              thread.setDaemon(true);
              return thread;
            });
  }

  @Override
  public CompletableFuture<ApiStringResponse> sendStringAsync(ApiRequest request) {
    return sendAsync(
        request,
        (statusCode, headers, inputStream) ->
            new ApiStringResponse(statusCode, headers, readStringBody(inputStream)));
  }

  @Override
  public CompletableFuture<ApiFileResponse> sendFileAsync(
      ApiRequest request, Path destinationFile) {
    Objects.requireNonNull(destinationFile);
    return sendAsync(
        request,
        (statusCode, headers, inputStream) ->
            new ApiFileResponse(statusCode, headers, writeFileBody(inputStream, destinationFile)));
  }

  private <R extends ApiHttpResponse> CompletableFuture<R> sendAsync(
      ApiRequest request, ResponseReader<R> responseReader) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return execute(request, responseReader);
          } catch (IOException ioException) {
            throw new CompletionException(ioException);
          }
        },
        executorService);
  }

  @Override
  public void close() {
    executorService.shutdownNow();
  }

  private <R extends ApiHttpResponse> R execute(
      ApiRequest request, ResponseReader<R> responseReader) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) request.uri().toURL().openConnection();
    connection.setRequestMethod("GET");
    connection.setRequestProperty("Accept", request.acceptHeader());
    long timeoutMillisLong = Math.max(1L, request.timeout().toMillis());
    int timeoutMillis = Math.toIntExact(Math.min(Integer.MAX_VALUE, timeoutMillisLong));
    connection.setConnectTimeout(timeoutMillis);
    connection.setReadTimeout(timeoutMillis);

    int statusCode = connection.getResponseCode();
    Map<String, List<String>> headers =
        connection.getHeaderFields() == null ? Map.of() : connection.getHeaderFields();

    try (InputStream inputStream = openBodyStream(connection, statusCode)) {
      return responseReader.read(statusCode, headers, inputStream);
    } finally {
      connection.disconnect();
    }
  }

  private static InputStream openBodyStream(HttpURLConnection connection, int statusCode)
      throws IOException {
    if (statusCode >= 400) {
      InputStream errorStream = connection.getErrorStream();
      return errorStream == null ? new ByteArrayInputStream(new byte[0]) : errorStream;
    }
    return connection.getInputStream();
  }

  private static String readStringBody(InputStream inputStream) throws IOException {
    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
  }

  private static Path writeFileBody(InputStream inputStream, Path destinationFile)
      throws IOException {
    Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    return destinationFile;
  }

  @FunctionalInterface
  private interface ResponseReader<R extends ApiHttpResponse> {
    R read(int statusCode, Map<String, List<String>> headers, InputStream inputStream)
        throws IOException;
  }
}
