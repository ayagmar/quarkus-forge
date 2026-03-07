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
    assertThat(justfile).contains("failsafe:integration-test failsafe:verify");
    assertThat(justfile).doesNotContain("test-it:\n    {{mvn}} verify -DskipTests");
  }
}
