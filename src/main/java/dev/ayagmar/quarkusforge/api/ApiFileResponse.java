package dev.ayagmar.quarkusforge.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record ApiFileResponse(int statusCode, Map<String, List<String>> headers, Path body)
    implements ApiHttpResponse {
  ApiFileResponse {
    Objects.requireNonNull(headers);
    Objects.requireNonNull(body);
  }
}
