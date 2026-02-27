package dev.ayagmar.quarkusforge.api;

public final class ApiHttpException extends ApiClientException {
  private final int statusCode;
  private final String responseBody;

  public ApiHttpException(int statusCode, String responseBody) {
    super("Unexpected HTTP status " + statusCode);
    this.statusCode = statusCode;
    this.responseBody = responseBody;
  }

  public int statusCode() {
    return statusCode;
  }

  public String responseBody() {
    return responseBody;
  }
}
