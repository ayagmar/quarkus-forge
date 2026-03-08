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

    assertThat(workflow)
        .contains("scripts/verify/docs-build.sh")
        .contains("scripts/verify/docs-linkcheck.sh")
        .contains("scripts/verify/format-check.sh")
        .contains("scripts/verify/headless-compile.sh")
        .contains("scripts/verify/verify.sh")
        .contains("scripts/verify/coverage.sh")
        .contains("scripts/verify/native-size.sh headless")
        .contains("scripts/verify/native-size.sh interactive")
        .contains("name: native-size-reports")
        .contains("target/quarkus-forge-build-report.html")
        .contains("target/quarkus-forge-headless-build-report.html")
        .contains("ci-status:\n    name: CI Status\n    if: always()")
        .contains("needs: [quality, tests, coverage, native-size]")
        .contains("needs.native-size.result");
  }

  private static String normalizedText(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
