package dev.ayagmar.quarkusforge.ui;

import java.util.List;

/**
 * UI text constants for the TUI: splash art, global help lines, command palette entries,
 * post-generation action labels, GitHub visibility labels, and file names.
 */
final class UiTextConstants {
  private UiTextConstants() {}

  static final String FORGE_LOCK_FILE_NAME = "forge.lock";
  static final String FORGE_RECIPE_FILE_NAME = "Forgefile";

  static final List<String> STARTUP_SPLASH_ART =
      List.of(
          "   ____ _   _   _    ____  _  ___   _ ____",
          "  / __ \\ | | | / \\  |  _ \\| |/ / | | / ___|",
          " | |  | | |_| |/ _ \\ | |_) | ' /| | | \\___ \\",
          " | |__| |  _  / ___ \\|  _ <| . \\| |_| |___) |",
          "  \\___\\_\\_| |_/_/   \\_\\_| \\_\\_|\\_\\\\___/|____/");

  static final List<CommandPaletteEntry> COMMAND_PALETTE_ENTRIES =
      List.of(
          new CommandPaletteEntry(
              "Focus extension search", "/ or Ctrl+F", CommandPaletteAction.FOCUS_EXTENSION_SEARCH),
          new CommandPaletteEntry(
              "Focus extension list", "Ctrl+L", CommandPaletteAction.FOCUS_EXTENSION_LIST),
          new CommandPaletteEntry(
              "Toggle favorite filter", "Ctrl+K", CommandPaletteAction.TOGGLE_FAVORITES_FILTER),
          new CommandPaletteEntry(
              "Cycle preset filter", "Ctrl+Y", CommandPaletteAction.CYCLE_PRESET_FILTER),
          new CommandPaletteEntry(
              "Jump to next favorite", "Ctrl+J", CommandPaletteAction.JUMP_TO_FAVORITE),
          new CommandPaletteEntry(
              "Cycle category filter", "v", CommandPaletteAction.CYCLE_CATEGORY_FILTER),
          new CommandPaletteEntry("Toggle category", "c", CommandPaletteAction.TOGGLE_CATEGORY),
          new CommandPaletteEntry(
              "Open all categories", "C", CommandPaletteAction.OPEN_ALL_CATEGORIES),
          new CommandPaletteEntry("Reload catalog", "Ctrl+R", CommandPaletteAction.RELOAD_CATALOG),
          new CommandPaletteEntry(
              "Toggle error details", "Ctrl+E", CommandPaletteAction.TOGGLE_ERROR_DETAILS));

  static final List<String> POST_GENERATION_ACTION_LABELS =
      List.of(
          "Export Forgefile + forge.lock",
          "Publish to GitHub (requires gh)",
          "Open in IDE",
          "Open in terminal",
          "Generate again",
          "Quit");

  static final List<String> GITHUB_VISIBILITY_LABELS =
      List.of("Private repo", "Public repo", "Internal repo (GitHub Enterprise)");

  static final List<String> GLOBAL_HELP_LINES =
      List.of(
          "Global",
          "  Tab / Shift+Tab : move focus",
          "  Enter           : submit generation",
          "  Alt+G           : submit generation",
          "  Esc / Ctrl+C    : cancel generation or quit",
          "  ?               : toggle help",
          "  Ctrl+P          : command palette",
          "",
          "Extensions",
          "  / or Ctrl+F     : focus extension search",
          "  Esc             : clear search/filter or return to list",
          "  Ctrl+L          : focus extension list",
          "  Up/Down or j/k  : move in list",
          "  Home/End        : first/last list row",
          "  PgUp/PgDn       : previous/next category",
          "  Space           : toggle extension",
          "  v               : cycle category filter",
          "  x               : clear selected extensions",
          "  f               : toggle favorite",
          "  c / C           : close/open focused category / open all",
          "  Ctrl+J          : jump to next favorite",
          "  Ctrl+K          : toggle favorites filter",
          "  Ctrl+Y          : cycle preset filter",
          "  Ctrl+R          : reload extension catalog",
          "",
          "Diagnostics",
          "  Ctrl+E          : toggle expanded error details",
          "",
          "Help",
          "  Esc or ?        : close this help");
}
