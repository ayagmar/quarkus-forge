package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UiSchedulerTest {
  @Test
  void zeroDelayDispatchRunsOnScheduledExecutorPath() {
    TrackingScheduledExecutor scheduler = new TrackingScheduledExecutor();
    AtomicBoolean renderThreadInvoked = new AtomicBoolean(false);
    UiScheduler uiScheduler =
        UiScheduler.fromScheduledExecutor(
            scheduler,
            task -> {
              renderThreadInvoked.set(true);
              task.run();
            });

    AtomicBoolean taskRan = new AtomicBoolean(false);
    uiScheduler.schedule(Duration.ZERO, () -> taskRan.set(true));

    assertThat(renderThreadInvoked).isTrue();
    assertThat(taskRan).isTrue();
    assertThat(scheduler.scheduleCalls()).isEqualTo(1);
    scheduler.shutdownNow();
  }

  private static final class TrackingScheduledExecutor extends ScheduledThreadPoolExecutor {
    private final AtomicInteger scheduleCalls = new AtomicInteger(0);

    TrackingScheduledExecutor() {
      super(1);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      scheduleCalls.incrementAndGet();
      return super.schedule(command, delay, unit);
    }

    int scheduleCalls() {
      return scheduleCalls.get();
    }
  }
}
