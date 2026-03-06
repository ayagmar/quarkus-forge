package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HeadlessTimeoutsTest {
  private static final String HEADLESS_CATALOG_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.catalog-timeout-ms";
  private static final String HEADLESS_GENERATION_TIMEOUT_PROPERTY =
      "quarkus.forge.headless.generation-timeout-ms";

  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  @Test
  void defaultCatalogTimeoutIs20Seconds() {
    systemProperties.clear(HEADLESS_CATALOG_TIMEOUT_PROPERTY);

    assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void defaultGenerationTimeoutIs2Minutes() {
    systemProperties.clear(HEADLESS_GENERATION_TIMEOUT_PROPERTY);

    assertThat(HeadlessTimeouts.generationTimeout()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void catalogTimeoutFromSystemProperty() {
    systemProperties.set(HEADLESS_CATALOG_TIMEOUT_PROPERTY, 5000L);

    assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofMillis(5000));
  }

  @Test
  void generationTimeoutFromSystemProperty() {
    systemProperties.set(HEADLESS_GENERATION_TIMEOUT_PROPERTY, 60000L);

    assertThat(HeadlessTimeouts.generationTimeout()).isEqualTo(Duration.ofMillis(60000));
  }

  @Test
  void invalidPropertyFallsBackToDefault() {
    systemProperties.set(HEADLESS_CATALOG_TIMEOUT_PROPERTY, "not-a-number");

    assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void negativeValueFallsBackToDefault() {
    systemProperties.set(HEADLESS_CATALOG_TIMEOUT_PROPERTY, "-100");

    assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  void blankPropertyFallsBackToDefault() {
    systemProperties.set(HEADLESS_CATALOG_TIMEOUT_PROPERTY, "  ");

    assertThat(HeadlessTimeouts.catalogTimeout()).isEqualTo(Duration.ofSeconds(20));
  }
}
