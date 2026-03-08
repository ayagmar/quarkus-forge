package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.cli.GenerateCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeadlessGenerationInputsTest {
  @TempDir Path tempDir;

  @Test
  void fromCommandLoadsTemplateAndMergesSelections() throws Exception {
    Path forgefilePath = tempDir.resolve("team.forgefile.json");
    Files.writeString(
        forgefilePath,
        """
        {
          "groupId": "com.team",
          "artifactId": "team-svc",
          "version": "2.0.0",
          "buildTool": "maven",
          "javaVersion": "21",
          "presets": ["web"],
          "extensions": ["io.quarkus:quarkus-rest"]
        }
        """);
    GenerateCommand command = new GenerateCommand();
    command.setFromFile(forgefilePath.toString());
    command.presets().add("favorites");
    command.extensions().add("io.quarkus:quarkus-arc");

    HeadlessGenerationInputs inputs = HeadlessGenerationInputs.fromCommand(command);

    assertThat(inputs.template().groupId()).isEqualTo("com.team");
    assertThat(inputs.template().artifactId()).isEqualTo("team-svc");
    assertThat(inputs.template().version()).isEqualTo("2.0.0");
    assertThat(inputs.presetInputs()).containsExactly("web", "favorites");
    assertThat(inputs.extensionInputs())
        .containsExactly("io.quarkus:quarkus-rest", "io.quarkus:quarkus-arc");
    assertThat(inputs.forgefilePath()).isEqualTo(forgefilePath.toAbsolutePath().normalize());
  }

  @Test
  void fromCommandRejectsLockCheckWithoutLockedForgefile() throws Exception {
    Path forgefilePath = tempDir.resolve("team.forgefile.json");
    Files.writeString(
        forgefilePath,
        """
        {
          "artifactId": "team-svc",
          "extensions": []
        }
        """);
    GenerateCommand command = new GenerateCommand();
    command.setFromFile(forgefilePath.toString());
    command.setLockCheck(true);

    assertThatThrownBy(() -> HeadlessGenerationInputs.fromCommand(command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("--lock-check requires --from pointing to a Forgefile with a locked section");
  }

  @Test
  void fromCommandResolvesRelativeSaveAsPathsUnderRecipesRoot() {
    GenerateCommand command = new GenerateCommand();
    command.setSaveAsFile("saved.forgefile.json");

    HeadlessGenerationInputs inputs = HeadlessGenerationInputs.fromCommand(command);

    assertThat(inputs.saveAsFile())
        .isEqualTo(
            ForgeDataPaths.recipesRoot()
                .resolve("saved.forgefile.json")
                .toAbsolutePath()
                .normalize());
  }
}
