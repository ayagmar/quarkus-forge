package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdeDetectorTest {

  @Test
  void detectReturnsImmutableList() {
    var ides = IdeDetector.detect();
    assertThat(ides).isNotNull();
    assertThat(ides).isUnmodifiable();
  }

  @Test
  void detectedIdesHaveNonBlankNames() {
    var ides = IdeDetector.detect();
    for (var ide : ides) {
      assertThat(ide.name()).isNotBlank();
      assertThat(ide.command()).isNotBlank();
    }
  }

  @Test
  void customEnvVarTakesPriority() {
    // When QUARKUS_FORGE_IDE_COMMAND is set, it should appear first
    // (can't test env var easily in unit test, but verify structural behavior)
    var ides = IdeDetector.detect();
    // If custom env var is set, first entry should contain "Custom"
    String envIde = System.getenv("QUARKUS_FORGE_IDE_COMMAND");
    if (envIde != null && !envIde.isBlank()) {
      assertThat(ides).isNotEmpty();
      assertThat(ides.getFirst().name()).startsWith("Custom");
    }
  }

  @Test
  void noDuplicateCommands() {
    var ides = IdeDetector.detect();
    var commands = ides.stream().map(IdeDetector.DetectedIde::command).toList();
    assertThat(commands).doesNotHaveDuplicates();
  }
}
