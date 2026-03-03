package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {
  @Test
  void rejectsNullRequestTimeoutWithClearMessage() {
    assertThatThrownBy(() -> new RetryPolicy(1, null, Duration.ofMillis(10), 0.2d))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("requestTimeout must not be null");
  }

  @Test
  void rejectsNullBaseDelayWithClearMessage() {
    assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofSeconds(1), null, 0.2d))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("baseDelay must not be null");
  }

  @Test
  void rejectsZeroMaxAttempts() {
    assertThatThrownBy(() -> new RetryPolicy(0, Duration.ofSeconds(1), Duration.ofMillis(10), 0.2d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts must be >= 1");
  }

  @Test
  void rejectsNegativeMaxAttempts() {
    assertThatThrownBy(
            () -> new RetryPolicy(-1, Duration.ofSeconds(1), Duration.ofMillis(10), 0.2d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxAttempts must be >= 1");
  }

  @Test
  void rejectsZeroRequestTimeout() {
    assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, Duration.ofMillis(10), 0.2d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requestTimeout must be positive");
  }

  @Test
  void rejectsNegativeRequestTimeout() {
    assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofMillis(-1), Duration.ofMillis(10), 0.2d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("requestTimeout must be positive");
  }

  @Test
  void rejectsNegativeBaseDelay() {
    assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(-1), 0.2d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseDelay must not be negative");
  }

  @Test
  void rejectsJitterRatioBelowZero() {
    assertThatThrownBy(
            () -> new RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(10), -0.1d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jitterRatio must be in [0, 1]");
  }

  @Test
  void rejectsJitterRatioAboveOne() {
    assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(10), 1.1d))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("jitterRatio must be in [0, 1]");
  }

  @Test
  void defaultsProvidesValidPolicy() {
    RetryPolicy policy = RetryPolicy.defaults();
    assertThat(policy.maxAttempts()).isEqualTo(3);
    assertThat(policy.requestTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(policy.baseDelay()).isEqualTo(Duration.ofMillis(200));
    assertThat(policy.jitterRatio()).isEqualTo(0.2d);
  }

  @Test
  void acceptsEdgeBoundaryValues() {
    // jitterRatio == 0 and baseDelay == 0
    RetryPolicy zeroJitter = new RetryPolicy(1, Duration.ofSeconds(1), Duration.ZERO, 0.0d);
    assertThat(zeroJitter.jitterRatio()).isEqualTo(0.0d);

    // jitterRatio == 1
    RetryPolicy fullJitter = new RetryPolicy(1, Duration.ofSeconds(1), Duration.ZERO, 1.0d);
    assertThat(fullJitter.jitterRatio()).isEqualTo(1.0d);
  }
}
