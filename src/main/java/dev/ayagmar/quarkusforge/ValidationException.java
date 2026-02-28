package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.domain.ValidationError;
import java.util.List;

final class ValidationException extends RuntimeException {
  private final List<ValidationError> errors;

  ValidationException(List<ValidationError> errors) {
    this.errors = List.copyOf(errors);
  }

  List<ValidationError> errors() {
    return errors;
  }
}
