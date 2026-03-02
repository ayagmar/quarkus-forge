package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HeadlessTimeoutsTest {
  @Test
  void defaultCatalogTimeoutIs20Seconds() {
    String previous = System.getProperty("quarkus.forge.headless.catalog-timeout-ms");
    try {
      System.clearProperty("quarkus.forge.headless.catalog-timeout-ms");
      assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
    } finally {
      if (previous != null) {
        System.setProperty("quarkus.forge.headless.catalog-timeout-ms", previous);
      }
    }
  }

  @Test
  void defaultGenerationTimeoutIs2Minutes() {
    String previous = System.getProperty("quarkus.forge.headless.generation-timeout-ms");
    try {
      System.clearProperty("quarkus.forge.headless.generation-timeout-ms");
      assertThat(HeadlessTimeouts.generationTimeout()).isEqualTo(Duration.ofMinutes(2));
    } finally {
      if (previous != null) {
        System.setProperty("quarkus.forge.headless.generation-timeout-ms", previous);
      }
    }
  }

  @Test
  void catalogTimeoutFromSystemProperty() {
    System.setProperty("quarkus.forge.headless.catalog-timeout-ms", "5000");
    try {
      assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofMillis(5000));
    } finally {
      System.clearProperty("quarkus.forge.headless.catalog-timeout-ms");
    }
  }

  @Test
  void invalidPropertyFallsBackToDefault() {
    System.setProperty("quarkus.forge.headless.catalog-timeout-ms", "not-a-number");
    try {
      assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
    } finally {
      System.clearProperty("quarkus.forge.headless.catalog-timeout-ms");
    }
  }

  @Test
  void negativeValueFallsBackToDefault() {
    System.setProperty("quarkus.forge.headless.catalog-timeout-ms", "-100");
    try {
      assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
    } finally {
      System.clearProperty("quarkus.forge.headless.catalog-timeout-ms");
    }
  }

  @Test
  void blankPropertyFallsBackToDefault() {
    System.setProperty("quarkus.forge.headless.catalog-timeout-ms", "  ");
    try {
      assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
    } finally {
      System.clearProperty("quarkus.forge.headless.catalog-timeout-ms");
    }
  }
}
