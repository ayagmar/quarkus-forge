package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

/** Shared key classification helpers used by menu/palette routing paths. */
final class UiKeyPredicates {
  private UiKeyPredicates() {}

  static boolean isDigitKey(KeyEvent keyEvent) {
    return keyEvent.code() == KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && Character.isDigit(keyEvent.codePoint());
  }
}
