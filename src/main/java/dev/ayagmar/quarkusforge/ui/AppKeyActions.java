package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

public final class AppKeyActions {
  public static final String FOCUS_EXTENSION_SEARCH = "app_focus_extension_search";
  public static final String FOCUS_EXTENSION_LIST = "app_focus_extension_list";
  public static final String TOGGLE_FAVORITES_FILTER = "app_toggle_favorites_filter";
  public static final String CYCLE_PRESET_FILTER = "app_cycle_preset_filter";
  public static final String JUMP_TO_FAVORITE = "app_jump_to_favorite";
  public static final String RELOAD_CATALOG = "app_reload_catalog";
  public static final String TOGGLE_ERROR_DETAILS = "app_toggle_error_details";
  public static final String CATEGORY_FILTER_CYCLE = "app_cycle_category_filter";
  public static final String TOGGLE_CATEGORY = "app_toggle_category";
  public static final String OPEN_ALL_CATEGORIES = "app_open_all_categories";
  public static final String CLEAR_SELECTED_EXTENSIONS = "app_clear_selected_extensions";
  public static final String FAVORITE_TOGGLE = "app_toggle_favorite";
  public static final String OPEN_HELP = "app_help";
  public static final String OPEN_COMMAND_PALETTE = "app_command_palette";
  public static final String SUBMIT_GENERATION = "app_submit_generation";

  private AppKeyActions() {}

  static boolean isCatalogReloadKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, RELOAD_CATALOG) || isCtrlChar(keyEvent, 'r', 'R');
  }

  static boolean isGenerateShortcutKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, SUBMIT_GENERATION)
        || (isAltChar(keyEvent, 'g', 'G') && !keyEvent.hasCtrl());
  }

  static boolean isFavoriteToggleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, FAVORITE_TOGGLE) || isPlainChar(keyEvent, 'f', 'F');
  }

  static boolean isClearSelectedExtensionsKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, CLEAR_SELECTED_EXTENSIONS) || isPlainChar(keyEvent, 'x', 'X');
  }

  static boolean isCategoryFilterCycleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, CATEGORY_FILTER_CYCLE) || isPlainChar(keyEvent, 'v', 'V');
  }

  static boolean isJumpToFavoriteKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, JUMP_TO_FAVORITE) || isCtrlChar(keyEvent, 'j', 'J');
  }

  static boolean isFavoritesFilterToggleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, TOGGLE_FAVORITES_FILTER) || isCtrlChar(keyEvent, 'k', 'K');
  }

  static boolean isPresetFilterCycleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, CYCLE_PRESET_FILTER) || isCtrlChar(keyEvent, 'y', 'Y');
  }

  static boolean isCategoryCollapseToggleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, TOGGLE_CATEGORY) || isPlainChar(keyEvent, 'c', 'c');
  }

  static boolean isExpandAllCategoriesKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, OPEN_ALL_CATEGORIES) || isPlainChar(keyEvent, 'C', 'C');
  }

  static boolean isCommandPaletteToggleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, OPEN_COMMAND_PALETTE)
        || (isCtrlChar(keyEvent, 'p', 'P') && !keyEvent.hasAlt());
  }

  static boolean isHelpOverlayToggleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, OPEN_HELP) || isPlainChar(keyEvent, '?', '?');
  }

  static boolean isErrorDetailsToggleKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, TOGGLE_ERROR_DETAILS) || isCtrlChar(keyEvent, 'e', 'E');
  }

  static boolean isFocusExtensionSearchSlashKey(KeyEvent keyEvent) {
    return keyEvent.code() == KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && keyEvent.character() == '/';
  }

  static boolean isFocusExtensionSearchCtrlKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, FOCUS_EXTENSION_SEARCH) || isCtrlChar(keyEvent, 'f', 'F');
  }

  static boolean isFocusExtensionListKey(KeyEvent keyEvent) {
    return hasAction(keyEvent, FOCUS_EXTENSION_LIST) || isCtrlChar(keyEvent, 'l', 'L');
  }

  private static boolean hasAction(KeyEvent keyEvent, String action) {
    return keyEvent.action().filter(action::equals).isPresent();
  }

  private static boolean isPlainChar(KeyEvent keyEvent, char lower, char upper) {
    return keyEvent.code() == KeyCode.CHAR
        && !keyEvent.hasCtrl()
        && !keyEvent.hasAlt()
        && (keyEvent.character() == lower || keyEvent.character() == upper);
  }

  private static boolean isCtrlChar(KeyEvent keyEvent, char lower, char upper) {
    return keyEvent.code() == KeyCode.CHAR
        && keyEvent.hasCtrl()
        && (keyEvent.character() == lower || keyEvent.character() == upper);
  }

  private static boolean isAltChar(KeyEvent keyEvent, char lower, char upper) {
    return keyEvent.code() == KeyCode.CHAR
        && keyEvent.hasAlt()
        && (keyEvent.character() == lower || keyEvent.character() == upper);
  }
}
