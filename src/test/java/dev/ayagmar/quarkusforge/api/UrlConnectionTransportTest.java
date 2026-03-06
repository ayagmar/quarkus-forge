package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UrlConnectionTransportTest {
  @TempDir Path tempDir;

  @Test
  void sendStringAsyncPropagatesHeadersAndAcceptHeader() {
    FakeHttpURLConnection connection =
        new FakeHttpURLConnection(
            200,
            Map.of("X-Test", List.of("present")),
            "catalog".getBytes(StandardCharsets.UTF_8),
            null);
    UrlConnectionTransport transport =
        new UrlConnectionTransport(Executors.newSingleThreadExecutor(), uri -> connection);

    ApiStringResponse response =
        transport
            .sendStringAsync(
                new ApiRequest(
                    URI.create("https://example.test/api/extensions"),
                    "application/json",
                    Duration.ofSeconds(2)))
            .join();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers()).containsEntry("X-Test", List.of("present"));
    assertThat(response.body()).isEqualTo("catalog");
    assertThat(connection.requestMethod).isEqualTo("GET");
    assertThat(connection.acceptHeader).isEqualTo("application/json");
    assertThat(connection.disconnected).isTrue();
    transport.close();
  }

  @Test
  void sendStringAsyncReadsErrorStreamForFailureStatus() {
    FakeHttpURLConnection connection =
        new FakeHttpURLConnection(
            503,
            Map.of("Retry-After", List.of("1")),
            null,
            "temporary failure".getBytes(StandardCharsets.UTF_8));
    UrlConnectionTransport transport =
        new UrlConnectionTransport(Executors.newSingleThreadExecutor(), uri -> connection);

    ApiStringResponse response =
        transport
            .sendStringAsync(
                new ApiRequest(
                    URI.create("https://example.test/api/extensions"),
                    "application/json",
                    Duration.ofSeconds(2)))
            .join();

    assertThat(response.statusCode()).isEqualTo(503);
    assertThat(response.body()).isEqualTo("temporary failure");
    assertThat(response.headers()).containsEntry("Retry-After", List.of("1"));
    transport.close();
  }

  @Test
  void sendFileAsyncWritesBodyToDestinationFile() throws IOException {
    FakeHttpURLConnection connection =
        new FakeHttpURLConnection(
            200,
            Map.of("Content-Type", List.of("application/zip")),
            "zip-content".getBytes(StandardCharsets.UTF_8),
            null);
    UrlConnectionTransport transport =
        new UrlConnectionTransport(Executors.newSingleThreadExecutor(), uri -> connection);
    Path destination = tempDir.resolve("generated.zip");

    ApiFileResponse response =
        transport
            .sendFileAsync(
                new ApiRequest(
                    URI.create("https://example.test/api/download"),
                    "application/zip, application/octet-stream",
                    Duration.ofSeconds(2)),
                destination)
            .join();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(destination);
    assertThat(Files.readString(destination)).isEqualTo("zip-content");
    assertThat(connection.acceptHeader).isEqualTo("application/zip, application/octet-stream");
    transport.close();
  }

  @Test
  void sendStringAsyncCoercesTimeoutsIntoHttpURLConnectionRange() {
    FakeHttpURLConnection minimalTimeoutConnection =
        new FakeHttpURLConnection(200, Map.of(), "ok".getBytes(StandardCharsets.UTF_8), null);
    UrlConnectionTransport minimalTimeoutTransport =
        new UrlConnectionTransport(
            Executors.newSingleThreadExecutor(), uri -> minimalTimeoutConnection);

    minimalTimeoutTransport
        .sendStringAsync(
            new ApiRequest(
                URI.create("https://example.test/min-timeout"), "application/json", Duration.ZERO))
        .join();

    assertThat(minimalTimeoutConnection.connectTimeout).isEqualTo(1);
    assertThat(minimalTimeoutConnection.readTimeout).isEqualTo(1);
    minimalTimeoutTransport.close();

    FakeHttpURLConnection maxTimeoutConnection =
        new FakeHttpURLConnection(200, Map.of(), "ok".getBytes(StandardCharsets.UTF_8), null);
    UrlConnectionTransport maxTimeoutTransport =
        new UrlConnectionTransport(
            Executors.newSingleThreadExecutor(), uri -> maxTimeoutConnection);

    maxTimeoutTransport
        .sendStringAsync(
            new ApiRequest(
                URI.create("https://example.test/max-timeout"),
                "application/json",
                Duration.ofDays(30_000)))
        .join();

    assertThat(maxTimeoutConnection.connectTimeout).isEqualTo(Integer.MAX_VALUE);
    assertThat(maxTimeoutConnection.readTimeout).isEqualTo(Integer.MAX_VALUE);
    maxTimeoutTransport.close();
  }

  @Test
  void cancellingRequestDisconnectsConnection() throws InterruptedException {
    CountDownLatch responseStarted = new CountDownLatch(1);
    CountDownLatch disconnected = new CountDownLatch(1);
    FakeHttpURLConnection connection =
        new FakeHttpURLConnection(200, Map.of(), "delayed".getBytes(StandardCharsets.UTF_8), null);
    connection.responseCodeHook =
        () -> {
          responseStarted.countDown();
          await(disconnected);
        };
    connection.disconnectHook = disconnected::countDown;
    UrlConnectionTransport transport =
        new UrlConnectionTransport(Executors.newSingleThreadExecutor(), uri -> connection);

    var responseFuture =
        transport.sendStringAsync(
            new ApiRequest(
                URI.create("https://example.test/cancel"),
                "application/json",
                Duration.ofSeconds(2)));
    assertThat(responseStarted.await(1, TimeUnit.SECONDS)).isTrue();

    assertThat(responseFuture.cancel(true)).isTrue();

    assertThat(disconnected.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(connection.disconnected).isTrue();
    transport.close();
  }

  @Test
  void closeShutsDownExecutorAndRejectsNewRequests() {
    var executor = Executors.newSingleThreadExecutor();
    UrlConnectionTransport transport =
        new UrlConnectionTransport(
            executor,
            uri ->
                new FakeHttpURLConnection(
                    200, Map.of(), "ok".getBytes(StandardCharsets.UTF_8), null));

    transport.close();

    assertThat(executor.isShutdown()).isTrue();
    assertThatThrownBy(
            () ->
                transport
                    .sendStringAsync(
                        new ApiRequest(
                            URI.create("https://example.test/closed"),
                            "application/json",
                            Duration.ofSeconds(2)))
                    .join())
        .isInstanceOf(CompletionException.class)
        .hasCauseInstanceOf(RejectedExecutionException.class);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(1, TimeUnit.SECONDS)) {
        throw new AssertionError("Timed out waiting on latch");
      }
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while waiting on latch", interruptedException);
    }
  }

  private static final class FakeHttpURLConnection extends HttpURLConnection {
    private final Map<String, List<String>> headerFields;
    private final byte[] inputBody;
    private final byte[] errorBody;

    private Runnable responseCodeHook = () -> {};
    private Runnable disconnectHook = () -> {};
    private String acceptHeader;
    private String requestMethod;
    private int connectTimeout;
    private int readTimeout;
    private boolean disconnected;

    private FakeHttpURLConnection(
        int statusCode,
        Map<String, List<String>> headerFields,
        byte[] inputBody,
        byte[] errorBody) {
      super(fakeUrl());
      this.responseCode = statusCode;
      this.headerFields = headerFields;
      this.inputBody = inputBody;
      this.errorBody = errorBody;
    }

    @Override
    public void disconnect() {
      disconnected = true;
      disconnectHook.run();
    }

    @Override
    public boolean usingProxy() {
      return false;
    }

    @Override
    public void connect() {}

    @Override
    public void setRequestMethod(String method) throws ProtocolException {
      this.requestMethod = method;
    }

    @Override
    public void setRequestProperty(String key, String value) {
      if ("Accept".equals(key)) {
        acceptHeader = value;
      }
    }

    @Override
    public void setConnectTimeout(int timeout) {
      connectTimeout = timeout;
    }

    @Override
    public void setReadTimeout(int timeout) {
      readTimeout = timeout;
    }

    @Override
    public int getResponseCode() {
      responseCodeHook.run();
      return responseCode;
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
      return headerFields;
    }

    @Override
    public InputStream getInputStream() {
      if (inputBody == null) {
        throw new AssertionError("Input stream not expected");
      }
      return new ByteArrayInputStream(inputBody);
    }

    @Override
    public InputStream getErrorStream() {
      if (errorBody == null) {
        return null;
      }
      return new ByteArrayInputStream(errorBody);
    }

    private static URL fakeUrl() {
      try {
        return URI.create("https://example.test").toURL();
      } catch (IOException ioException) {
        throw new IllegalStateException(ioException);
      }
    }
  }
}
