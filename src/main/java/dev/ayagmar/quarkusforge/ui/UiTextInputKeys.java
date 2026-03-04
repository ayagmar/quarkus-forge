package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

final class UiTextInputKeys {
  private UiTextInputKeys() {}

  static boolean isSupportedEditKey(KeyEvent event) {
    if (event.code() == KeyCode.CHAR && !event.hasCtrl() && !event.hasAlt()) {
      return true;
    }
    return event.isDeleteBackward()
        || event.isDeleteForward()
        || event.isLeft()
        || event.isRight()
        || event.isHome()
        || event.isEnd();
  }
}
