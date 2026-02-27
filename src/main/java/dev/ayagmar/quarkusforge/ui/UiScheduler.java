package dev.ayagmar.quarkusforge.ui;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@FunctionalInterface
public interface UiScheduler {
  Cancellable schedule(Duration delay, Runnable task);

  static UiScheduler immediate() {
    return (delay, task) -> {
      task.run();
      return () -> false;
    };
  }

  static UiScheduler fromScheduledExecutor(
      ScheduledExecutorService scheduler, Consumer<Runnable> renderThreadDispatcher) {
    Objects.requireNonNull(scheduler);
    Objects.requireNonNull(renderThreadDispatcher);

    return (delay, task) -> {
      long delayMillis = Math.max(0L, delay.toMillis());
      ScheduledFuture<?> future =
          scheduler.schedule(
              () -> renderThreadDispatcher.accept(task), delayMillis, TimeUnit.MILLISECONDS);
      return () -> future.cancel(false);
    };
  }

  @FunctionalInterface
  interface Cancellable {
    boolean cancel();
  }
}
