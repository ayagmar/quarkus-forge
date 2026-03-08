package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DocsPagesWorkflowTest {

  @Test
  void docsPagesWorkflowUsesSharedDocsVerificationScripts() throws Exception {
    String workflow =
        Files.readString(Path.of(".github", "workflows", "docs-pages.yml"), StandardCharsets.UTF_8);

    assertThat(workflow)
        .contains("scripts/verify/docs-build.sh", "scripts/verify/docs-linkcheck.sh");
  }
}
