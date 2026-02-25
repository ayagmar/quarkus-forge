package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuarkusApiClient {
  private static final List<String> FALLBACK_BUILD_TOOLS =
      List.of("maven", "gradle", "gradle-kotlin-dsl");
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

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
    HttpRequest streamsRequest = newGetRequest(baseUri.resolve("/api/streams"), "application/json");
    HttpRequest openApiRequest = newGetRequest(baseUri.resolve("/q/openapi"), "application/json");

    CompletableFuture<StreamsMetadata> streamsMetadataFuture =
        sendWithRetry(streamsRequest, BodyHandlers.ofString(StandardCharsets.UTF_8), 1)
            .thenApply(this::assertSuccessful)
            .thenApply(response -> parseStreamsMetadataPayload(response.body(), objectMapper));

    CompletableFuture<List<String>> buildToolsFuture =
        sendWithRetry(openApiRequest, BodyHandlers.ofString(StandardCharsets.UTF_8), 1)
            .thenApply(this::assertSuccessful)
            .thenApply(response -> parseBuildToolsFromOpenApiPayload(response.body(), objectMapper))
            .exceptionally(ignored -> fallbackBuildTools());

    return streamsMetadataFuture.thenCombine(buildToolsFuture, QuarkusApiClient::toMetadata);
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
    List<MetadataDto.PlatformStream> platformStreams =
        parsePlatformStreams(root.get("platformStreams"));
    return new MetadataDto(javaVersions, buildTools, compatibility, platformStreams);
  }

  static List<String> parseJavaVersionsFromStreamsPayload(
      String payload, ObjectMapper objectMapper) {
    return parseStreamsMetadataPayload(payload, objectMapper).javaVersions();
  }

  static List<MetadataDto.PlatformStream> parsePlatformStreamsFromStreamsPayload(
      String payload, ObjectMapper objectMapper) {
    return parseStreamsMetadataPayload(payload, objectMapper).platformStreams();
  }

  static StreamsMetadata parseStreamsMetadataPayload(String payload, ObjectMapper objectMapper) {
    JsonNode root = readPayload(payload, objectMapper);
    if (!root.isArray()) {
      throw new ApiContractException("Streams payload must be a JSON array");
    }

    Set<Integer> versions = new LinkedHashSet<>();
    List<MetadataDto.PlatformStream> platformStreams = new ArrayList<>();
    for (JsonNode stream : root) {
      String key = requiredText(stream, "key");
      JsonNode javaCompatibilityNode = stream.get("javaCompatibility");
      if (javaCompatibilityNode == null || !javaCompatibilityNode.isObject()) {
        throw new ApiContractException("Stream payload is missing 'javaCompatibility' object");
      }
      JsonNode javaVersionsNode = javaCompatibilityNode.get("versions");
      if (javaVersionsNode == null || !javaVersionsNode.isArray()) {
        throw new ApiContractException("Stream payload is missing 'javaCompatibility.versions'");
      }
      List<String> streamJavaVersions = new ArrayList<>();
      for (JsonNode version : javaVersionsNode) {
        if (!version.canConvertToInt()) {
          throw new ApiContractException(
              "Stream payload field 'javaCompatibility.versions' must contain integers");
        }
        int parsed = version.intValue();
        if (parsed > 0) {
          versions.add(parsed);
          streamJavaVersions.add(String.valueOf(parsed));
        }
      }
      if (streamJavaVersions.isEmpty()) {
        throw new ApiContractException(
            "Stream payload 'javaCompatibility.versions' must contain at least one positive value");
      }
      String platformVersion = optionalText(stream, "platformVersion");
      boolean recommended = stream.path("recommended").asBoolean(false);
      platformStreams.add(
          new MetadataDto.PlatformStream(key, platformVersion, recommended, streamJavaVersions));
    }

    if (versions.isEmpty()) {
      throw new ApiContractException("Streams payload did not provide any Java versions");
    }
    List<String> javaVersions =
        versions.stream().sorted(Comparator.naturalOrder()).map(String::valueOf).toList();
    return new StreamsMetadata(javaVersions, List.copyOf(platformStreams));
  }

  static List<String> parseBuildToolsFromOpenApiPayload(String payload, ObjectMapper objectMapper) {
    JsonNode root = readPayload(payload, objectMapper);
    if (!root.isObject()) {
      throw new ApiContractException("OpenAPI payload must be a JSON object");
    }

    // Use direct parameter iteration to avoid brittle fixed indexing in OpenAPI parameters.
    JsonNode parametersNode =
        root.path("paths").path("/api/download").path("get").path("parameters");
    if (!parametersNode.isArray()) {
      throw new ApiContractException(
          "OpenAPI payload is missing '/api/download.get.parameters' array");
    }

    Set<String> buildTools = new LinkedHashSet<>();
    for (JsonNode parameter : parametersNode) {
      if (!"b".equals(parameter.path("name").asText())) {
        continue;
      }
      JsonNode parameterEnum = parameter.path("schema").path("enum");
      if (!parameterEnum.isArray()) {
        throw new ApiContractException(
            "OpenAPI payload is missing '/api/download' build tool enum");
      }
      for (JsonNode value : parameterEnum) {
        if (!value.isTextual()) {
          throw new ApiContractException("OpenAPI build tool enum must contain strings");
        }
        String buildTool = BuildToolCodec.toUiValue(value.textValue());
        if (buildTool.isBlank()) {
          throw new ApiContractException("OpenAPI build tool enum must not contain blank values");
        }
        buildTools.add(buildTool);
      }
      break;
    }

    if (buildTools.isEmpty()) {
      throw new ApiContractException("OpenAPI payload did not provide any build tool enum values");
    }
    return List.copyOf(buildTools);
  }

  private static MetadataDto toMetadata(StreamsMetadata streamsMetadata, List<String> buildTools) {
    Map<String, List<String>> compatibility = new LinkedHashMap<>();
    List<String> javaVersions = streamsMetadata.javaVersions();
    for (String buildTool : buildTools) {
      compatibility.put(buildTool, javaVersions);
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
    Path temporaryArchive =
        destinationFile.resolveSibling(
            destinationFile.getFileName() + ".part-" + java.util.UUID.randomUUID());

    return sendWithRetry(
            request,
            BodyHandlers.ofFile(
                temporaryArchive,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING),
            1)
        .thenApply(this::assertSuccessful)
        .thenApply(HttpResponse::body)
        .thenApply(path -> moveDownloadedArchive(path, destinationFile))
        .whenComplete(
            (ignored, throwable) -> {
              if (throwable != null) {
                deleteIfExistsQuietly(temporaryArchive);
              }
            });
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
      String shortName = resolvedShortName(node, name);
      String category = resolvedCategory(node);
      Integer order = optionalInt(node, "order");
      extensions.add(new ExtensionDto(id, name, shortName, category, order));
    }
    return List.copyOf(extensions);
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

  private Duration parseRetryAfter(HttpHeaders headers) {
    return headers.firstValue("Retry-After").map(this::parseRetryAfterValue).orElse(null);
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
    } catch (IOException | RuntimeException ioException) {
      throw new ApiContractException("Malformed JSON payload", ioException);
    }
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

  private static String requiredText(JsonNode node, String fieldName) {
    JsonNode child = node.get(fieldName);
    if (child == null || !child.isTextual() || child.textValue().isBlank()) {
      throw new ApiContractException("Missing required contract field '" + fieldName + "'");
    }
    return child.textValue().trim();
  }

  private static String optionalText(JsonNode node, String fieldName) {
    JsonNode child = node.get(fieldName);
    if (child == null || !child.isTextual()) {
      return "";
    }
    return child.textValue().trim();
  }

  private static String resolvedShortName(JsonNode node, String name) {
    String shortName = optionalText(node, "shortName");
    if (!shortName.isBlank()) {
      return shortName;
    }
    return name;
  }

  private static String resolvedCategory(JsonNode node) {
    String category = optionalText(node, "category");
    if (!category.isBlank()) {
      return category;
    }
    return "Other";
  }

  private static Integer optionalInt(JsonNode node, String fieldName) {
    JsonNode child = node.get(fieldName);
    if (child == null || child.isNull()) {
      return null;
    }
    if (!child.canConvertToInt()) {
      throw new ApiContractException(
          "Contract field '" + fieldName + "' must be an integer when present");
    }
    return child.intValue();
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

    Map<String, CompatibilityEntry> compatibilityByNormalizedBuildTool = new LinkedHashMap<>();
    node.fields()
        .forEachRemaining(
            entry -> {
              String normalizedBuildTool = normalizeKey(entry.getKey());
              CompatibilityEntry previous =
                  compatibilityByNormalizedBuildTool.putIfAbsent(
                      normalizedBuildTool,
                      new CompatibilityEntry(entry.getKey(), entry.getValue()));
              if (previous != null) {
                throw new ApiContractException(
                    "Metadata payload compatibility contains duplicate build tool entries differing"
                        + " only by case: '"
                        + previous.originalBuildTool()
                        + "' and '"
                        + entry.getKey()
                        + "'");
              }
            });

    for (String buildTool : buildTools) {
      CompatibilityEntry compatibilityEntry =
          compatibilityByNormalizedBuildTool.get(normalizeKey(buildTool));
      JsonNode javaVersionsNode = compatibilityEntry == null ? null : compatibilityEntry.value();
      if (javaVersionsNode == null || !javaVersionsNode.isArray()) {
        throw new ApiContractException(
            "Metadata payload compatibility missing build tool entry '" + buildTool + "'");
      }
      compatibility.put(buildTool, toStringList(javaVersionsNode, "compatibility." + buildTool));
    }
    return compatibility;
  }

  private static List<MetadataDto.PlatformStream> parsePlatformStreams(JsonNode node) {
    if (node == null || node.isNull()) {
      return List.of();
    }
    if (!node.isArray()) {
      throw new ApiContractException("Metadata payload field 'platformStreams' must be an array");
    }

    List<MetadataDto.PlatformStream> platformStreams = new ArrayList<>();
    for (JsonNode platformStreamNode : node) {
      if (!platformStreamNode.isObject()) {
        throw new ApiContractException("Metadata platform stream entry must be an object");
      }
      String key = requiredText(platformStreamNode, "key");
      String platformVersion = optionalText(platformStreamNode, "platformVersion");
      JsonNode javaVersionsNode = platformStreamNode.get("javaVersions");
      if (javaVersionsNode == null || !javaVersionsNode.isArray()) {
        throw new ApiContractException(
            "Metadata platform stream entry is missing 'javaVersions' array");
      }
      List<String> javaVersions = toStringList(javaVersionsNode, "platformStreams.javaVersions");
      boolean recommended = platformStreamNode.path("recommended").asBoolean(false);
      platformStreams.add(
          new MetadataDto.PlatformStream(key, platformVersion, recommended, javaVersions));
    }
    return List.copyOf(platformStreams);
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

  private static String normalizeKey(String key) {
    return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
  }

  record StreamsMetadata(
      List<String> javaVersions, List<MetadataDto.PlatformStream> platformStreams) {
    StreamsMetadata {
      javaVersions = List.copyOf(javaVersions);
      platformStreams = List.copyOf(platformStreams);
    }
  }

  private record CompatibilityEntry(String originalBuildTool, JsonNode value) {}
}
