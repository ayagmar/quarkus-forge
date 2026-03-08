package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseWorkflowTest {

  @Test
  void releaseWorkflowRunsPosixInteractiveNativeSmoke() throws Exception {
    String workflow =
        Files.readString(Path.of(".github", "workflows", "release.yml"), StandardCharsets.UTF_8);

    assertThat(workflow)
        .contains("if: runner.os != 'Windows' && matrix.binary_name == 'quarkus-forge'");
    assertThat(workflow)
        .contains("python3 scripts/interactive_native_smoke.py --binary \"$binary\"");
    assertThat(workflow).contains("\"$binary\" --help > /dev/null");
    assertThat(workflow).contains("\"$binary\" --version > /dev/null");
  }

  @Test
  void releaseWorkflowRunsWindowsInteractiveNativeSmoke() throws Exception {
    String workflow =
        Files.readString(Path.of(".github", "workflows", "release.yml"), StandardCharsets.UTF_8);

    assertThat(workflow)
        .contains("if: runner.os == 'Windows' && matrix.binary_name == 'quarkus-forge'");
    assertThat(workflow)
        .contains("if ! \"$binary\" --interactive-smoke-test --verbose 2> \"$log_path\"; then");
    assertThat(workflow).contains("grep -F '\"event\":\"tui.render.ready\"'");
    assertThat(workflow).contains("cat \"$log_path\"");
  }
}
