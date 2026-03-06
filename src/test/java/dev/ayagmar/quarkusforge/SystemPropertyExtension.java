package dev.ayagmar.quarkusforge;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

final class SystemPropertyExtension implements AfterEachCallback {
  private final Map<String, String> originalValues = new LinkedHashMap<>();

  void set(String key, String value) {
    rememberOriginalValue(key);
    System.setProperty(key, value);
  }

  void clear(String key) {
    rememberOriginalValue(key);
    System.clearProperty(key);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    originalValues.forEach(
        (key, originalValue) -> {
          if (originalValue == null) {
            System.clearProperty(key);
          } else {
            System.setProperty(key, originalValue);
          }
        });
    originalValues.clear();
  }

  private void rememberOriginalValue(String key) {
    Objects.requireNonNull(key);
    if (!originalValues.containsKey(key)) {
      originalValues.put(key, System.getProperty(key));
    }
  }
}
