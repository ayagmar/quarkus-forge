package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

class ThrowableUnwrapperTest {
  @Test
  void unwrapCompletionCauseReturnsDirectCause() {
    IllegalStateException root = new IllegalStateException("boom");
    CompletionException wrapped = new CompletionException(root);

    assertThat(ThrowableUnwrapper.unwrapCompletionCause(wrapped)).isSameAs(root);
  }

  @Test
  void unwrapAsyncFailureUnwrapsNestedExecutionAndCompletion() {
    IllegalArgumentException root = new IllegalArgumentException("invalid");
    CompletionException completion = new CompletionException(root);
    ExecutionException execution = new ExecutionException(completion);

    assertThat(ThrowableUnwrapper.unwrapAsyncFailure(execution)).isSameAs(root);
  }

  @Test
  void unwrapAsyncFailureReturnsOriginalWhenNoNestedCauseExists() {
    RuntimeException root = new RuntimeException("plain");

    assertThat(ThrowableUnwrapper.unwrapAsyncFailure(root)).isSameAs(root);
  }
}
