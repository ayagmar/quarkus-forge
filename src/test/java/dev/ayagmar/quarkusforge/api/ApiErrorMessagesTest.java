package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiErrorMessagesTest {
  @Test
  void mapsHttp400ToActionableGenerationGuidance() {
    ApiHttpException exception = new ApiHttpException(400, "<binary>");

    String message = ApiErrorMessages.userFriendlyMessage(exception);

    assertThat(message).contains("HTTP 400");
    assertThat(message).contains("Check project metadata and selected extensions");
  }

  @Test
  void includesSanitizedHttpBodyDetailsWhenTextual() {
    ApiHttpException exception = new ApiHttpException(404, "  endpoint \n missing  ");

    String message = ApiErrorMessages.userFriendlyMessage(exception);

    assertThat(message).contains("HTTP 404");
    assertThat(message).contains("Details: endpoint missing");
  }

  @Test
  void fallsBackToThrowableClassNameWhenMessageIsBlank() {
    RuntimeException exception = new RuntimeException("");

    String message = ApiErrorMessages.userFriendlyMessage(exception);

    assertThat(message).isEqualTo("RuntimeException");
  }
}
