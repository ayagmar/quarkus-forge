package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.style.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiThemeTest {
  private static final String THEME_PATH_PROPERTY = "quarkus.forge.theme";

  @TempDir Path tempDir;

  @AfterEach
  void resetThemeOverrideProperty() {
    System.clearProperty(THEME_PATH_PROPERTY);
  }

  @Test
  void loadDefaultUsesUserProvidedThemeOverrideFile() throws Exception {
    Path customTheme = tempDir.resolve("custom-theme.tcss");
    Files.writeString(
        customTheme,
        """
        accent = #010203
        focus = #0a0b0c
        """);
    System.setProperty(THEME_PATH_PROPERTY, customTheme.toString());

    UiTheme theme = UiTheme.loadDefault();

    assertThat(theme.color("accent")).isEqualTo(Color.hex("#010203"));
    assertThat(theme.color("focus")).isEqualTo(Color.hex("#0a0b0c"));
  }

  @Test
  void loadDefaultIgnoresMissingUserThemeOverrideFile() {
    System.setProperty(THEME_PATH_PROPERTY, tempDir.resolve("missing.tcss").toString());

    UiTheme theme = UiTheme.loadDefault();

    assertThat(theme.color("accent")).isEqualTo(Color.hex("#ff3b66"));
  }
}
