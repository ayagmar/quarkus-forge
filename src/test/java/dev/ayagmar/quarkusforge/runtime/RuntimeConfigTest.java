package dev.ayagmar.quarkusforge.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
