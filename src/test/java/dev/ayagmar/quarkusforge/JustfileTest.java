package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
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

    assertRecipeRunsScript(justfile, "test-unit:", "scripts/verify/unit.sh");
    assertRecipeRunsScript(justfile, "test:", "scripts/verify/verify.sh");
    assertRecipeRunsScript(justfile, "format-check:", "scripts/verify/format-check.sh");
    assertRecipeRunsScript(justfile, "headless-check:", "scripts/verify/headless-compile.sh");
    assertRecipeRunsScript(justfile, "coverage:", "scripts/verify/coverage.sh");
    assertRecipeRunsScript(justfile, "docs-build:", "scripts/verify/docs-build.sh");
    assertRecipeRunsScript(justfile, "docs-linkcheck:", "scripts/verify/docs-linkcheck.sh");
    assertRecipeRunsScript(justfile, "native-size mode:", "scripts/verify/native-size.sh {{mode}}");
    assertRecipeRunsScript(
        justfile,
        "native-smoke-posix binary:",
        "scripts/verify/native-interactive-smoke-posix.sh {{binary}}");
    assertRecipeRunsScript(
        justfile,
        "native-smoke-windows binary:",
        "scripts/verify/native-interactive-smoke-windows.sh {{binary}}");
  }

  private static void assertRecipeRunsScript(String justfile, String recipe, String script) {
    Pattern pattern =
        Pattern.compile(
            "(?m)^"
                + Pattern.quote(recipe)
                + "\\R(?:    .*\\R)*?    "
                + Pattern.quote(script)
                + "$");
    assertThat(pattern.matcher(justfile).find()).isTrue();
  }
}
