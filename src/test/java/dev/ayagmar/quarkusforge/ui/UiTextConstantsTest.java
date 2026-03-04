package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.IdeDetector;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiTextConstantsTest {

  @Test
  void postGenerationActionsWithNoIdesIncludesGenericOpenInIde() {
    List<UiTextConstants.PostGenerationAction> actions =
        UiTextConstants.postGenerationActions(List.of());

    assertThat(actions).anyMatch(a -> "Open in IDE".equals(a.label()));
    assertThat(actions).anyMatch(a -> "Quit".equals(a.label()));
  }

  @Test
  void postGenerationActionsWithDetectedIdesUsesIdeNames() {
    List<IdeDetector.DetectedIde> ides =
        List.of(
            new IdeDetector.DetectedIde("VS Code", "code ."),
            new IdeDetector.DetectedIde("IntelliJ IDEA", "idea ."));

    List<UiTextConstants.PostGenerationAction> actions =
        UiTextConstants.postGenerationActions(ides);

    assertThat(actions).anyMatch(a -> "Open in VS Code".equals(a.label()));
    assertThat(actions).anyMatch(a -> "Open in IntelliJ IDEA".equals(a.label()));
    assertThat(actions).noneMatch(a -> "Open in IDE".equals(a.label()));
  }

  @Test
  void postGenerationActionsAlwaysIncludeForgefileAndTerminalAndQuit() {
    List<UiTextConstants.PostGenerationAction> actions =
        UiTextConstants.postGenerationActions(List.of());

    List<String> labels = UiTextConstants.postGenerationActionLabels(actions);

    assertThat(labels).contains("Export Forgefile", "Open in terminal", "Generate again", "Quit");
    assertThat(labels.getLast()).isEqualTo("Export Forgefile");
  }

  @Test
  void postGenerationActionLabelsExtractsLabels() {
    List<UiTextConstants.PostGenerationAction> actions =
        List.of(
            new UiTextConstants.PostGenerationAction("A", PostGenerationExitAction.QUIT),
            new UiTextConstants.PostGenerationAction("B", PostGenerationExitAction.QUIT));

    List<String> labels = UiTextConstants.postGenerationActionLabels(actions);

    assertThat(labels).containsExactly("A", "B");
  }

  @Test
  void postGenerationActionRecordPreservesIdeCommand() {
    UiTextConstants.PostGenerationAction action =
        new UiTextConstants.PostGenerationAction(
            "Open in VS Code", PostGenerationExitAction.OPEN_IDE, "code .");

    assertThat(action.ideCommand()).isEqualTo("code .");
    assertThat(action.label()).isEqualTo("Open in VS Code");
    assertThat(action.action()).isEqualTo(PostGenerationExitAction.OPEN_IDE);
  }

  @Test
  void postGenerationActionWithoutIdeCommandDefaultsToNull() {
    UiTextConstants.PostGenerationAction action =
        new UiTextConstants.PostGenerationAction("Quit", PostGenerationExitAction.QUIT);

    assertThat(action.ideCommand()).isNull();
  }

  @Test
  void splashArtIsNotEmpty() {
    assertThat(UiTextConstants.STARTUP_SPLASH_ART).isNotEmpty();
  }

  @Test
  void globalHelpLinesAreNotEmpty() {
    assertThat(UiTextConstants.GLOBAL_HELP_LINES).isNotEmpty();
  }
}
