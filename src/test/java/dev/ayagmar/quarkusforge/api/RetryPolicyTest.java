package dev.ayagmar.quarkusforge.api;

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
}
