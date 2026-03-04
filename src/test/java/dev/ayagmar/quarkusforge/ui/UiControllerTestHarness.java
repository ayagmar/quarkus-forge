package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.GenerationRequest;
import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class UiControllerTestHarness {
  private UiControllerTestHarness() {}

  static CoreTuiController controller() {
    return controller(UiScheduler.immediate(), Duration.ZERO);
  }

  static CoreTuiController controller(UiScheduler scheduler, Duration debounceDelay) {
    return CoreTuiController.from(
        UiTestFixtureFactory.defaultForgeUiState(), scheduler, debounceDelay);
  }

  static CoreTuiController controller(
      UiScheduler scheduler, Duration debounceDelay, ProjectGenerationRunner generationRunner) {
    return CoreTuiController.from(
        UiTestFixtureFactory.defaultForgeUiState(), scheduler, debounceDelay, generationRunner);
  }

  static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  static void moveSelectionToLabel(CoreTuiController controller, String label) {
    int targetIndex = controller.postGenerationActionLabels().indexOf(label);
    assertThat(targetIndex).isGreaterThanOrEqualTo(0);
    int delta = targetIndex - controller.postGenerationActionSelection();
    if (delta > 0) {
      for (int i = 0; i < delta; i++) {
        controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
      }
      return;
    }
    for (int i = 0; i < -delta; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.UP));
    }
  }

  static String renderToString(CoreTuiController controller) {
    return renderToString(controller, 120, 32);
  }

  static String renderToString(CoreTuiController controller, int width, int height) {
    return renderFrame(controller, width, height).buffer().toAnsiStringTrimmed();
  }

  static Frame renderFrame(CoreTuiController controller, int width, int height) {
    Buffer buffer = Buffer.empty(new Rect(0, 0, width, height));
    Frame frame = Frame.forTesting(buffer);
    controller.render(frame);
    return frame;
  }

  static final class QueueingScheduler implements UiScheduler {
    private final List<Runnable> queuedTasks = new ArrayList<>();

    @Override
    public UiCancellable schedule(Duration delay, Runnable task) {
      queuedTasks.add(task);
      return () -> queuedTasks.remove(task);
    }

    void runAll() {
      List<Runnable> pendingTasks = new ArrayList<>(queuedTasks);
      queuedTasks.clear();
      for (Runnable pendingTask : pendingTasks) {
        pendingTask.run();
      }
    }
  }

  static final class ManualUiScheduler implements UiScheduler {
    private final List<ScheduledTask> tasks = new ArrayList<>();
    private final boolean ignoreCancellation;
    private long nowMillis;
    private long sequence;

    ManualUiScheduler(boolean ignoreCancellation) {
      this.ignoreCancellation = ignoreCancellation;
    }

    @Override
    public UiCancellable schedule(Duration delay, Runnable task) {
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

  static final class ControlledGenerationRunner implements ProjectGenerationRunner {
    private int callCount;
    private Path lastOutputDirectory;
    private CompletableFuture<Path> future;

    ControlledGenerationRunner() {
      callCount = 0;
      lastOutputDirectory = null;
      future = new CompletableFuture<>();
    }

    @Override
    public CompletableFuture<Path> generate(
        GenerationRequest generationRequest,
        Path outputDirectory,
        BooleanSupplier cancelled,
        Consumer<GenerationProgressUpdate> progressListener) {
      callCount++;
      lastOutputDirectory = outputDirectory;
      progressListener.accept(
          GenerationProgressUpdate.requestingArchive(
              "requesting project archive from Quarkus API..."));
      return future;
    }

    int callCount() {
      return callCount;
    }

    Path lastOutputDirectory() {
      return lastOutputDirectory;
    }

    void complete(Path outputPath) {
      future.complete(outputPath);
    }

    void fail(Throwable throwable) {
      future.completeExceptionally(throwable);
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
