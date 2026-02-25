package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DebouncerTest {
  @Test
  void cancelDoesNotBlockWhileImmediateTaskIsRunning() throws Exception {
    Debouncer debouncer = new Debouncer(UiScheduler.immediate(), Duration.ZERO);
    CountDownLatch taskStarted = new CountDownLatch(1);
    CountDownLatch allowTaskCompletion = new CountDownLatch(1);
    AtomicReference<Throwable> submitFailure = new AtomicReference<>();

    var executor = Executors.newSingleThreadExecutor();
    try {
      executor.submit(
          () -> {
            try {
              debouncer.submit(
                  () -> {
                    taskStarted.countDown();
                    try {
                      allowTaskCompletion.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException interruptedException) {
                      Thread.currentThread().interrupt();
                    }
                  });
            } catch (Throwable throwable) {
              submitFailure.set(throwable);
            }
          });

      assertThat(taskStarted.await(1, TimeUnit.SECONDS)).isTrue();
      long startedAt = System.nanoTime();
      debouncer.cancel();
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      assertThat(elapsedMillis).isLessThan(100L);

      allowTaskCompletion.countDown();
    } finally {
      executor.shutdownNow();
    }

    assertThat(submitFailure.get()).isNull();
  }
}
