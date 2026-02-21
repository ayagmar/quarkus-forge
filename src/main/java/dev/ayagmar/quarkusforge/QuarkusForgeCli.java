package dev.ayagmar.quarkusforge;

import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "quarkus-forge",
    mixinStandardHelpOptions = true,
    description = "Quarkus forge terminal UI")
public final class QuarkusForgeCli implements Callable<Integer> {
  @Option(
      names = "--smoke",
      defaultValue = "false",
      description = "Start the TUI and auto-exit after a short delay")
  private boolean smokeMode;

  @Override
  public Integer call() throws Exception {
    runTui(smokeMode);
    return 0;
  }

  public static int runWithArgs(String[] args) {
    return new CommandLine(new QuarkusForgeCli()).execute(args);
  }

  public static void main(String[] args) {
    int exitCode = runWithArgs(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static void runTui(boolean smokeMode) throws Exception {
    try (var tui = TuiRunner.create()) {
      if (smokeMode) {
        tui.scheduler().schedule(tui::quit, 350, TimeUnit.MILLISECONDS);
      }

      tui.run(
          QuarkusForgeCli::handleEvent, frame -> frame.renderWidget(renderHome(), frame.area()));
    }
  }

  private static boolean handleEvent(Event event, TuiRunner runner) {
    if (event instanceof KeyEvent keyEvent && (keyEvent.isQuit() || keyEvent.isCancel())) {
      runner.quit();
      return true;
    }
    return false;
  }

  private static Paragraph renderHome() {
    return Paragraph.builder()
        .text(Text.from("Quarkus Forge is ready. Press 'q' to quit."))
        .build();
  }
}
