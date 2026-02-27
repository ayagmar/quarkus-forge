package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BootstrapLifecycleIT {
  @Test
  void runtimeConfirmsJava25Baseline() {
    String javaFeature = Runtime.version().feature() + "";
    assertThat(javaFeature).isEqualTo("25");
  }
}
