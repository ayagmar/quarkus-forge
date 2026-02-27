package dev.ayagmar.quarkusforge.api;

import java.time.Duration;
import java.util.Objects;

public record RetryPolicy(
    int maxAttempts, Duration requestTimeout, Duration baseDelay, double jitterRatio) {
  public RetryPolicy {
    Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    Objects.requireNonNull(baseDelay, "baseDelay must not be null");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be >= 1");
    }
    if (requestTimeout.isNegative() || requestTimeout.isZero()) {
      throw new IllegalArgumentException("requestTimeout must be positive");
    }
    if (baseDelay.isNegative()) {
      throw new IllegalArgumentException("baseDelay must not be negative");
    }
    if (jitterRatio < 0 || jitterRatio > 1) {
      throw new IllegalArgumentException("jitterRatio must be in [0, 1]");
    }
  }

  public static RetryPolicy defaults() {
    return new RetryPolicy(3, Duration.ofSeconds(5), Duration.ofMillis(200), 0.2d);
  }
}
