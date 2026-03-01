package dev.ayagmar.quarkusforge.api;

public final class ErrorMessageMapper {
  private ErrorMessageMapper() {}

  public static String userFriendlyError(Throwable throwable) {
    return ApiErrorMessages.userFriendlyMessage(throwable);
  }

  public static String simpleError(Throwable throwable) {
    if (throwable == null) {
      return "unknown error";
    }
    if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
      return throwable.getMessage();
    }
    return throwable.getClass().getSimpleName();
  }

  public static String verboseDetails(Throwable throwable) {
    if (throwable == null) {
      return "";
    }
    if (throwable instanceof ApiHttpException apiHttpException) {
      String body = apiHttpException.responseBody();
      if (body == null || body.isBlank() || "<binary>".equals(body)) {
        return "";
      }
      return body.strip();
    }
    String message = throwable.getMessage();
    return message == null ? "" : message.strip();
  }
}
