package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseWorkflowTest {

  @Test
  void releaseWorkflowRunsLinuxInteractiveNativeSmoke() throws Exception {
    String workflow =
        Files.readString(Path.of(".github", "workflows", "release.yml"), StandardCharsets.UTF_8);

    assertThat(workflow)
        .contains("if: runner.os == 'Linux' && matrix.binary_name == 'quarkus-forge'");
    assertThat(workflow)
        .contains("python3 scripts/interactive_native_smoke.py --binary \"$binary\"");
    assertThat(workflow).contains("\"$binary\" --help > /dev/null");
    assertThat(workflow).contains("\"$binary\" --version > /dev/null");
  }
}
