package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationExceptionTest {

  @Test
  void errorsReturnsImmutableCopy() {
    List<ValidationError> errors =
        new ArrayList<>(
            List.of(
                new ValidationError("field1", "must not be blank"),
                new ValidationError("field2", "invalid value")));
    ValidationException exception = new ValidationException(errors);
    errors.add(new ValidationError("field3", "extra"));

    assertThat(exception.errors()).hasSize(2);
    assertThat(exception.errors()).isNotSameAs(errors);
    assertThat(exception.errors()).isUnmodifiable();
  }

  @Test
  void getMessageFormatsAllErrors() {
    List<ValidationError> errors =
        List.of(
            new ValidationError("groupId", "must not be blank"),
            new ValidationError("buildTool", "unsupported"));
    ValidationException exception = new ValidationException(errors);

    assertThat(exception.getMessage())
        .startsWith("Validation failed")
        .contains("groupId: must not be blank")
        .contains("buildTool: unsupported");
  }

  @Test
  void getMessageWithEmptyErrorsReturnsFallback() {
    ValidationException exception = new ValidationException(List.of());

    assertThat(exception.getMessage()).isEqualTo("Validation failed with no details");
  }

  @Test
  void singleErrorMessageFormatted() {
    ValidationException exception =
        new ValidationException(List.of(new ValidationError("version", "is required")));

    assertThat(exception.getMessage()).isEqualTo("Validation failed — version: is required");
  }
}
