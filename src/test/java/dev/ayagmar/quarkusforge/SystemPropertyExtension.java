package dev.ayagmar.quarkusforge;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class SystemPropertyExtension implements AfterEachCallback {
  private final Map<String, String> originalValues = new LinkedHashMap<>();

  public void set(String key, String value) {
    rememberOriginalValue(key);
    System.setProperty(key, value);
  }

  public void set(String key, long value) {
    set(key, Long.toString(value));
  }

  public void set(String key, Path value) {
    Objects.requireNonNull(value);
    set(key, value.toString());
  }

  public void setAll(Map<String, String> values) {
    Objects.requireNonNull(values);
    values.forEach(this::set);
  }

  public void clear(String key) {
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
