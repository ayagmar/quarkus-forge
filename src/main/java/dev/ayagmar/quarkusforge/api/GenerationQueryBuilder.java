package dev.ayagmar.quarkusforge.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class GenerationQueryBuilder {
  private GenerationQueryBuilder() {}

  public static URI build(URI baseUri, GenerationRequest request) {
    URI endpoint = downloadEndpoint(baseUri);

    List<GenerationQueryParameter> parameters =
        new ArrayList<>(
            List.of(
                new GenerationQueryParameter("S", request.platformStream()),
                new GenerationQueryParameter("g", request.groupId()),
                new GenerationQueryParameter("a", request.artifactId()),
                new GenerationQueryParameter("v", request.version()),
                new GenerationQueryParameter("b", BuildToolCodec.toApiValue(request.buildTool())),
                new GenerationQueryParameter("j", request.javaVersion())));
    request
        .extensions()
        .forEach(extension -> parameters.add(new GenerationQueryParameter("e", extension)));

    List<String> queryParts =
        parameters.stream()
            .filter(parameter -> parameter.value() != null && !parameter.value().isBlank())
            .map(parameter -> urlEncode(parameter.key()) + "=" + urlEncode(parameter.value()))
            .toList();

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
    } catch (URISyntaxException syntaxException) {
      throw new IllegalArgumentException("Invalid base URI: " + baseUri, syntaxException);
    }
  }

  private static String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
