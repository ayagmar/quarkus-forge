package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.domain.ValidationError;
import java.util.List;
import java.util.stream.Collectors;

final class ValidationException extends RuntimeException {
  private final List<ValidationError> errors;

  ValidationException(List<ValidationError> errors) {
    this.errors = List.copyOf(errors);
  }

  List<ValidationError> errors() {
    return errors;
  }

  @Override
  public String getMessage() {
    if (errors.isEmpty()) {
      return "Validation failed with no details";
    }
    return errors.stream()
        .map(e -> e.field() + ": " + e.message())
        .collect(Collectors.joining("; ", "Validation failed — ", ""));
  }
}
