package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CoreTuiAsyncDeterministicTest {
  @Test
  void debounceAppliesOnlyAfterVirtualTimeAdvance() {
    ManualUiScheduler scheduler = new ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(200));
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));
    controller.onEvent(KeyEvent.ofChar('c'));

    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
    scheduler.advanceBy(Duration.ofMillis(199));
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);

    scheduler.advanceBy(Duration.ofMillis(1));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  @Test
  void staleResultsNeverOverwriteLatestQueryWhenCancelIsIneffective() {
    ManualUiScheduler scheduler = new ManualUiScheduler(true);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(150));
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "rest".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    for (int i = 0; i < 4; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }

    scheduler.advanceBy(Duration.ofMillis(150));
    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  @Test
  void cancellationPreventsPendingDebounceTaskFromApplying() {
    ManualUiScheduler scheduler = new ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ofMillis(120));
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    CoreTuiController.UiAction quitAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(quitAction.shouldQuit()).isTrue();

    scheduler.advanceBy(Duration.ofMillis(500));
    assertThat(controller.filteredExtensionCount()).isEqualTo(7);
  }

  @Test
  void pendingSearchRefreshDoesNotOverrideGenerationStatus() {
    ManualUiScheduler scheduler = new ManualUiScheduler(false);
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            scheduler,
            Duration.ofMillis(120),
            (generationRequest, outputDirectory, cancelled, progressListener) -> {
              progressListener.accept("downloading project archive...");
              return new CompletableFuture<>();
            });
    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);

    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.statusMessage()).contains("Generation in progress");

    scheduler.advanceBy(Duration.ofMillis(120));
    assertThat(controller.statusMessage()).contains("Generation in progress");
  }

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static final class ManualUiScheduler implements UiScheduler {
    private final List<ScheduledTask> tasks = new ArrayList<>();
    private final boolean ignoreCancellation;
    private long nowMillis;
    private long sequence;

    ManualUiScheduler(boolean ignoreCancellation) {
      this.ignoreCancellation = ignoreCancellation;
    }

    @Override
    public Cancellable schedule(Duration delay, Runnable task) {
      ScheduledTask scheduledTask =
          new ScheduledTask(nowMillis + Math.max(0L, delay.toMillis()), sequence++, task);
      tasks.add(scheduledTask);
      return () -> {
        if (ignoreCancellation) {
          return false;
        }
        return scheduledTask.cancel();
      };
    }

    void advanceBy(Duration step) {
      nowMillis += Math.max(0L, step.toMillis());
      boolean progressed;
      do {
        progressed = false;
        tasks.sort(
            Comparator.comparingLong(ScheduledTask::dueMillis)
                .thenComparingLong(ScheduledTask::order));
        for (int i = 0; i < tasks.size(); i++) {
          ScheduledTask task = tasks.get(i);
          if (task.dueMillis() > nowMillis) {
            break;
          }
          tasks.remove(i);
          i--;
          if (!task.cancelled()) {
            task.run();
          }
          progressed = true;
        }
      } while (progressed);
    }
  }

  private static final class ScheduledTask {
    private final long dueMillis;
    private final long order;
    private final Runnable runnable;
    private boolean cancelled;

    ScheduledTask(long dueMillis, long order, Runnable runnable) {
      this.dueMillis = dueMillis;
      this.order = order;
      this.runnable = runnable;
      this.cancelled = false;
    }

    long dueMillis() {
      return dueMillis;
    }

    long order() {
      return order;
    }

    boolean cancelled() {
      return cancelled;
    }

    boolean cancel() {
      if (cancelled) {
        return false;
      }
      cancelled = true;
      return true;
    }

    void run() {
      runnable.run();
    }
  }
}
