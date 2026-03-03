package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Bindings;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TuiBootstrapServiceTest {
  @BeforeEach
  void clearSystemPropertiesBefore() {
    System.clearProperty("tamboui.backend");
  }

  @AfterEach
  void clearSystemProperties() {
    System.clearProperty("tamboui.backend");
  }

  // ── static accessors ──────────────────────────────────────────────

  @Test
  void defaultBackendPreferenceIsPanama() {
    assertThat(TuiBootstrapService.defaultBackendPreference()).isEqualTo("panama");
  }

  @Test
  void startupSplashMinDurationIs450ms() {
    assertThat(TuiBootstrapService.STARTUP_SPLASH_MIN_DURATION).isEqualTo(Duration.ofMillis(450));
  }

  @Test
  void appBindingsProfileReturnsNonNullBindings() {
    Bindings bindings = TuiBootstrapService.appBindingsProfile();
    assertThat(bindings).isNotNull();
  }

  @Test
  void appTuiConfigReturnsNonNullConfig() {
    TuiConfig config = TuiBootstrapService.appTuiConfig();
    assertThat(config).isNotNull();
  }

  @Test
  void appTuiConfigIncludesBindings() {
    TuiConfig config = TuiBootstrapService.appTuiConfig();
    assertThat(config.bindings()).isNotNull();
  }
}
