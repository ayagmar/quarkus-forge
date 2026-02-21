package dev.ayagmar.quarkusforge.api;

public class ApiClientException extends RuntimeException {
  public ApiClientException(String message) {
    super(message);
  }

  public ApiClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
