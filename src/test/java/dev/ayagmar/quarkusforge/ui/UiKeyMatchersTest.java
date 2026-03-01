package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

class UiKeyMatchersTest {
  @Test
  void plainCharMatchesOnlyCharWithoutModifiers() {
    assertThat(UiKeyMatchers.isPlainChar(KeyEvent.ofChar('k'), 'k', 'K')).isTrue();
    assertThat(UiKeyMatchers.isPlainChar(KeyEvent.ofChar('K'), 'k', 'K')).isTrue();
    assertThat(UiKeyMatchers.isPlainChar(KeyEvent.ofChar('k', KeyModifiers.CTRL), 'k', 'K'))
        .isFalse();
    assertThat(UiKeyMatchers.isPlainChar(KeyEvent.ofChar('k', KeyModifiers.ALT), 'k', 'K'))
        .isFalse();
    assertThat(UiKeyMatchers.isPlainChar(KeyEvent.ofKey(KeyCode.UP), 'k', 'K')).isFalse();
  }

  @Test
  void vimMovementChecksMatchConfiguredCharacters() {
    assertThat(UiKeyMatchers.isVimUpKey(KeyEvent.ofChar('k'))).isTrue();
    assertThat(UiKeyMatchers.isVimDownKey(KeyEvent.ofChar('j'))).isTrue();
    assertThat(UiKeyMatchers.isVimLeftKey(KeyEvent.ofChar('h'))).isTrue();
    assertThat(UiKeyMatchers.isVimRightKey(KeyEvent.ofChar('l'))).isTrue();
    assertThat(UiKeyMatchers.isVimHomeKey(KeyEvent.ofChar('g'))).isTrue();
    assertThat(UiKeyMatchers.isVimEndKey(KeyEvent.ofChar('G'))).isTrue();
    assertThat(UiKeyMatchers.isVimUpKey(KeyEvent.ofChar('k', KeyModifiers.CTRL))).isFalse();
  }

  @Test
  void digitKeyRequiresPlainCharacterDigit() {
    assertThat(UiKeyMatchers.isDigitKey(KeyEvent.ofChar('7'))).isTrue();
    assertThat(UiKeyMatchers.isDigitKey(KeyEvent.ofChar('7', KeyModifiers.CTRL))).isFalse();
    assertThat(UiKeyMatchers.isDigitKey(KeyEvent.ofChar('x'))).isFalse();
    assertThat(UiKeyMatchers.isDigitKey(KeyEvent.ofKey(KeyCode.DOWN))).isFalse();
  }
}
