package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JustfileTest {

  @Test
  void testItRecipeRunsFailsafeGoalsWithoutSkipTests() throws Exception {
    String justfile = Files.readString(Path.of("justfile"), StandardCharsets.UTF_8);

    assertThat(justfile).contains("test-it:");
    assertThat(justfile).contains("scripts/verify/integration.sh");
    assertThat(justfile).doesNotContain("test-it:\n    {{mvn}} verify -DskipTests");
  }

  @Test
  void verificationRecipesRouteThroughSharedScripts() throws Exception {
    String justfile = Files.readString(Path.of("justfile"), StandardCharsets.UTF_8);

    assertThat(justfile).contains("test-unit:");
    assertThat(justfile).contains("scripts/verify/unit.sh");
    assertThat(justfile).contains("test:");
    assertThat(justfile).contains("scripts/verify/verify.sh");
    assertThat(justfile).contains("format-check:");
    assertThat(justfile).contains("scripts/verify/format-check.sh");
    assertThat(justfile).contains("headless-check:");
    assertThat(justfile).contains("scripts/verify/headless-compile.sh");
    assertThat(justfile).contains("docs-build:");
    assertThat(justfile).contains("scripts/verify/docs-build.sh");
    assertThat(justfile).contains("native-smoke-posix binary:");
    assertThat(justfile).contains("scripts/verify/native-interactive-smoke-posix.sh {{binary}}");
  }
}
