package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorMessageMapperTest {

  // ── userFriendlyError ──────────────────────────────────────────────────

  @Test
  void userFriendlyErrorReturnsUnknownForNull() {
    assertThat(ErrorMessageMapper.userFriendlyError(null)).isEqualTo("unknown error");
  }

  @Test
  void userFriendlyErrorDelegatesToApiErrorMessages() {
    RuntimeException exception = new RuntimeException("something broke");
    assertThat(ErrorMessageMapper.userFriendlyError(exception)).isEqualTo("something broke");
  }

  // ── simpleError ──────────────────────────────────────────────────────

  @Test
  void simpleErrorReturnsUnknownForNull() {
    assertThat(ErrorMessageMapper.simpleError(null)).isEqualTo("unknown error");
  }

  @Test
  void simpleErrorReturnsMessageWhenPresent() {
    assertThat(ErrorMessageMapper.simpleError(new RuntimeException("broken")))
        .isEqualTo("broken");
  }

  @Test
  void simpleErrorReturnsClassNameWhenNoMessage() {
    assertThat(ErrorMessageMapper.simpleError(new NullPointerException()))
        .isEqualTo("NullPointerException");
  }

  @Test
  void simpleErrorReturnsClassNameForBlankMessage() {
    assertThat(ErrorMessageMapper.simpleError(new RuntimeException("   ")))
        .isEqualTo("RuntimeException");
  }

  // ── verboseDetails ──────────────────────────────────────────────────────

  @Test
  void verboseDetailsReturnsEmptyForNull() {
    assertThat(ErrorMessageMapper.verboseDetails(null)).isEmpty();
  }

  @Test
  void verboseDetailsReturnsResponseBodyForApiHttpException() {
    ApiHttpException exception = new ApiHttpException(400, " some error body ");
    assertThat(ErrorMessageMapper.verboseDetails(exception)).isEqualTo("some error body");
  }

  @Test
  void verboseDetailsReturnsEmptyForBlankBody() {
    ApiHttpException exception = new ApiHttpException(500, "   ");
    assertThat(ErrorMessageMapper.verboseDetails(exception)).isEmpty();
  }

  @Test
  void verboseDetailsReturnsEmptyForBinaryBody() {
    ApiHttpException exception = new ApiHttpException(500, "<binary>");
    assertThat(ErrorMessageMapper.verboseDetails(exception)).isEmpty();
  }

  @Test
  void verboseDetailsReturnsMessageForRegularException() {
    RuntimeException exception = new RuntimeException("  detail here  ");
    assertThat(ErrorMessageMapper.verboseDetails(exception)).isEqualTo("detail here");
  }

  @Test
  void verboseDetailsReturnsEmptyForNullMessage() {
    assertThat(ErrorMessageMapper.verboseDetails(new RuntimeException((String) null))).isEmpty();
  }
}
