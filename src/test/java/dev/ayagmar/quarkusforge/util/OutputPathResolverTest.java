package dev.ayagmar.quarkusforge.util;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class OutputPathResolverTest {
  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  @Test
  void resolvesRelativePathsToAbsoluteNormalizedPath() {
    Path resolved = OutputPathResolver.resolveOutputRoot("./generated");

    assertThat(resolved).isEqualTo(Path.of("./generated").toAbsolutePath().normalize());
  }

  @Test
  void expandsTildePrefixAgainstUserHome() {
    Path homePath = Path.of("target", "qf-home").toAbsolutePath().normalize();
    systemProperties.set("user.home", homePath);

    assertThat(OutputPathResolver.resolveOutputRoot("~/Projects/Quarkus"))
        .isEqualTo(homePath.resolve("Projects").resolve("Quarkus").normalize());
  }

  @Test
  void expandsBareTildeToHomeDirectory() {
    Path homePath = Path.of("target", "qf-home-bare").toAbsolutePath().normalize();
    systemProperties.set("user.home", homePath);

    assertThat(OutputPathResolver.resolveOutputRoot("~")).isEqualTo(homePath);
  }

  @Test
  void expandsWindowsStyleTildePrefixAgainstUserHome() {
    Path homePath = Path.of("target", "qf-home-win").toAbsolutePath().normalize();
    systemProperties.set("user.home", homePath);

    String output = "~\\Projects\\Quarkus";
    Path expected = homePath.resolve("Projects").resolve("Quarkus").normalize();
    assertThat(OutputPathResolver.resolveOutputRoot(output)).isEqualTo(expected);
  }

  @Test
  void absoluteDisplayPathFallsBackToOriginalInputWhenPathIsInvalid() {
    systemProperties.set("user.home", "\0bad-home");

    assertThat(OutputPathResolver.absoluteDisplayPath("~")).isEqualTo("~");
  }

  @Test
  void absoluteDisplayPathResolvesNullInputToCurrentWorkingDirectory() {
    assertThat(OutputPathResolver.absoluteDisplayPath(null))
        .isEqualTo(Path.of("").toAbsolutePath().normalize().toString());
  }

  @Test
  void resolvesGeneratedProjectDirectoryFromRequest() {
    ProjectRequest request =
        new ProjectRequest(
            "org.acme",
            "demo",
            "1.0.0-SNAPSHOT",
            "org.acme.demo",
            "./output",
            "3.20",
            "maven",
            "21");

    assertThat(OutputPathResolver.resolveGeneratedProjectDirectory(request))
        .isEqualTo(Path.of("./output").toAbsolutePath().normalize().resolve("demo"));
  }
}
