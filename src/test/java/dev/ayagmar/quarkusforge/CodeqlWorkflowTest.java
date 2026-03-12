package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CodeqlWorkflowTest {

  @Test
  void codeqlWorkflowCompilesProjectBeforeAnalysis() throws Exception {
    String workflow = normalizedText(Path.of(".github", "workflows", "codeql.yml"));

    assertThat(workflow)
        .contains("name: CodeQL")
        .contains("security-events: write")
        .contains("uses: github/codeql-action/init@")
        .contains("languages: java-kotlin")
        .contains("build-mode: manual")
        .contains("./mvnw -q -DskipTests compile")
        .contains("uses: github/codeql-action/analyze@");
  }

  private static String normalizedText(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
