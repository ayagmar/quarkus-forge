package dev.ayagmar.quarkusforge.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuarkusApiClient implements AutoCloseable {
  private static final List<String> FALLBACK_BUILD_TOOLS =
      List.of("maven", "gradle", "gradle-kotlin-dsl");
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

  private final ApiTransport transport;
  private final URI baseUri;
  private final RetryPolicy retryPolicy;
  private final AsyncSleeper sleeper;
  private final Clock clock;
  private final Supplier<Double> jitterSupplier;

  public QuarkusApiClient(URI baseUri) {
    this(
        new UrlConnectionTransport(),
        baseUri,
        RetryPolicy.defaults(),
        AsyncSleeper.system(),
        Clock.systemUTC(),
        () -> ThreadLocalRandom.current().nextDouble());
  }

  public QuarkusApiClient(
      URI baseUri,
      RetryPolicy retryPolicy,
      AsyncSleeper sleeper,
      Clock clock,
      Supplier<Double> jitterSupplier) {
    this(new UrlConnectionTransport(), baseUri, retryPolicy, sleeper, clock, jitterSupplier);
  }

  QuarkusApiClient(
      ApiTransport transport,
      URI baseUri,
      RetryPolicy retryPolicy,
      AsyncSleeper sleeper,
      Clock clock,
      Supplier<Double> jitterSupplier) {
    this.transport = Objects.requireNonNull(transport);
    this.baseUri = Objects.requireNonNull(baseUri);
    this.retryPolicy = Objects.requireNonNull(retryPolicy);
    this.sleeper = Objects.requireNonNull(sleeper);
    this.clock = Objects.requireNonNull(clock);
    this.jitterSupplier = Objects.requireNonNull(jitterSupplier);
  }

  @Override
  public void close() {
    transport.close();
  }

  public CompletableFuture<List<ExtensionDto>> fetchExtensions() {
    ApiRequest request = newGetRequest(baseUri.resolve("/api/extensions"), "application/json");
    return sendWithRetry(() -> transport.sendStringAsync(request), 1)
        .thenApply(this::assertSuccessful)
        .thenApply(ApiStringResponse::body)
        .thenApply(ApiPayloadParser::parseExtensionsPayload);
  }

  public CompletableFuture<MetadataDto> fetchMetadata() {
    ApiRequest streamsRequest = newGetRequest(baseUri.resolve("/api/streams"), "application/json");
    ApiRequest openApiRequest = newGetRequest(baseUri.resolve("/q/openapi"), "application/json");

    CompletableFuture<StreamsMetadata> streamsMetadataFuture =
        sendWithRetry(() -> transport.sendStringAsync(streamsRequest), 1)
            .thenApply(this::assertSuccessful)
            .thenApply(ApiStringResponse::body)
            .thenApply(ApiPayloadParser::parseStreamsMetadataPayload);

    CompletableFuture<List<String>> buildToolsFuture =
        sendWithRetry(() -> transport.sendStringAsync(openApiRequest), 1)
            .thenApply(this::assertSuccessful)
            .thenApply(ApiStringResponse::body)
            .thenApply(ApiPayloadParser::parseBuildToolsFromOpenApiPayload)
            .exceptionally(ignored -> fallbackBuildTools());

    return streamsMetadataFuture.thenCombine(buildToolsFuture, QuarkusApiClient::toMetadata);
  }

  public CompletableFuture<Map<String, List<String>>> fetchPresets(String streamKey) {
    URI presetsUri =
        streamKey == null || streamKey.isBlank()
            ? baseUri.resolve("/api/presets")
            : baseUri.resolve(
                "/api/presets/stream/" + URLEncoder.encode(streamKey, StandardCharsets.UTF_8));
    ApiRequest request = newGetRequest(presetsUri, "application/json");
    return sendWithRetry(() -> transport.sendStringAsync(request), 1)
        .thenApply(this::assertSuccessful)
        .thenApply(ApiStringResponse::body)
        .thenApply(ApiPayloadParser::parsePresetsPayload);
  }

  private static MetadataDto toMetadata(StreamsMetadata streamsMetadata, List<String> buildTools) {
    List<String> javaVersions = streamsMetadata.javaVersions();

    // Build compatibility matrix from the union of all streams' Java versions.
    // Stream-specific Java version constraints are enforced separately by
    // MetadataCompatibilityValidator.validate() via per-stream javaVersions check.
    LinkedHashSet<String> allJavaVersions = new LinkedHashSet<>(javaVersions);
    for (PlatformStream stream : streamsMetadata.platformStreams()) {
      allJavaVersions.addAll(stream.javaVersions());
    }

    Map<String, List<String>> compatibility = new LinkedHashMap<>();
    for (String buildTool : buildTools) {
      compatibility.put(buildTool, List.copyOf(allJavaVersions));
    }
    return new MetadataDto(
        javaVersions, buildTools, compatibility, streamsMetadata.platformStreams());
  }

  private static List<String> fallbackBuildTools() {
    try {
      return MetadataSnapshotLoader.loadDefault().buildTools();
    } catch (RuntimeException ignored) {
      return FALLBACK_BUILD_TOOLS;
    }
  }

  public CompletableFuture<Path> downloadProjectZipToFile(
      GenerationRequest generationRequest, Path destinationFile) {
    URI uri = GenerationQueryBuilder.build(baseUri, generationRequest);
    ApiRequest request = newGetRequest(uri, "application/zip, application/octet-stream");
    Path temporaryArchive =
        destinationFile.resolveSibling(
            destinationFile.getFileName() + ".part-" + UUID.randomUUID());

    return sendWithRetry(() -> transport.sendFileAsync(request, temporaryArchive), 1)
        .thenApply(this::assertSuccessful)
        .thenApply(ApiFileResponse::body)
        .thenApply(path -> moveDownloadedArchive(path, destinationFile))
        .whenComplete(
            (ignored, throwable) -> {
              if (throwable != null) {
                deleteIfExistsQuietly(temporaryArchive);
              }
            });
  }

  private <R extends ApiHttpResponse> CompletableFuture<R> sendWithRetry(
      Supplier<CompletableFuture<R>> requestSender, int attempt) {
    return requestSender
        .get()
        .orTimeout(retryPolicy.requestTimeout().toMillis(), TimeUnit.MILLISECONDS)
        .handle(
            (response, throwable) -> {
              Throwable cause = ThrowableUnwrapper.unwrapCompletionCause(throwable);
              if (cause != null) {
                return handleFailure(requestSender, attempt, cause);
              }

              if (shouldRetry(response.statusCode()) && attempt < retryPolicy.maxAttempts()) {
                Duration delay = computeRetryDelay(attempt, response.headers());
                return scheduleRetry(requestSender, attempt, delay);
              }

              return CompletableFuture.completedFuture(response);
            })
        .thenCompose(Function.identity());
  }

  private <R extends ApiHttpResponse> CompletableFuture<R> handleFailure(
      Supplier<CompletableFuture<R>> requestSender, int attempt, Throwable cause) {
    if (attempt < retryPolicy.maxAttempts() && isRetryableFailure(cause)) {
      Duration delay = computeRetryDelay(attempt, Map.of());
      return scheduleRetry(requestSender, attempt, delay);
    }

    return CompletableFuture.failedFuture(
        new ApiClientException(
            "Request failed after " + attempt + " attempt(s): " + cause.getMessage(), cause));
  }

  private <R extends ApiHttpResponse> CompletableFuture<R> scheduleRetry(
      Supplier<CompletableFuture<R>> requestSender, int attempt, Duration delay) {
    return sleeper.sleep(delay).thenCompose(ignored -> sendWithRetry(requestSender, attempt + 1));
  }

  private boolean shouldRetry(int statusCode) {
    return statusCode == 429 || statusCode >= 500;
  }

  private boolean isRetryableFailure(Throwable cause) {
    return cause instanceof TimeoutException
        || cause instanceof SocketTimeoutException
        || cause instanceof IOException;
  }

  private Duration computeRetryDelay(int attempt, Map<String, List<String>> headers) {
    Duration retryAfter = parseRetryAfter(headers);
    if (retryAfter != null) {
      return boundRetryDelay(retryAfter);
    }

    Duration baseDelay = exponentialDelay(attempt);
    long millis = baseDelay.toMillis();
    if (millis == 0) {
      return baseDelay;
    }

    double jitter = (jitterSupplier.get() * 2.0d) - 1.0d;
    double factor = 1.0d + (retryPolicy.jitterRatio() * jitter);
    long adjustedMillis = Math.max(0, Math.round(millis * factor));
    return boundRetryDelay(Duration.ofMillis(adjustedMillis));
  }

  private Duration parseRetryAfter(Map<String, List<String>> headers) {
    String retryAfterValue = firstHeaderValue(headers, "Retry-After");
    if (retryAfterValue == null) {
      return null;
    }
    return parseRetryAfterValue(retryAfterValue);
  }

  private static String firstHeaderValue(Map<String, List<String>> headers, String name) {
    for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
      String key = entry.getKey();
      if (key != null && key.equalsIgnoreCase(name)) {
        List<String> values = entry.getValue();
        if (values != null && !values.isEmpty()) {
          return values.get(0);
        }
      }
    }
    return null;
  }

  private Duration parseRetryAfterValue(String rawValue) {
    String value = rawValue == null ? "" : rawValue.trim();
    try {
      long seconds = Long.parseLong(value);
      return Duration.ofSeconds(Math.max(0, seconds));
    } catch (NumberFormatException ignored) {
      // Continue with HTTP-date parsing.
    }

    try {
      ZonedDateTime retryTime = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
      long delayMillis = retryTime.toInstant().toEpochMilli() - Instant.now(clock).toEpochMilli();
      return Duration.ofMillis(Math.max(0, delayMillis));
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private Duration exponentialDelay(int attempt) {
    int cappedShift = Math.max(0, Math.min(20, attempt - 1));
    long multiplier = 1L << cappedShift;
    try {
      return retryPolicy.baseDelay().multipliedBy(multiplier);
    } catch (ArithmeticException arithmeticException) {
      return Duration.ofMillis(Long.MAX_VALUE);
    }
  }

  private static Duration boundRetryDelay(Duration candidate) {
    if (candidate.compareTo(MAX_RETRY_DELAY) > 0) {
      return MAX_RETRY_DELAY;
    }
    return candidate;
  }

  private ApiRequest newGetRequest(URI uri, String acceptHeader) {
    return new ApiRequest(uri, acceptHeader, retryPolicy.requestTimeout());
  }

  private static final int MAX_ERROR_BODY_BYTES = 16 * 1024;

  private <R extends ApiHttpResponse> R assertSuccessful(R response) {
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw new ApiHttpException(status, extractErrorBody(response));
    }
    return response;
  }

  private static String extractErrorBody(ApiHttpResponse response) {
    return switch (response) {
      case ApiStringResponse stringResponse -> extractErrorBody(stringResponse.body());
      case ApiFileResponse fileResponse -> extractErrorBody(fileResponse.body());
    };
  }

  private static String extractErrorBody(String body) {
    return body == null ? "" : body;
  }

  private static String extractErrorBody(Path path) {
    try {
      if (!Files.exists(path)) {
        return "";
      }
      try (InputStream inputStream = Files.newInputStream(path)) {
        byte[] bytes = inputStream.readNBytes(MAX_ERROR_BODY_BYTES);
        return decodeBytesAsUtf8OrBinary(bytes);
      }
    } catch (IOException ignored) {
      return "<binary>";
    }
  }

  private static String decodeBytesAsUtf8OrBinary(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    int length = Math.min(bytes.length, MAX_ERROR_BODY_BYTES);
    String decoded = new String(bytes, 0, length, StandardCharsets.UTF_8);
    if (looksBinary(decoded)) {
      return "<binary>";
    }
    return decoded;
  }

  private static boolean looksBinary(String decoded) {
    return decoded.indexOf('\u0000') >= 0;
  }

  private static Path moveDownloadedArchive(Path temporaryArchive, Path destinationFile) {
    try {
      Files.move(temporaryArchive, destinationFile, StandardCopyOption.REPLACE_EXISTING);
      return destinationFile;
    } catch (IOException ioException) {
      throw new ApiClientException(
          "Failed to move downloaded archive to destination: " + destinationFile, ioException);
    }
  }

  private static void deleteIfExistsQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup only.
    }
  }
}
