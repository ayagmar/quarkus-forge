package dev.ayagmar.quarkusforge.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class UrlConnectionTransport implements ApiTransport {
  private static final int MAX_THREADS = 4;

  private final ExecutorService executorService;
  private final ConnectionFactory connectionFactory;

  UrlConnectionTransport() {
    this(newExecutorService(), uri -> (HttpURLConnection) uri.toURL().openConnection());
  }

  UrlConnectionTransport(ExecutorService executorService, ConnectionFactory connectionFactory) {
    this.executorService = Objects.requireNonNull(executorService);
    this.connectionFactory = Objects.requireNonNull(connectionFactory);
  }

  private static ExecutorService newExecutorService() {
    AtomicInteger threadId = new AtomicInteger(1);
    return Executors.newFixedThreadPool(
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
    AtomicReference<HttpURLConnection> connectionReference = new AtomicReference<>();
    CompletableFuture<R> responseFuture =
        new CompletableFuture<>() {
          @Override
          public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
              disconnectQuietly(connectionReference.get());
            }
            return cancelled;
          }
        };
    try {
      executorService.execute(
          () -> {
            if (responseFuture.isCancelled()) {
              return;
            }
            try {
              R response = execute(request, responseReader, responseFuture, connectionReference);
              responseFuture.complete(response);
            } catch (IOException ioException) {
              responseFuture.completeExceptionally(new CompletionException(ioException));
            } catch (RuntimeException runtimeException) {
              responseFuture.completeExceptionally(runtimeException);
            }
          });
    } catch (RuntimeException runtimeException) {
      responseFuture.completeExceptionally(runtimeException);
    }
    return responseFuture;
  }

  @Override
  public void close() {
    executorService.shutdownNow();
  }

  private <R extends ApiHttpResponse> R execute(
      ApiRequest request,
      ResponseReader<R> responseReader,
      CompletableFuture<?> responseFuture,
      AtomicReference<HttpURLConnection> connectionReference)
      throws IOException {
    if (Thread.currentThread().isInterrupted() || responseFuture.isCancelled()) {
      throw new CancellationException("Request was interrupted before execution");
    }
    HttpURLConnection connection = connectionFactory.open(request.uri());
    connectionReference.set(connection);
    if (Thread.currentThread().isInterrupted() || responseFuture.isCancelled()) {
      disconnectQuietly(connection);
      throw new CancellationException("Request was cancelled before response handling");
    }
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

  private static void disconnectQuietly(HttpURLConnection connection) {
    if (connection != null) {
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

  @FunctionalInterface
  interface ConnectionFactory {
    HttpURLConnection open(URI uri) throws IOException;
  }
}
