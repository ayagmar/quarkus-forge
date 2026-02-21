package dev.ayagmar.quarkusforge.api;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@FunctionalInterface
public interface AsyncSleeper {
  CompletableFuture<Void> sleep(Duration delay);

  static AsyncSleeper system() {
    return delay ->
        CompletableFuture.runAsync(
            () -> {},
            CompletableFuture.delayedExecutor(
                Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS));
  }
}
