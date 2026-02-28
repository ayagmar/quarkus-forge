package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.ui.AppKeyActions;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.bindings.Bindings;
import dev.tamboui.tui.bindings.KeyTrigger;

final class AppBindingsProfile {
  private AppBindingsProfile() {}

  static Bindings bindings() {
    return BindingSets.standard().toBuilder()
        .bind(KeyTrigger.ch('j'), Actions.MOVE_DOWN)
        .bind(KeyTrigger.ch('k'), Actions.MOVE_UP)
        .bind(KeyTrigger.ch('h'), Actions.MOVE_LEFT)
        .bind(KeyTrigger.ch('l'), Actions.MOVE_RIGHT)
        .bind(KeyTrigger.ch('g'), Actions.HOME)
        .bind(KeyTrigger.ch('G'), Actions.END)
        .bind(KeyTrigger.ch('/'), AppKeyActions.FOCUS_EXTENSION_SEARCH)
        .bind(KeyTrigger.ctrl('f'), AppKeyActions.FOCUS_EXTENSION_SEARCH)
        .bind(KeyTrigger.ctrl('l'), AppKeyActions.FOCUS_EXTENSION_LIST)
        .bind(KeyTrigger.ctrl('k'), AppKeyActions.TOGGLE_FAVORITES_FILTER)
        .bind(KeyTrigger.ctrl('j'), AppKeyActions.JUMP_TO_FAVORITE)
        .bind(KeyTrigger.ctrl('r'), AppKeyActions.RELOAD_CATALOG)
        .bind(KeyTrigger.ctrl('e'), AppKeyActions.TOGGLE_ERROR_DETAILS)
        .bind(KeyTrigger.ch('v'), AppKeyActions.CATEGORY_FILTER_CYCLE)
        .bind(KeyTrigger.ch('c'), AppKeyActions.TOGGLE_CATEGORY)
        .bind(KeyTrigger.ch('C'), AppKeyActions.OPEN_ALL_CATEGORIES)
        .bind(KeyTrigger.ch('x'), AppKeyActions.CLEAR_SELECTED_EXTENSIONS)
        .bind(KeyTrigger.ch('f'), AppKeyActions.FAVORITE_TOGGLE)
        .bind(KeyTrigger.ch('?'), AppKeyActions.OPEN_HELP)
        .bind(KeyTrigger.ctrl('p'), AppKeyActions.OPEN_COMMAND_PALETTE)
        .bind(KeyTrigger.alt('g'), AppKeyActions.SUBMIT_GENERATION)
        .build();
  }
}
