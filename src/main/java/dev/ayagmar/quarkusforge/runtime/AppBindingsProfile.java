package dev.ayagmar.quarkusforge.runtime;

import dev.ayagmar.quarkusforge.ui.AppKeyActions;
import dev.tamboui.tui.bindings.BindingSets;
import dev.tamboui.tui.bindings.Bindings;
import dev.tamboui.tui.bindings.KeyTrigger;

final class AppBindingsProfile {
  private AppBindingsProfile() {}

  static Bindings bindings() {
    return BindingSets.vim().toBuilder()
        .bind(KeyTrigger.ch('/'), AppKeyActions.FOCUS_EXTENSION_SEARCH)
        .bind(KeyTrigger.ctrl('f'), AppKeyActions.FOCUS_EXTENSION_SEARCH)
        .bind(KeyTrigger.ctrl('F'), AppKeyActions.FOCUS_EXTENSION_SEARCH)
        .bind(KeyTrigger.ctrl('l'), AppKeyActions.FOCUS_EXTENSION_LIST)
        .bind(KeyTrigger.ctrl('L'), AppKeyActions.FOCUS_EXTENSION_LIST)
        .bind(KeyTrigger.ctrl('k'), AppKeyActions.TOGGLE_FAVORITES_FILTER)
        .bind(KeyTrigger.ctrl('K'), AppKeyActions.TOGGLE_FAVORITES_FILTER)
        .bind(KeyTrigger.ctrl('y'), AppKeyActions.CYCLE_PRESET_FILTER)
        .bind(KeyTrigger.ctrl('Y'), AppKeyActions.CYCLE_PRESET_FILTER)
        .bind(KeyTrigger.ctrl('j'), AppKeyActions.JUMP_TO_FAVORITE)
        .bind(KeyTrigger.ctrl('J'), AppKeyActions.JUMP_TO_FAVORITE)
        .bind(KeyTrigger.ctrl('r'), AppKeyActions.RELOAD_CATALOG)
        .bind(KeyTrigger.ctrl('R'), AppKeyActions.RELOAD_CATALOG)
        .bind(KeyTrigger.ctrl('e'), AppKeyActions.TOGGLE_ERROR_DETAILS)
        .bind(KeyTrigger.ctrl('E'), AppKeyActions.TOGGLE_ERROR_DETAILS)
        .bind(KeyTrigger.chIgnoreCase('v'), AppKeyActions.CATEGORY_FILTER_CYCLE)
        .bind(KeyTrigger.ch('c'), AppKeyActions.TOGGLE_CATEGORY)
        .bind(KeyTrigger.ch('C'), AppKeyActions.OPEN_ALL_CATEGORIES)
        .bind(KeyTrigger.chIgnoreCase('x'), AppKeyActions.CLEAR_SELECTED_EXTENSIONS)
        .bind(KeyTrigger.chIgnoreCase('f'), AppKeyActions.FAVORITE_TOGGLE)
        .bind(KeyTrigger.ch('?'), AppKeyActions.OPEN_HELP)
        .bind(KeyTrigger.ctrl('p'), AppKeyActions.OPEN_COMMAND_PALETTE)
        .bind(KeyTrigger.ctrl('P'), AppKeyActions.OPEN_COMMAND_PALETTE)
        .bind(KeyTrigger.alt('g'), AppKeyActions.SUBMIT_GENERATION)
        .bind(KeyTrigger.alt('G'), AppKeyActions.SUBMIT_GENERATION)
        .bind(KeyTrigger.alt('s'), AppKeyActions.TOGGLE_SELECTED_FILTER)
        .bind(KeyTrigger.alt('S'), AppKeyActions.TOGGLE_SELECTED_FILTER)
        .bind(KeyTrigger.alt('n'), AppKeyActions.NEXT_INVALID_FIELD)
        .bind(KeyTrigger.alt('N'), AppKeyActions.NEXT_INVALID_FIELD)
        .bind(KeyTrigger.alt('p'), AppKeyActions.PREVIOUS_INVALID_FIELD)
        .bind(KeyTrigger.alt('P'), AppKeyActions.PREVIOUS_INVALID_FIELD)
        .build();
  }
}
