package dev.ayagmar.quarkusforge.ui;

import java.time.Duration;
import java.util.Objects;

final class Debouncer {
  private final UiScheduler scheduler;
  private final Duration delay;
  private UiScheduler.Cancellable pendingTask;

  Debouncer(UiScheduler scheduler, Duration delay) {
    this.scheduler = Objects.requireNonNull(scheduler);
    this.delay = Objects.requireNonNull(delay);
  }

  synchronized void submit(Runnable task) {
    cancel();
    pendingTask = scheduler.schedule(delay, task);
  }

  synchronized void cancel() {
    if (pendingTask != null) {
      pendingTask.cancel();
      pendingTask = null;
    }
  }
}
