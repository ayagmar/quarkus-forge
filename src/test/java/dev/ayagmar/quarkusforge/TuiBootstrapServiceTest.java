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
    invokeConfigureTerminalBackendPreference();
    assertThat(System.getProperty("tamboui.backend")).isEqualTo("panama");

    System.setProperty("tamboui.backend", "custom");

    invokeConfigureTerminalBackendPreference();

    assertThat(System.getProperty("tamboui.backend")).isEqualTo("custom");
  }

  @Test
  void backendPreferenceIsExplicitlyConfiguredOnlyForNonBlankProperty() throws Exception {
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured()).isFalse();

    System.setProperty("tamboui.backend", "   ");
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured()).isFalse();

    System.setProperty("tamboui.backend", "panama");
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured()).isTrue();
  }

  private static void invokeConfigureTerminalBackendPreference() throws Exception {
    Method method =
        TuiBootstrapService.class.getDeclaredMethod("configureTerminalBackendPreference");
    method.setAccessible(true);
    method.invoke(null);
  }

  private static boolean invokeIsBackendPreferenceExplicitlyConfigured() throws Exception {
    Method method =
        TuiBootstrapService.class.getDeclaredMethod("isBackendPreferenceExplicitlyConfigured");
    method.setAccessible(true);
    return (boolean) method.invoke(null);
  }
}
