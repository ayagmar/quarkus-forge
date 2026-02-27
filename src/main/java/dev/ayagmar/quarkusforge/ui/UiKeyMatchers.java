package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

final class UiKeyMatchers {
  private UiKeyMatchers() {}

  static boolean isVimUpKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'k', 'K');
  }

  static boolean isVimDownKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'j', 'J');
  }

  static boolean isVimLeftKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'h', 'H');
  }

  static boolean isVimRightKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'l', 'L');
  }

  static boolean isVimHomeKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'g', 'g');
  }

  static boolean isVimEndKey(KeyEvent keyEvent) {
    return isPlainChar(keyEvent, 'G', 'G');
  }

  static boolean isPlainChar(KeyEvent keyEvent, char lower, char upper) {
    return keyEvent.code() == KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == lower || keyEvent.character() == upper);
  }

  static boolean isDigitKey(KeyEvent keyEvent) {
    return keyEvent.code() == KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && Character.isDigit(keyEvent.character());
  }
}
