package dev.ayagmar.quarkusforge.api;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class ThrowableUnwrapper {
  private ThrowableUnwrapper() {}

  public static Throwable unwrapCompletionCause(Throwable throwable) {
    if (throwable instanceof CompletionException completionException
        && completionException.getCause() != null) {
      return completionException.getCause();
    }
    return throwable;
  }

  public static Throwable unwrapAsyncFailure(Throwable throwable) {
    Throwable current = throwable;
    while (current instanceof ExecutionException || current instanceof CompletionException) {
      Throwable cause = current.getCause();
      if (cause == null) {
        break;
      }
      current = cause;
    }
    return current;
  }
}
