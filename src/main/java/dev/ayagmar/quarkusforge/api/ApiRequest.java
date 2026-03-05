package dev.ayagmar.quarkusforge.api;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

record ApiRequest(URI uri, String acceptHeader, Duration timeout) {
  ApiRequest {
    Objects.requireNonNull(uri);
    Objects.requireNonNull(acceptHeader);
    Objects.requireNonNull(timeout);
  }
}
