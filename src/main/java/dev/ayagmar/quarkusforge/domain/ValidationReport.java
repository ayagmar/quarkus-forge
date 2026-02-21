package dev.ayagmar.quarkusforge.domain;

import java.util.List;

public record ValidationReport(List<ValidationError> errors) {
  public ValidationReport {
    errors = List.copyOf(errors);
  }

  public boolean isValid() {
    return errors.isEmpty();
  }
}
