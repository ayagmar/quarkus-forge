package dev.ayagmar.quarkusforge.api;

public final class ApiContractException extends ApiClientException {
  public ApiContractException(String message) {
    super(message);
  }

  public ApiContractException(String message, Throwable cause) {
    super(message, cause);
  }
}
