package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CiWorkflowTest {

  @Test
  void ciWorkflowUsesSharedVerificationEntrypoints() throws Exception {
    String workflow =
        Files.readString(Path.of(".github", "workflows", "ci.yml"), StandardCharsets.UTF_8);

    assertThat(workflow).contains("scripts/verify/docs-build.sh");
    assertThat(workflow).contains("scripts/verify/docs-linkcheck.sh");
    assertThat(workflow).contains("scripts/verify/format-check.sh");
    assertThat(workflow).contains("scripts/verify/headless-compile.sh");
    assertThat(workflow).contains("scripts/verify/verify.sh");
    assertThat(workflow).contains("scripts/verify/coverage.sh");
    assertThat(workflow).contains("scripts/verify/native-size.sh headless");
    assertThat(workflow).contains("scripts/verify/native-size.sh interactive");
  }
}
