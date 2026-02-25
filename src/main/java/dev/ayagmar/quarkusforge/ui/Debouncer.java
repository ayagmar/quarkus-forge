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

  void submit(Runnable task) {
    Runnable immediateTask = null;
    synchronized (this) {
      cancel();
      if (delay.isZero()) {
        immediateTask = task;
      } else {
        pendingTask = scheduler.schedule(delay, task);
      }
    }
    if (immediateTask != null) {
      immediateTask.run();
    }
  }

  synchronized void cancel() {
    if (pendingTask != null) {
      pendingTask.cancel();
      pendingTask = null;
    }
  }
}
