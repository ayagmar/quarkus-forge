package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ayagmar.quarkusforge.util.CaseInsensitiveLookup;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuarkusApiClient {
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final URI baseUri;
  private final RetryPolicy retryPolicy;
  private final AsyncSleeper sleeper;
  private final Clock clock;
  private final Supplier<Double> jitterSupplier;

  public QuarkusApiClient(URI baseUri) {
    this(
        HttpClient.newHttpClient(),
        ObjectMapperProvider.shared(),
        baseUri,
        RetryPolicy.defaults(),
        AsyncSleeper.system(),
        Clock.systemUTC(),
        () -> ThreadLocalRandom.current().nextDouble());
  }

  public QuarkusApiClient(
      HttpClient httpClient,
      ObjectMapper objectMapper,
      URI baseUri,
      RetryPolicy retryPolicy,
      AsyncSleeper sleeper,
      Clock clock,
      Supplier<Double> jitterSupplier) {
    this.httpClient = Objects.requireNonNull(httpClient);
    this.objectMapper = Objects.requireNonNull(objectMapper);
    this.baseUri = Objects.requireNonNull(baseUri);
    this.retryPolicy = Objects.requireNonNull(retryPolicy);
    this.sleeper = Objects.requireNonNull(sleeper);
    this.clock = Objects.requireNonNull(clock);
    this.jitterSupplier = Objects.requireNonNull(jitterSupplier);
  }

  public CompletableFuture<List<ExtensionDto>> fetchExtensions() {
    HttpRequest request = newGetRequest(baseUri.resolve("/api/extensions"), "application/json");
    return sendWithRetry(request, BodyHandlers.ofString(StandardCharsets.UTF_8), 1)
        .thenApply(this::assertSuccessful)
        .thenApply(response -> parseExtensionsPayload(response.body(), objectMapper));
  }

  public CompletableFuture<MetadataDto> fetchMetadata() {
    HttpRequest request = newGetRequest(baseUri.resolve("/api/metadata"), "application/json");
    return sendWithRetry(request, BodyHandlers.ofString(StandardCharsets.UTF_8), 1)
        .thenApply(this::assertSuccessful)
        .thenApply(response -> parseMetadataPayload(response.body(), objectMapper));
  }

  public CompletableFuture<byte[]> generateProjectZip(GenerationRequest generationRequest) {
    URI uri = GenerationQueryBuilder.build(baseUri, generationRequest);
    HttpRequest request = newGetRequest(uri, "application/zip, application/octet-stream");

    return sendWithRetry(request, BodyHandlers.ofByteArray(), 1)
        .thenApply(this::assertSuccessful)
        .thenApply(HttpResponse::body);
  }

  public CompletableFuture<Path> downloadProjectZipToFile(
      GenerationRequest generationRequest, Path destinationFile) {
    URI uri = GenerationQueryBuilder.build(baseUri, generationRequest);
    HttpRequest request = newGetRequest(uri, "application/zip, application/octet-stream");

    return sendWithRetry(
            request,
            BodyHandlers.ofFile(
                destinationFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING),
            1)
        .thenApply(this::assertSuccessful)
        .thenApply(HttpResponse::body);
  }

  static List<ExtensionDto> parseExtensionsPayload(String payload, ObjectMapper objectMapper) {
    JsonNode root = readPayload(payload, objectMapper);
    if (!root.isArray()) {
      throw new ApiContractException("Extensions payload must be a JSON array");
    }

    List<ExtensionDto> extensions = new ArrayList<>();
    for (JsonNode node : root) {
      String id = requiredText(node, "id");
      String name = requiredText(node, "name");
      String shortName = requiredText(node, "shortName");
      extensions.add(new ExtensionDto(id, name, shortName));
    }
    return List.copyOf(extensions);
  }

  static MetadataDto parseMetadataPayload(String payload, ObjectMapper objectMapper) {
    JsonNode root = readPayload(payload, objectMapper);
    if (!root.isObject()) {
      throw new ApiContractException("Metadata payload must be a JSON object");
    }

    JsonNode javaVersionsNode = root.get("javaVersions");
    JsonNode buildToolsNode = root.get("buildTools");
    if (javaVersionsNode == null || !javaVersionsNode.isArray()) {
      throw new ApiContractException("Metadata payload is missing 'javaVersions' array");
    }
    if (buildToolsNode == null || !buildToolsNode.isArray()) {
      throw new ApiContractException("Metadata payload is missing 'buildTools' array");
    }

    List<String> javaVersions = toStringList(javaVersionsNode, "javaVersions");
    List<String> buildTools = toStringList(buildToolsNode, "buildTools");
    Map<String, List<String>> compatibility =
        parseCompatibility(root.get("compatibility"), buildTools);
    return new MetadataDto(javaVersions, buildTools, compatibility);
  }

  private <T> CompletableFuture<HttpResponse<T>> sendWithRetry(
      HttpRequest request, BodyHandler<T> bodyHandler, int attempt) {
    return httpClient
        .sendAsync(request, bodyHandler)
        .orTimeout(
            retryPolicy.requestTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .handle(
            (response, throwable) -> {
              Throwable cause = unwrapThrowable(throwable);
              if (cause != null) {
                return handleFailure(request, bodyHandler, attempt, cause);
              }

              if (shouldRetry(response.statusCode()) && attempt < retryPolicy.maxAttempts()) {
                Duration delay = computeRetryDelay(attempt, response.headers());
                return scheduleRetry(request, bodyHandler, attempt, delay);
              }

              return CompletableFuture.completedFuture(response);
            })
        .thenCompose(Function.identity());
  }

  private <T> CompletableFuture<HttpResponse<T>> handleFailure(
      HttpRequest request, BodyHandler<T> bodyHandler, int attempt, Throwable cause) {
    if (attempt < retryPolicy.maxAttempts() && isRetryableFailure(cause)) {
      Duration delay =
          computeRetryDelay(attempt, HttpHeaders.of(java.util.Map.of(), (a, b) -> true));
      return scheduleRetry(request, bodyHandler, attempt, delay);
    }

    return CompletableFuture.failedFuture(
        new ApiClientException(
            "Request failed after " + attempt + " attempt(s): " + cause.getMessage(), cause));
  }

  private <T> CompletableFuture<HttpResponse<T>> scheduleRetry(
      HttpRequest request, BodyHandler<T> bodyHandler, int attempt, Duration delay) {
    return sleeper
        .sleep(delay)
        .thenCompose(ignored -> sendWithRetry(request, bodyHandler, attempt + 1));
  }

  private boolean shouldRetry(int statusCode) {
    return statusCode == 429 || statusCode >= 500;
  }

  private boolean isRetryableFailure(Throwable cause) {
    return cause instanceof TimeoutException
        || cause instanceof HttpTimeoutException
        || cause instanceof IOException;
  }

  private Duration computeRetryDelay(int attempt, HttpHeaders headers) {
    Duration retryAfter = parseRetryAfter(headers);
    if (retryAfter != null) {
      return retryAfter;
    }

    Duration baseDelay = exponentialDelay(attempt);
    long millis = baseDelay.toMillis();
    if (millis == 0) {
      return baseDelay;
    }

    double jitter = (jitterSupplier.get() * 2.0d) - 1.0d;
    double factor = 1.0d + (retryPolicy.jitterRatio() * jitter);
    long adjustedMillis = Math.max(0, Math.round(millis * factor));
    return Duration.ofMillis(adjustedMillis);
  }

  private Duration parseRetryAfter(HttpHeaders headers) {
    return headers.firstValue("Retry-After").map(this::parseRetryAfterValue).orElse(null);
  }

  private Duration parseRetryAfterValue(String rawValue) {
    try {
      long seconds = Long.parseLong(rawValue.trim());
      return Duration.ofSeconds(Math.max(0, seconds));
    } catch (NumberFormatException ignored) {
      // Continue with HTTP-date parsing.
    }

    try {
      ZonedDateTime retryTime = ZonedDateTime.parse(rawValue, DateTimeFormatter.RFC_1123_DATE_TIME);
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

  private HttpRequest newGetRequest(URI uri, String acceptHeader) {
    return HttpRequest.newBuilder(uri)
        .GET()
        .header("Accept", acceptHeader)
        .timeout(retryPolicy.requestTimeout())
        .build();
  }

  private <T> HttpResponse<T> assertSuccessful(HttpResponse<T> response) {
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      String responseBody =
          response.body() instanceof String ? (String) response.body() : "<binary>";
      throw new ApiHttpException(status, responseBody);
    }
    return response;
  }

  private static JsonNode readPayload(String payload, ObjectMapper objectMapper) {
    try {
      return objectMapper.readTree(payload);
    } catch (IOException ioException) {
      throw new ApiContractException("Malformed JSON payload", ioException);
    }
  }

  private static String requiredText(JsonNode node, String fieldName) {
    JsonNode child = node.get(fieldName);
    if (child == null || !child.isTextual() || child.textValue().isBlank()) {
      throw new ApiContractException("Missing required contract field '" + fieldName + "'");
    }
    return child.textValue();
  }

  private static List<String> toStringList(JsonNode node, String fieldName) {
    List<String> values = new ArrayList<>();
    for (JsonNode element : node) {
      if (!element.isTextual()) {
        throw new ApiContractException("Field '" + fieldName + "' must contain only strings");
      }
      values.add(element.textValue());
    }
    return List.copyOf(values);
  }

  private static Map<String, List<String>> parseCompatibility(
      JsonNode node, List<String> buildTools) {
    Map<String, List<String>> compatibility = new LinkedHashMap<>();
    if (node == null || node.isNull()) {
      return Map.of();
    }

    if (!node.isObject()) {
      throw new ApiContractException("Metadata payload field 'compatibility' must be an object");
    }

    Map<String, JsonNode> compatibilityByBuildTool = new LinkedHashMap<>();
    node.fields()
        .forEachRemaining(entry -> compatibilityByBuildTool.put(entry.getKey(), entry.getValue()));

    for (String buildTool : buildTools) {
      JsonNode javaVersionsNode = CaseInsensitiveLookup.find(compatibilityByBuildTool, buildTool);
      if (javaVersionsNode == null || !javaVersionsNode.isArray()) {
        throw new ApiContractException(
            "Metadata payload compatibility missing build tool entry '" + buildTool + "'");
      }
      compatibility.put(buildTool, toStringList(javaVersionsNode, "compatibility." + buildTool));
    }
    return compatibility;
  }

  private static Throwable unwrapThrowable(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    if (throwable instanceof CompletionException completionException
        && completionException.getCause() != null) {
      return completionException.getCause();
    }
    return throwable;
  }
}
