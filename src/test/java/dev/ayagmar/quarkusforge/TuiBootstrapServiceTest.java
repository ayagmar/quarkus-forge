package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.ayagmar.quarkusforge.cli.CliCommandTestSupport;
import dev.ayagmar.quarkusforge.diagnostics.DiagnosticLogger;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import dev.ayagmar.quarkusforge.runtime.TuiBootstrapService;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Bindings;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_ERR)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TuiBootstrapServiceTest {
  @TempDir Path tempDir;

  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  // ── static accessors ──────────────────────────────────────────────

  @Test
  void defaultBackendPreferenceUsesPanamaOnNonWindows() {
    systemProperties.set("os.name", "Linux");
    assertThat(TuiBootstrapService.defaultBackendPreference()).isEqualTo("panama");
  }

  @Test
  void defaultBackendPreferenceDoesNotTreatDarwinAsWindows() {
    systemProperties.set("os.name", "Darwin");
    assertThat(TuiBootstrapService.defaultBackendPreference()).isEqualTo("panama");
  }

  @Test
  void defaultBackendPreferenceUsesJlineOnWindows() {
    systemProperties.set("os.name", "Windows 11");
    assertThat(TuiBootstrapService.defaultBackendPreference()).isEqualTo("jline3");
  }

  @Test
  void defaultBackendPreferenceHandlesNullOsName() throws Exception {
    assertThat(invokeDefaultBackendPreference(null)).isEqualTo("panama");
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
    invokeConfigureTerminalBackendPreference(null, null, "Linux");
    assertThat(System.getProperty("tamboui.backend")).isEqualTo("panama");

    System.setProperty("tamboui.backend", "custom");

    invokeConfigureTerminalBackendPreference("custom", null, "Linux");

    assertThat(System.getProperty("tamboui.backend")).isEqualTo("custom");
  }

  @Test
  void configureTerminalBackendPreferenceUsesJlineDefaultOnWindows() throws Exception {
    invokeConfigureTerminalBackendPreference(null, null, "Windows 11");
    assertThat(System.getProperty("tamboui.backend")).isEqualTo("jline3");
  }

  @Test
  void backendPreferenceIsExplicitlyConfiguredOnlyForNonBlankProperty() throws Exception {
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured(null, null)).isFalse();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured("   ", null)).isFalse();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured(null, "   ")).isFalse();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured("panama", null)).isTrue();
    assertThat(invokeIsBackendPreferenceExplicitlyConfigured(null, "ansi")).isTrue();
  }

  @Test
  void resolvePresetStreamKeyPrefersCurrentThenRecommendedThenBlank() throws Exception {
    dev.ayagmar.quarkusforge.api.MetadataDto metadata =
        new dev.ayagmar.quarkusforge.api.MetadataDto(
            java.util.List.of("21"),
            java.util.List.of("maven"),
            java.util.Map.of("maven", java.util.List.of("21")),
            java.util.List.of(
                new dev.ayagmar.quarkusforge.api.PlatformStream(
                    "io.quarkus.platform:3.31", "3.31", true, java.util.List.of("21"))));

    assertThat(invokeResolvePresetStreamKey("io.quarkus.platform:current", metadata))
        .isEqualTo("io.quarkus.platform:current");
    assertThat(invokeResolvePresetStreamKey("", metadata)).isEqualTo("io.quarkus.platform:3.31");
    assertThat(invokeResolvePresetStreamKey("", null)).isEmpty();
  }

  @Test
  void configureTerminalBackendPreferenceUsesExistingSystemProperty() throws Exception {
    systemProperties.set("tamboui.backend", "custom");

    invokeConfigureTerminalBackendPreference();

    assertThat(System.getProperty("tamboui.backend")).isEqualTo("custom");
  }

  @Test
  void restoreTerminalBackendPreferenceClearsPropertyWhenPreviousValueMissing() throws Exception {
    systemProperties.set("tamboui.backend", "custom");

    invokeRestoreTerminalBackendPreference(null);

    assertThat(System.getProperty("tamboui.backend")).isNull();
  }

  @Test
  void restoreTerminalBackendPreferenceRestoresPreviousValue() throws Exception {
    systemProperties.set("tamboui.backend", "current");

    invokeRestoreTerminalBackendPreference("previous");

    assertThat(System.getProperty("tamboui.backend")).isEqualTo("previous");
  }

  @Test
  void runHeadlessSmokeLogsSuccessfulCatalogLoad() {
    WireMockServer wireMockServer = new WireMockServer(0);
    try {
      wireMockServer.start();
      CliCommandTestSupport.stubLiveMetadataWithMavenOnly(wireMockServer);
      CliCommandTestSupport.stubSingleRestExtensionCatalog(wireMockServer);
      RuntimeConfig runtimeConfig =
          CliCommandTestSupport.runtimeConfig(tempDir, URI.create(wireMockServer.baseUrl()));

      String stderr =
          captureStandardError(
              () ->
                  TuiBootstrapService.runHeadlessSmoke(
                      runtimeConfig, DiagnosticLogger.create(true)));

      assertThat(stderr)
          .contains("\"event\":\"tui.session.start\"")
          .contains("\"event\":\"catalog.load.start\"")
          .contains("\"event\":\"catalog.load.success\"")
          .contains("\"event\":\"tui.session.exit\"");
    } finally {
      wireMockServer.stop();
    }
  }

  @Test
  void runHeadlessSmokePropagatesCatalogLoadFailure() {
    WireMockServer wireMockServer = new WireMockServer(0);
    try {
      wireMockServer.start();
      RuntimeConfig runtimeConfig =
          CliCommandTestSupport.runtimeConfig(tempDir, URI.create(wireMockServer.baseUrl()));

      String stderr =
          captureStandardError(
              () ->
                  assertThatThrownBy(
                          () ->
                              TuiBootstrapService.runHeadlessSmoke(
                                  runtimeConfig, DiagnosticLogger.create(true)))
                      .isInstanceOf(java.util.concurrent.CompletionException.class));

      assertThat(stderr)
          .contains("\"event\":\"catalog.load.failure\"")
          .contains("\"mode\":\"headless-smoke\"");
    } finally {
      wireMockServer.stop();
    }
  }

  private static void invokeConfigureTerminalBackendPreference(
      String propertyValue, String envValue, String osName) throws Exception {
    Method method =
        backendPreferenceClass()
            .getDeclaredMethod("configure", String.class, String.class, String.class);
    method.setAccessible(true);
    method.invoke(null, propertyValue, envValue, osName);
  }

  private static void invokeConfigureTerminalBackendPreference() throws Exception {
    Method method = backendPreferenceClass().getDeclaredMethod("configure");
    method.setAccessible(true);
    method.invoke(null);
  }

  private static boolean invokeIsBackendPreferenceExplicitlyConfigured(
      String propertyValue, String envValue) throws Exception {
    Method method =
        backendPreferenceClass()
            .getDeclaredMethod("isExplicitlyConfigured", String.class, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, propertyValue, envValue);
  }

  private static String invokeDefaultBackendPreference(String osName) throws Exception {
    Method method =
        backendPreferenceClass().getDeclaredMethod("defaultBackendPreference", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, osName);
  }

  private static String invokeResolvePresetStreamKey(
      String currentStreamKey, dev.ayagmar.quarkusforge.api.MetadataDto metadata) throws Exception {
    Method method =
        TuiBootstrapService.class.getDeclaredMethod(
            "resolvePresetStreamKey", String.class, dev.ayagmar.quarkusforge.api.MetadataDto.class);
    method.setAccessible(true);
    return (String) method.invoke(null, currentStreamKey, metadata);
  }

  private static void invokeRestoreTerminalBackendPreference(String previousBackendPreference)
      throws Exception {
    Method method = backendPreferenceClass().getDeclaredMethod("restore", String.class);
    method.setAccessible(true);
    method.invoke(null, previousBackendPreference);
  }

  private static Class<?> backendPreferenceClass() throws ClassNotFoundException {
    return Class.forName("dev.ayagmar.quarkusforge.runtime.TerminalBackendPreference");
  }

  private static String captureStandardError(ThrowingRunnable action) {
    PrintStream originalErr = System.err;
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    try {
      System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
      action.run();
      return stderr.toString(StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    } finally {
      System.setErr(originalErr);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
