package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.style.Style;

final class UiInputStyles {
  private UiInputStyles() {}

  static Style focusedField(UiTheme theme, boolean hasError) {
    return Style.EMPTY.fg(hasError ? theme.color("error") : theme.color("focus")).bold();
  }

  static Style cursor(UiTheme theme) {
    // Strong visibility across terminals: explicit focus background + base foreground.
    return Style.EMPTY.fg(theme.color("base")).bg(theme.color("focus")).underlined().bold();
  }
}
