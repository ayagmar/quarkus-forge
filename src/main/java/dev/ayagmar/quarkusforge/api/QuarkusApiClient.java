package dev.ayagmar.quarkusforge.api;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuarkusApiClient {
  private static final List<String> FALLBACK_BUILD_TOOLS =
      List.of("maven", "gradle", "gradle-kotlin-dsl");
  private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
  private static final TypeReference<List<ExtensionPayload>> EXTENSIONS_TYPE =
      new TypeReference<>() {};
  private static final TypeReference<List<StreamPayload>> STREAMS_TYPE = new TypeReference<>() {};

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
    MetadataPayload metadataPayload = readValue(payload, objectMapper, MetadataPayload.class);
    if (metadataPayload.javaVersions() == null) {
      throw new ApiContractException("Metadata payload is missing 'javaVersions' array");
    }
    if (metadataPayload.buildTools() == null) {
      throw new ApiContractException("Metadata payload is missing 'buildTools' array");
    }

    List<String> javaVersions = copyStringList(metadataPayload.javaVersions(), "javaVersions");
    List<String> buildTools = copyStringList(metadataPayload.buildTools(), "buildTools");
    Map<String, List<String>> compatibility =
        parseCompatibility(metadataPayload.compatibility(), buildTools);
    List<PlatformStream> platformStreams = parsePlatformStreams(metadataPayload.platformStreams());
    return new MetadataDto(javaVersions, buildTools, compatibility, platformStreams);
  }

  static List<String> parseJavaVersionsFromStreamsPayload(
      String payload, ObjectMapper objectMapper) {
    return parseStreamsMetadataPayload(payload, objectMapper).javaVersions();
  }

  static List<PlatformStream> parsePlatformStreamsFromStreamsPayload(
      String payload, ObjectMapper objectMapper) {
    return parseStreamsMetadataPayload(payload, objectMapper).platformStreams();
  }

  static StreamsMetadata parseStreamsMetadataPayload(String payload, ObjectMapper objectMapper) {
    List<StreamPayload> streamPayloads = readValue(payload, objectMapper, STREAMS_TYPE);

    Set<Integer> versions = new LinkedHashSet<>();
    List<PlatformStream> platformStreams = new ArrayList<>();
    for (StreamPayload stream : streamPayloads) {
      String key = requiredText(stream.key(), "key");
      JavaCompatibilityPayload javaCompatibility = stream.javaCompatibility();
      if (javaCompatibility == null) {
        throw new ApiContractException("Stream payload is missing 'javaCompatibility' object");
      }
      List<Integer> javaVersionsNode = javaCompatibility.versions();
      if (javaVersionsNode == null) {
        throw new ApiContractException("Stream payload is missing 'javaCompatibility.versions'");
      }
      List<String> streamJavaVersions = new ArrayList<>();
      for (Integer version : javaVersionsNode) {
        if (version == null) {
          throw new ApiContractException(
              "Stream payload field 'javaCompatibility.versions' must contain integers");
        }
        int parsed = version;
        if (parsed > 0) {
          versions.add(parsed);
          streamJavaVersions.add(String.valueOf(parsed));
        }
      }
      if (streamJavaVersions.isEmpty()) {
        throw new ApiContractException(
            "Stream payload 'javaCompatibility.versions' must contain at least one positive value");
      }
      String platformVersion = normalizeOptionalText(stream.platformVersion());
      boolean recommended = stream.recommended();
      platformStreams.add(new PlatformStream(key, platformVersion, recommended, streamJavaVersions));
    }

    if (versions.isEmpty()) {
      throw new ApiContractException("Streams payload did not provide any Java versions");
    }
    List<String> javaVersions =
        versions.stream().sorted(Comparator.naturalOrder()).map(String::valueOf).toList();
    return new StreamsMetadata(javaVersions, List.copyOf(platformStreams));
  }

  static List<String> parseBuildToolsFromOpenApiPayload(String payload, ObjectMapper objectMapper) {
    OpenApiPayload openApiPayload = readValue(payload, objectMapper, OpenApiPayload.class);
    OpenApiPathsPayload paths = openApiPayload.paths();
    OpenApiPathPayload downloadPath = paths == null ? null : paths.download();
    OpenApiGetPayload getOperation = downloadPath == null ? null : downloadPath.getOperation();
    List<OpenApiParameterPayload> parametersNode =
        getOperation == null ? null : getOperation.parameters();
    if (parametersNode == null) {
      throw new ApiContractException(
          "OpenAPI payload is missing '/api/download.get.parameters' array");
    }

    Set<String> buildTools = new LinkedHashSet<>();
    for (OpenApiParameterPayload parameter : parametersNode) {
      if (parameter == null || !"b".equals(parameter.name())) {
        continue;
      }
      OpenApiSchemaPayload schema = parameter.schema();
      List<String> parameterEnum = schema == null ? null : schema.enumValues();
      if (parameterEnum == null) {
        throw new ApiContractException("OpenAPI payload is missing '/api/download' build tool enum");
      }
      for (String value : parameterEnum) {
        String buildTool = BuildToolCodec.toUiValue(normalizeOptionalText(value));
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
    List<ExtensionPayload> root = readValue(payload, objectMapper, EXTENSIONS_TYPE);

    List<ExtensionDto> extensions = new ArrayList<>();
    for (ExtensionPayload node : root) {
      if (node == null) {
        throw new ApiContractException("Extensions payload entries must be objects");
      }
      String id = requiredText(node.id(), "id");
      String name = requiredText(node.name(), "name");
      String shortName = resolvedShortName(node.shortName(), name);
      String category = resolvedCategory(node.category());
      Integer order = node.order();
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
              Throwable cause = ThrowableUnwrapper.unwrapCompletionCause(throwable);
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

  private static final int MAX_ERROR_BODY_BYTES = 16 * 1024;

  private <T> HttpResponse<T> assertSuccessful(HttpResponse<T> response) {
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw new ApiHttpException(status, extractErrorBody(response));
    }
    return response;
  }

  private static String extractErrorBody(HttpResponse<?> response) {
    Object body = response.body();
    return switch (body) {
      case null -> "";
      case String text -> text;
      case byte[] bytes -> decodeBytesAsUtf8OrBinary(bytes);
      case Path path -> {
        try {
          if (!Files.exists(path)) {
            yield "";
          }
          try (java.io.InputStream inputStream = Files.newInputStream(path)) {
            byte[] bytes = inputStream.readNBytes(MAX_ERROR_BODY_BYTES);
            yield decodeBytesAsUtf8OrBinary(bytes);
          }
        } catch (IOException ignored) {
          yield "<binary>";
        }
      }
      default -> "<binary>";
    };
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
    // Simple heuristic: NUL character is a strong signal the payload isn't textual.
    return decoded.indexOf('\u0000') >= 0;
  }

  private static <T> T readValue(String payload, ObjectMapper objectMapper, Class<T> valueType) {
    try {
      return objectMapper.readValue(payload, valueType);
    } catch (IOException | RuntimeException ioException) {
      throw new ApiContractException("Malformed JSON payload", ioException);
    }
  }

  private static <T> T readValue(
      String payload, ObjectMapper objectMapper, TypeReference<T> valueTypeRef) {
    try {
      return objectMapper.readValue(payload, valueTypeRef);
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

  private static String requiredText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ApiContractException("Missing required contract field '" + fieldName + "'");
    }
    return value.trim();
  }

  private static String normalizeOptionalText(String value) {
    return value == null ? "" : value.trim();
  }

  private static String resolvedShortName(String value, String name) {
    String shortName = normalizeOptionalText(value);
    if (!shortName.isBlank()) {
      return shortName;
    }
    return name;
  }

  private static String resolvedCategory(String value) {
    String category = normalizeOptionalText(value);
    if (!category.isBlank()) {
      return category;
    }
    return "Other";
  }

  private static List<String> copyStringList(List<String> values, String fieldName) {
    List<String> valuesCopy = new ArrayList<>();
    for (String element : Objects.requireNonNull(values)) {
      if (element == null) {
        throw new ApiContractException("Field '" + fieldName + "' must contain only strings");
      }
      valuesCopy.add(element);
    }
    return List.copyOf(valuesCopy);
  }

  private static Map<String, List<String>> parseCompatibility(
      Map<String, List<String>> compatibilityPayload, List<String> buildTools) {
    Map<String, List<String>> compatibility = new LinkedHashMap<>();
    if (compatibilityPayload == null) {
      return Map.of();
    }

    Map<String, BuildToolCompatibilitySelection> compatibilityByNormalizedBuildTool =
        new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : compatibilityPayload.entrySet()) {
      String normalizedBuildTool = normalizeKey(entry.getKey());
      BuildToolCompatibilitySelection previous =
          compatibilityByNormalizedBuildTool.putIfAbsent(
              normalizedBuildTool,
              new BuildToolCompatibilitySelection(entry.getKey(), entry.getValue()));
      if (previous != null) {
        throw new ApiContractException(
            "Metadata payload compatibility contains duplicate build tool entries differing"
                + " only by case: '"
                + previous.key()
                + "' and '"
                + entry.getKey()
                + "'");
      }
    }

    for (String buildTool : buildTools) {
      BuildToolCompatibilitySelection compatibilityEntry =
          compatibilityByNormalizedBuildTool.get(normalizeKey(buildTool));
      List<String> javaVersions = compatibilityEntry == null ? null : compatibilityEntry.values();
      if (javaVersions == null) {
        throw new ApiContractException(
            "Metadata payload compatibility missing build tool entry '" + buildTool + "'");
      }
      compatibility.put(buildTool, copyStringList(javaVersions, "compatibility." + buildTool));
    }
    return compatibility;
  }

  private static List<PlatformStream> parsePlatformStreams(List<PlatformStreamPayload> node) {
    if (node == null) {
      return List.of();
    }

    List<PlatformStream> platformStreams = new ArrayList<>();
    for (PlatformStreamPayload platformStreamNode : node) {
      if (platformStreamNode == null) {
        throw new ApiContractException("Metadata platform stream entry must be an object");
      }
      String key = requiredText(platformStreamNode.key(), "key");
      String platformVersion = normalizeOptionalText(platformStreamNode.platformVersion());
      List<String> javaVersionsNode = platformStreamNode.javaVersions();
      if (javaVersionsNode == null) {
        throw new ApiContractException(
            "Metadata platform stream entry is missing 'javaVersions' array");
      }
      List<String> javaVersions = copyStringList(javaVersionsNode, "platformStreams.javaVersions");
      platformStreams.add(
          new PlatformStream(key, platformVersion, platformStreamNode.recommended(), javaVersions));
    }
    return List.copyOf(platformStreams);
  }

  private static String normalizeKey(String key) {
    return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
  }
}
