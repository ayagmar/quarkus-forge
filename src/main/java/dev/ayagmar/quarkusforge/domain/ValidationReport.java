package dev.ayagmar.quarkusforge.domain;

import java.util.ArrayList;
import java.util.List;

public record ValidationReport(List<ValidationError> errors) {
  public ValidationReport {
    errors = List.copyOf(errors);
  }

  public boolean isValid() {
    return errors.isEmpty();
  }

  public ValidationReport merge(ValidationReport other) {
    List<ValidationError> merged = new ArrayList<>(errors);
    merged.addAll(other.errors());
    return new ValidationReport(merged);
  }
}
