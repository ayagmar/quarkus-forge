package dev.ayagmar.quarkusforge.api;

import java.util.List;
import java.util.Map;

sealed interface ApiHttpResponse permits ApiFileResponse, ApiStringResponse {
  int statusCode();

  Map<String, List<String>> headers();
}
