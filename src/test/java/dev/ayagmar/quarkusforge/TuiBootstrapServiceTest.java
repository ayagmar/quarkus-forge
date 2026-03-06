package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.runtime.TuiBootstrapService;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Bindings;
import java.lang.reflect.Method;
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

  @Test
  void configureTerminalBackendPreferenceSetsDefaultOnlyWhenUnset() throws Exception {
    invokeConfigureTerminalBackendPreference(null, null);
    assertThat(System.getProperty("tamboui.backend")).isEqualTo("panama");

    System.setProperty("tamboui.backend", "custom");

    invokeConfigureTerminalBackendPreference("custom", null);

    assertThat(System.getProperty("tamboui.backend")).isEqualTo("custom");
  }

  @Test
  void backendPreferenceIsExplicitlyConfiguredOnlyForNonBlankProperty() throws Exception {
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured(null, null)).isFalse();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured("   ", null)).isFalse();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured("panama", null)).isTrue();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured(null, "ansi")).isTrue();
  }

  private static void invokeConfigureTerminalBackendPreference(
      String propertyValue, String envValue) throws Exception {
    Method method =
        TuiBootstrapService.class.getDeclaredMethod(
            "configureTerminalBackendPreference", String.class, String.class);
    method.setAccessible(true);
    method.invoke(null, propertyValue, envValue);
  }

  private static boolean invokeIsBackendPreferenceExplicitlyConfigured(
      String propertyValue, String envValue) throws Exception {
    Method method =
        TuiBootstrapService.class.getDeclaredMethod(
            "isBackendPreferenceExplicitlyConfigured", String.class, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, propertyValue, envValue);
  }
}
