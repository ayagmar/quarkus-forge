package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

record ApiStringResponse(int statusCode, Map<String, List<String>> headers, String body)
    implements ApiHttpResponse {
  ApiStringResponse {
    Objects.requireNonNull(headers);
    Objects.requireNonNull(body);
  }
}
