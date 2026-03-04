package dev.ayagmar.quarkusforge.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OutputPathResolverTest {

  @Test
  void resolvesRelativePathsToAbsoluteNormalizedPath() {
    Path resolved = OutputPathResolver.resolveOutputRoot("./generated");

    assertThat(resolved).isEqualTo(Path.of("./generated").toAbsolutePath().normalize());
  }

  @Test
  void expandsTildePrefixAgainstUserHome() {
    Path homePath = Path.of("target", "qf-home").toAbsolutePath().normalize();
    withSystemProperty(
        "user.home",
        homePath.toString(),
        () ->
            assertThat(OutputPathResolver.resolveOutputRoot("~/Projects/Quarkus"))
                .isEqualTo(homePath.resolve("Projects").resolve("Quarkus").normalize()));
  }

  @Test
  void expandsBareTildeToHomeDirectory() {
    Path homePath = Path.of("target", "qf-home-bare").toAbsolutePath().normalize();
    withSystemProperty(
        "user.home",
        homePath.toString(),
        () -> assertThat(OutputPathResolver.resolveOutputRoot("~")).isEqualTo(homePath));
  }

  @Test
  void expandsWindowsStyleTildePrefixAgainstUserHome() {
    Path homePath = Path.of("target", "qf-home-win").toAbsolutePath().normalize();
    withSystemProperty(
        "user.home",
        homePath.toString(),
        () -> {
          String output = "~\\Projects\\Quarkus";
          Path expected =
              Path.of(homePath.toString() + output.substring(1)).toAbsolutePath().normalize();
          assertThat(OutputPathResolver.resolveOutputRoot(output)).isEqualTo(expected);
        });
  }

  @Test
  void absoluteDisplayPathFallsBackToOriginalInputWhenPathIsInvalid() {
    withSystemProperty(
        "user.home",
        "\0bad-home",
        () -> assertThat(OutputPathResolver.absoluteDisplayPath("~")).isEqualTo("~"));
  }

  @Test
  void absoluteDisplayPathResolvesNullInputToCurrentWorkingDirectory() {
    assertThat(OutputPathResolver.absoluteDisplayPath(null))
        .isEqualTo(Path.of("").toAbsolutePath().normalize().toString());
  }

  private static void withSystemProperty(String key, String value, Runnable runnable) {
    String previous = System.getProperty(key);
    try {
      System.setProperty(key, value);
      runnable.run();
    } finally {
      if (previous == null) {
        System.clearProperty(key);
      } else {
        System.setProperty(key, previous);
      }
    }
  }
}
