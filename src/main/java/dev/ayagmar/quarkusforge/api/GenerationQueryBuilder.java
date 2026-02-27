package dev.ayagmar.quarkusforge.api;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class GenerationQueryBuilder {
  private GenerationQueryBuilder() {}

  public static URI build(URI baseUri, GenerationRequest request) {
    URI endpoint = downloadEndpoint(baseUri);

    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("S", request.platformStream());
    parameters.put("g", request.groupId());
    parameters.put("a", request.artifactId());
    parameters.put("v", request.version());
    parameters.put("b", BuildToolCodec.toApiValue(request.buildTool()));
    parameters.put("j", request.javaVersion());
    parameters.put("e", String.join(",", request.extensions()));

    List<String> queryParts =
        parameters.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
            .collect(Collectors.toList());

    String query = String.join("&", queryParts);
    if (query.isBlank()) {
      return endpoint;
    }
    return URI.create(endpoint + "?" + query);
  }

  private static URI downloadEndpoint(URI baseUri) {
    String basePath = baseUri.getPath();
    String normalizedPath = basePath == null ? "" : basePath.trim();
    if (normalizedPath.endsWith("/")) {
      normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
    }
    String endpointPath = normalizedPath + "/api/download";
    try {
      return new URI(
          baseUri.getScheme(),
          baseUri.getUserInfo(),
          baseUri.getHost(),
          baseUri.getPort(),
          endpointPath,
          null,
          null);
    } catch (java.net.URISyntaxException syntaxException) {
      throw new IllegalArgumentException("Invalid base URI: " + baseUri, syntaxException);
    }
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
