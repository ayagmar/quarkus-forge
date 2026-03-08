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

    assertRecipeCalls(justfile, "test-it:", "scripts/verify/integration.sh");
    assertThat(justfile).doesNotContain("test-it:\n    {{mvn}} verify -DskipTests");
  }

  @Test
  void verificationRecipesRouteThroughSharedScripts() throws Exception {
    String justfile = Files.readString(Path.of("justfile"), StandardCharsets.UTF_8);

    assertRecipeCalls(justfile, "test-unit:", "scripts/verify/unit.sh");
    assertRecipeCalls(justfile, "test:", "scripts/verify/verify.sh");
    assertRecipeCalls(justfile, "format-check:", "scripts/verify/format-check.sh");
    assertRecipeCalls(justfile, "headless-check:", "scripts/verify/headless-compile.sh");
    assertRecipeCalls(justfile, "coverage:", "scripts/verify/coverage.sh");
    assertRecipeCalls(justfile, "docs-build:", "scripts/verify/docs-build.sh");
    assertRecipeCalls(justfile, "docs-linkcheck:", "scripts/verify/docs-linkcheck.sh");
    assertRecipeCalls(justfile, "native-size mode:", "scripts/verify/native-size.sh {{mode}}");
    assertRecipeCalls(
        justfile,
        "native-smoke-posix binary:",
        "scripts/verify/native-interactive-smoke-posix.sh {{binary}}");
    assertRecipeCalls(
        justfile,
        "native-smoke-windows binary:",
        "scripts/verify/native-interactive-smoke-windows.sh {{binary}}");
  }

  private static void assertRecipeCalls(String justfile, String recipe, String script) {
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
