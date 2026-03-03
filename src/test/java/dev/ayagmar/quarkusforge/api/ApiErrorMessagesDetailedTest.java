package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiErrorMessagesDetailedTest {

  @Test
  void http400ReturnsRejectionMessage() {
    ApiHttpException exception = new ApiHttpException(400, "bad request body");
    String message = ApiErrorMessages.userFriendlyMessage(exception);

    assertThat(message)
        .contains("HTTP 400")
        .contains("Check project metadata")
        .contains("bad request body");
  }

  @Test
  void http401ReturnsAuthMessage() {
    String message = ApiErrorMessages.userFriendlyMessage(new ApiHttpException(401, ""));
    assertThat(message).contains("not authorized").contains("HTTP 401");
  }

  @Test
  void http403ReturnsAuthMessage() {
    String message = ApiErrorMessages.userFriendlyMessage(new ApiHttpException(403, ""));
    assertThat(message).contains("not authorized").contains("HTTP 403");
  }

  @Test
  void http404ReturnsNotFoundMessage() {
    String message = ApiErrorMessages.userFriendlyMessage(new ApiHttpException(404, null));
    assertThat(message).contains("not found").contains("HTTP 404");
  }

  @Test
  void httpOtherStatusReturnsGenericMessage() {
    String message = ApiErrorMessages.userFriendlyMessage(new ApiHttpException(503, ""));
    assertThat(message).contains("HTTP 503").contains("request failed");
  }

  @Test
  void nonHttpExceptionReturnsMessage() {
    String message =
        ApiErrorMessages.userFriendlyMessage(new RuntimeException("connection refused"));
    assertThat(message).isEqualTo("connection refused");
  }

  @Test
  void nullThrowableReturnsUnknownError() {
    assertThat(ApiErrorMessages.userFriendlyMessage(null)).isEqualTo("unknown error");
  }

  @Test
  void exceptionWithBlankMessageReturnsClassName() {
    assertThat(ApiErrorMessages.userFriendlyMessage(new RuntimeException("   ")))
        .isEqualTo("RuntimeException");
  }

  @Test
  void exceptionWithNullMessageReturnsClassName() {
    assertThat(ApiErrorMessages.userFriendlyMessage(new RuntimeException((String) null)))
        .isEqualTo("RuntimeException");
  }

  @Test
  void httpResponseBodyIsTruncatedWhenLong() {
    String longBody = "x".repeat(300);
    ApiHttpException exception = new ApiHttpException(500, longBody);
    String message = ApiErrorMessages.userFriendlyMessage(exception);

    assertThat(message).contains("..."); // truncated
    assertThat(message.length()).isLessThan(longBody.length() + 100);
  }

  @Test
  void binaryResponseBodyIsOmitted() {
    ApiHttpException exception = new ApiHttpException(500, "<binary>");
    String message = ApiErrorMessages.userFriendlyMessage(exception);

    assertThat(message).doesNotContain("<binary>");
  }
}
