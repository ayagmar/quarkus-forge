package dev.ayagmar.quarkusforge.diagnostics;

import java.util.Objects;

public record DiagnosticField(String name, Object value) {
  public DiagnosticField {
    Objects.requireNonNull(name);
    if (name.isBlank()) {
      throw new IllegalArgumentException("Diagnostic field name must not be blank");
    }
  }

  public static DiagnosticField of(String name, Object value) {
    return new DiagnosticField(name, value);
  }
}
