package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigTest {
  @TempDir Path tempDir;

  @Test
  void acceptsHttpsApiBaseUri() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("https://code.quarkus.io"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    assertThat(runtimeConfig.apiBaseUri()).isEqualTo(URI.create("https://code.quarkus.io"));
  }

  @Test
  void acceptsLoopbackHttpApiBaseUriForTests() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("http://127.0.0.1:8080"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));

    assertThat(runtimeConfig.apiBaseUri()).isEqualTo(URI.create("http://127.0.0.1:8080"));
  }

  @Test
  void rejectsNonHttpsRemoteApiBaseUri() {
    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("http://example.com"),
                    tempDir.resolve("catalog-cache.json"),
                    tempDir.resolve("favorites.json"),
                    tempDir.resolve("preferences.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use https unless it targets localhost or a loopback address");
  }

  @Test
  void rejectsHostsThatOnlyStartWith127ButAreNotLoopbackIpv4Addresses() {
    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("http://127.example.com:8080"),
                    tempDir.resolve("catalog-cache.json"),
                    tempDir.resolve("favorites.json"),
                    tempDir.resolve("preferences.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use https unless it targets localhost or a loopback address");
  }

  @Test
  void acceptsLocalhostAndIpv6LoopbackHttpApiBaseUriForTests() {
    RuntimeConfig localhostConfig =
        new RuntimeConfig(
            URI.create("http://localhost:8080/api"),
            tempDir.resolve("catalog-cache.json"),
            tempDir.resolve("favorites.json"),
            tempDir.resolve("preferences.json"));
    RuntimeConfig ipv6LoopbackConfig =
        new RuntimeConfig(
            URI.create("http://[::1]:8080/api"),
            tempDir.resolve("catalog-cache-2.json"),
            tempDir.resolve("favorites-2.json"),
            tempDir.resolve("preferences-2.json"));
    RuntimeConfig expandedIpv6LoopbackConfig =
        new RuntimeConfig(
            URI.create("http://[0:0:0:0:0:0:0:1]:8080/api"),
            tempDir.resolve("catalog-cache-3.json"),
            tempDir.resolve("favorites-3.json"),
            tempDir.resolve("preferences-3.json"));

    assertThat(localhostConfig.apiBaseUri()).isEqualTo(URI.create("http://localhost:8080/api"));
    assertThat(ipv6LoopbackConfig.apiBaseUri()).isEqualTo(URI.create("http://[::1]:8080/api"));
    assertThat(expandedIpv6LoopbackConfig.apiBaseUri())
        .isEqualTo(URI.create("http://[0:0:0:0:0:0:0:1]:8080/api"));
  }

  @Test
  void rejectsApiBaseUriWithUserInfo() {
    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("https://user:pass@example.com"),
                    tempDir.resolve("catalog-cache.json"),
                    tempDir.resolve("favorites.json"),
                    tempDir.resolve("preferences.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain user info");
  }

  @Test
  void rejectsApiBaseUriWithQueryOrFragment() {
    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("https://code.quarkus.io?draft=true"),
                    tempDir.resolve("catalog-cache.json"),
                    tempDir.resolve("favorites.json"),
                    tempDir.resolve("preferences.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain query or fragment data");

    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("https://code.quarkus.io#fragment"),
                    tempDir.resolve("catalog-cache-2.json"),
                    tempDir.resolve("favorites-2.json"),
                    tempDir.resolve("preferences-2.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not contain query or fragment data");
  }

  @Test
  void rejectsRelativeOrUnsupportedApiBaseUri() {
    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("/relative"),
                    tempDir.resolve("catalog-cache.json"),
                    tempDir.resolve("favorites.json"),
                    tempDir.resolve("preferences.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be absolute");

    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("ftp://code.quarkus.io"),
                    tempDir.resolve("catalog-cache-2.json"),
                    tempDir.resolve("favorites-2.json"),
                    tempDir.resolve("preferences-2.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must use http or https");
  }

  @Test
  void rejectsApiBaseUriWithoutHost() {
    assertThatThrownBy(
            () ->
                new RuntimeConfig(
                    URI.create("https:/missing-host"),
                    tempDir.resolve("catalog-cache.json"),
                    tempDir.resolve("favorites.json"),
                    tempDir.resolve("preferences.json")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must include a host");
  }

  @Test
  void loopbackHelpersRecognizeOnlyValidLoopbackHosts() throws Exception {
    assertThat(invokeBooleanHelper("isLoopbackHost", "127.0.0.1")).isTrue();
    assertThat(invokeBooleanHelper("isLoopbackHost", "127.example.com")).isFalse();
    assertThat(invokeBooleanHelper("isLoopbackHost", "[::1]")).isTrue();
    assertThat(invokeBooleanHelper("isLoopbackHost", "0:0:0:0:0:0:0:1")).isTrue();
    assertThat(invokeBooleanHelper("isLoopbackHost", "[0:0:0:0:0:0:0:1]")).isTrue();
    assertThat(invokeBooleanHelper("isLoopbackHost", "[::1")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4LoopbackHost", "127.255.255.255")).isTrue();
    assertThat(invokeBooleanHelper("isIpv4LoopbackHost", "126.0.0.1")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4LoopbackHost", "127.0.0")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4LoopbackHost", "127.0.999.1")).isFalse();
  }

  @Test
  void ipv4OctetHelperRejectsEmptyNonDigitOutOfRangeAndOverflowValues() throws Exception {
    assertThat(invokeBooleanHelper("isIpv4Octet", "")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4Octet", "12a")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4Octet", "256")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4Octet", "999999999999999999999")).isFalse();
    assertThat(invokeBooleanHelper("isIpv4Octet", "42")).isTrue();
  }

  @Test
  void normalizesApiBaseUriAndFilePaths() {
    RuntimeConfig runtimeConfig =
        new RuntimeConfig(
            URI.create("https://code.quarkus.io/api/../catalog"),
            Path.of(".").resolve("target/../catalog-cache.json"),
            Path.of(".").resolve("target/../favorites.json"),
            Path.of(".").resolve("target/../preferences.json"));

    assertThat(runtimeConfig.apiBaseUri()).isEqualTo(URI.create("https://code.quarkus.io/catalog"));
    assertThat(runtimeConfig.catalogCacheFile()).isAbsolute();
    assertThat(runtimeConfig.catalogCacheFile().getFileName()).hasToString("catalog-cache.json");
    assertThat(runtimeConfig.favoritesFile()).isAbsolute();
    assertThat(runtimeConfig.favoritesFile().getFileName()).hasToString("favorites.json");
    assertThat(runtimeConfig.preferencesFile()).isAbsolute();
    assertThat(runtimeConfig.preferencesFile().getFileName()).hasToString("preferences.json");
  }

  private static boolean invokeBooleanHelper(String methodName, String value) throws Exception {
    Method method = RuntimeConfig.class.getDeclaredMethod(methodName, String.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, value);
  }
}
