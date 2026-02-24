package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.style.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class UiTheme {
  private static final String DEFAULT_THEME_RESOURCE = "/ui/quarkus-forge.tcss";
  private static final String THEME_PATH_PROPERTY = "quarkus.forge.theme";
  private static final String THEME_PATH_ENV = "QUARKUS_FORGE_THEME";
  private static final Map<String, Color> DEFAULT_COLORS =
      Map.ofEntries(
          Map.entry("base", Color.rgb(16, 23, 42)),
          Map.entry("text", Color.rgb(238, 244, 255)),
          Map.entry("accent", Color.rgb(255, 59, 102)),
          Map.entry("focus", Color.rgb(35, 229, 216)),
          Map.entry("muted", Color.rgb(124, 137, 168)),
          Map.entry("success", Color.rgb(53, 208, 127)),
          Map.entry("warning", Color.rgb(246, 191, 92)),
          Map.entry("error", Color.rgb(255, 77, 109)));

  private final Map<String, Color> colors;

  private UiTheme(Map<String, Color> colors) {
    this.colors = Map.copyOf(colors);
  }

  static UiTheme loadDefault() {
    Map<String, Color> loaded = new HashMap<>(DEFAULT_COLORS);
    loadThemeFromResource(DEFAULT_THEME_RESOURCE, loaded);
    String customThemePath = configuredThemePath();
    if (!customThemePath.isBlank()) {
      loadThemeFromFile(customThemePath, loaded);
    }
    return new UiTheme(loaded);
  }

  Color color(String tokenName) {
    return colors.getOrDefault(normalizeToken(tokenName), DEFAULT_COLORS.get("text"));
  }

  private static void parseThemeLine(String line, Map<String, Color> target) {
    String normalized = line.trim();
    if (normalized.isEmpty() || normalized.startsWith("#")) {
      return;
    }
    String[] parts = normalized.split("[:=]", 2);
    if (parts.length != 2) {
      return;
    }
    String token = normalizeToken(parts[0]);
    String rawValue = parts[1].replace(";", "").trim();
    Color color = parseColor(rawValue);
    if (color != null) {
      target.put(token, color);
    }
  }

  private static String configuredThemePath() {
    String propertyValue = System.getProperty(THEME_PATH_PROPERTY);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue.trim();
    }
    String envValue = System.getenv(THEME_PATH_ENV);
    return envValue == null ? "" : envValue.trim();
  }

  private static void loadThemeFromResource(String resourcePath, Map<String, Color> target) {
    InputStream inputStream = UiTheme.class.getResourceAsStream(resourcePath);
    if (inputStream == null) {
      return;
    }
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      parseTheme(reader, target);
    } catch (IOException ignored) {
      // Keep deterministic fallback defaults on read errors.
    }
  }

  private static void loadThemeFromFile(String filePath, Map<String, Color> target) {
    Path path = Path.of(filePath).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) {
      return;
    }
    try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      parseTheme(reader, target);
    } catch (IOException ignored) {
      // Keep deterministic fallback/default resource tokens on read errors.
    }
  }

  private static void parseTheme(BufferedReader reader, Map<String, Color> target)
      throws IOException {
    String line;
    while ((line = reader.readLine()) != null) {
      parseThemeLine(line, target);
    }
  }

  private static String normalizeToken(String tokenName) {
    return tokenName.trim().toLowerCase(Locale.ROOT);
  }

  private static Color parseColor(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    String value = rawValue.trim();
    if (value.startsWith("#")) {
      try {
        return Color.hex(value);
      } catch (RuntimeException ignored) {
        return null;
      }
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "black" -> Color.BLACK;
      case "red" -> Color.RED;
      case "green" -> Color.GREEN;
      case "yellow" -> Color.YELLOW;
      case "blue" -> Color.BLUE;
      case "magenta" -> Color.MAGENTA;
      case "cyan" -> Color.CYAN;
      case "white" -> Color.WHITE;
      case "gray" -> Color.GRAY;
      case "dark-gray", "dark_gray" -> Color.DARK_GRAY;
      case "light-red", "light_red" -> Color.LIGHT_RED;
      case "light-green", "light_green" -> Color.LIGHT_GREEN;
      case "light-yellow", "light_yellow" -> Color.LIGHT_YELLOW;
      case "light-blue", "light_blue" -> Color.LIGHT_BLUE;
      case "light-magenta", "light_magenta" -> Color.LIGHT_MAGENTA;
      case "light-cyan", "light_cyan" -> Color.LIGHT_CYAN;
      case "bright-white", "bright_white" -> Color.BRIGHT_WHITE;
      default -> null;
    };
  }
}
