package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.bindings.Bindings;
import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.KeyModifiers;
import org.junit.jupiter.api.Test;

class AppKeyActionsTest {
  @Test
  void keyChecksPreserveRawShortcutParity() {
    assertThat(AppKeyActions.isCatalogReloadKey(KeyEvent.ofChar('r', KeyModifiers.CTRL))).isTrue();
    assertThat(AppKeyActions.isGenerateShortcutKey(KeyEvent.ofChar('g', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isFavoriteToggleKey(KeyEvent.ofChar('f'))).isTrue();
    assertThat(AppKeyActions.isSelectedOnlyFilterToggleKey(KeyEvent.ofChar('s', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isSelectedOnlyFilterToggleKey(KeyEvent.ofChar('S', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isCategoryCollapseToggleKey(KeyEvent.ofChar('C'))).isFalse();
    assertThat(AppKeyActions.isCategoryCollapseToggleKey(KeyEvent.ofChar('c'))).isTrue();
    assertThat(AppKeyActions.isExpandAllCategoriesKey(KeyEvent.ofChar('C'))).isTrue();
    assertThat(AppKeyActions.isFocusExtensionSearchSlashKey(KeyEvent.ofChar('/'))).isTrue();
    assertThat(
            AppKeyActions.isFocusExtensionSearchSlashKey(KeyEvent.ofChar('/', KeyModifiers.CTRL)))
        .isFalse();
    assertThat(AppKeyActions.isNextInvalidFieldKey(KeyEvent.ofChar('N', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isNextInvalidFieldKey(KeyEvent.ofChar('n', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isPreviousInvalidFieldKey(KeyEvent.ofChar('P', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isPreviousInvalidFieldKey(KeyEvent.ofChar('p', KeyModifiers.ALT)))
        .isTrue();
    assertThat(AppKeyActions.isGenerateShortcutKey(KeyEvent.ofChar('g', KeyModifiers.CTRL)))
        .isFalse();
    assertThat(AppKeyActions.isHelpOverlayToggleKey(KeyEvent.ofChar('?'))).isTrue();
  }

  @Test
  void keyChecksSupportSemanticActionsFromBindings() {
    Bindings bindings =
        BindingSets.standard().toBuilder()
            .bind(KeyTrigger.ctrl('z'), AppKeyActions.RELOAD_CATALOG)
            .build();

    KeyEvent reboundShortcut = KeyEvent.ofChar('z', KeyModifiers.CTRL, bindings);

    assertThat(AppKeyActions.isCatalogReloadKey(reboundShortcut)).isTrue();

    Bindings selectedFilterBinding =
        BindingSets.standard().toBuilder()
            .bind(KeyTrigger.ctrl('z'), AppKeyActions.TOGGLE_SELECTED_FILTER)
            .build();

    KeyEvent selectedFilterActionKey =
        KeyEvent.ofChar('z', KeyModifiers.CTRL, selectedFilterBinding);

    assertThat(AppKeyActions.isSelectedOnlyFilterToggleKey(selectedFilterActionKey)).isTrue();
  }
}
