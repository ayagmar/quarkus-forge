package dev.ayagmar.quarkusforge.api;

public final class ApiErrorMessages {
  private static final int HTTP_ERROR_DETAIL_MAX_LENGTH = 200;

  private ApiErrorMessages() {}

  public static String userFriendlyMessage(Throwable throwable) {
    if (throwable == null) {
      return "unknown error";
    }
    if (throwable instanceof ApiHttpException apiHttpException) {
      return userFriendlyHttpMessage(apiHttpException);
    }
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable.getClass().getSimpleName();
    }
    return message;
  }

  private static String userFriendlyHttpMessage(ApiHttpException exception) {
    String details = sanitizeHttpDetails(exception.responseBody());
    return switch (exception.statusCode()) {
      case 400 ->
          "Quarkus API rejected generation request (HTTP 400)."
              + " Check project metadata and selected extensions."
              + details;
      case 401, 403 ->
          "Quarkus API request was not authorized (HTTP " + exception.statusCode() + ")." + details;
      case 404 ->
          "Quarkus API endpoint not found (HTTP 404). Check configured API base URL." + details;
      default -> "Quarkus API request failed (HTTP " + exception.statusCode() + ")." + details;
    };
  }

  private static String sanitizeHttpDetails(String responseBody) {
    if (responseBody == null || responseBody.isBlank() || "<binary>".equals(responseBody)) {
      return "";
    }
    String compact = responseBody.replaceAll("\\s+", " ").trim();
    if (compact.isEmpty()) {
      return "";
    }
    String truncated =
        compact.length() <= HTTP_ERROR_DETAIL_MAX_LENGTH
            ? compact
            : compact.substring(0, HTTP_ERROR_DETAIL_MAX_LENGTH - 3) + "...";
    return " Details: " + truncated;
  }
}
