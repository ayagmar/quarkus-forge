package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CiWorkflowTest {

  @Test
  void ciWorkflowUsesSharedVerificationEntrypoints() throws Exception {
    String workflow = normalizedText(Path.of(".github", "workflows", "ci.yml"));

    assertThat(workflow).contains("scripts/verify/docs-build.sh");
    assertThat(workflow).contains("scripts/verify/docs-linkcheck.sh");
    assertThat(workflow).contains("scripts/verify/format-check.sh");
    assertThat(workflow).contains("scripts/verify/headless-compile.sh");
    assertThat(workflow).contains("scripts/verify/verify.sh");
    assertThat(workflow).contains("scripts/verify/coverage.sh");
    assertThat(workflow).contains("scripts/verify/native-size.sh headless");
    assertThat(workflow).contains("scripts/verify/native-size.sh interactive");
    assertThat(workflow).contains("name: native-size-reports");
    assertThat(workflow).contains("target/quarkus-forge-build-report.html");
    assertThat(workflow).contains("target/quarkus-forge-headless-build-report.html");
    assertThat(workflow).contains("ci-status:\n    name: CI Status\n    if: always()");
    assertThat(workflow).contains("needs: [quality, tests, coverage, native-size]");
    assertThat(workflow).contains("needs.native-size.result");
  }

  private static String normalizedText(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
