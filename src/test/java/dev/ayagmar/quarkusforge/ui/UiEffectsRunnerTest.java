package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiEffectsRunnerTest {

  @Test
  void runnerDelegatesEachEffectToTheMatchingPortMethodInOrder() {
    ExtensionCatalogLoader loader = () -> null;
    CatalogLoadSuccess success =
        new CatalogLoadSuccess(
            List.of(),
            null,
            Map.of(),
            CatalogLoadState.loaded("live", false),
            "Loaded extension catalog from live API");
    CommandPaletteAction commandPaletteAction = CommandPaletteAction.TOGGLE_SELECTED_FILTER;
    KeyEvent selectorKey = KeyEvent.ofKey(KeyCode.LEFT);
    KeyEvent textInputKey = KeyEvent.ofChar('a');
    RecordingUiEffectsPort port = new RecordingUiEffectsPort();

    new UiEffectsRunner()
        .run(
            List.of(
                new UiEffect.StartCatalogLoad(loader),
                new UiEffect.RequestCatalogReload(),
                new UiEffect.PrepareForGeneration(),
                new UiEffect.CancelPendingAsync(),
                new UiEffect.ExportRecipeAndLock(),
                new UiEffect.ExecuteCommandPaletteAction(commandPaletteAction),
                new UiEffect.ApplyCatalogLoadSuccess(success),
                new UiEffect.StartGeneration(),
                new UiEffect.TransitionGenerationState(CoreTuiController.GenerationState.LOADING),
                new UiEffect.RequestGenerationCancellation(),
                new UiEffect.RequestAsyncRepaint(),
                new UiEffect.ApplyMetadataSelectorKey(FocusTarget.BUILD_TOOL, selectorKey),
                new UiEffect.ApplyTextInputKey(FocusTarget.ARTIFACT_ID, textInputKey)),
            port);

    assertThat(port.calls)
        .containsExactly(
            "startCatalogLoad",
            "requestCatalogReload",
            "prepareForGeneration",
            "cancelPendingAsync",
            "exportRecipeAndLock",
            "executeCommandPaletteAction",
            "applyCatalogLoadSuccess",
            "startGeneration",
            "transitionGenerationState",
            "requestGenerationCancellation",
            "requestAsyncRepaint",
            "applyMetadataSelectorKey",
            "applyTextInputKey");
    assertThat(port.loader).isSameAs(loader);
    assertThat(port.commandPaletteAction).isEqualTo(commandPaletteAction);
    assertThat(port.success).isEqualTo(success);
    assertThat(port.targetState).isEqualTo(CoreTuiController.GenerationState.LOADING);
    assertThat(port.metadataFocusTarget).isEqualTo(FocusTarget.BUILD_TOOL);
    assertThat(port.metadataKeyEvent).isEqualTo(selectorKey);
    assertThat(port.textInputFocusTarget).isEqualTo(FocusTarget.ARTIFACT_ID);
    assertThat(port.textInputKeyEvent).isEqualTo(textInputKey);
  }

  private static final class RecordingUiEffectsPort implements UiEffectsPort {
    private final List<String> calls = new ArrayList<>();
    private ExtensionCatalogLoader loader;
    private CommandPaletteAction commandPaletteAction;
    private CatalogLoadSuccess success;
    private CoreTuiController.GenerationState targetState;
    private FocusTarget metadataFocusTarget;
    private KeyEvent metadataKeyEvent;
    private FocusTarget textInputFocusTarget;
    private KeyEvent textInputKeyEvent;

    @Override
    public void startCatalogLoad(ExtensionCatalogLoader loader) {
      calls.add("startCatalogLoad");
      this.loader = loader;
    }

    @Override
    public void requestCatalogReload() {
      calls.add("requestCatalogReload");
    }

    @Override
    public void prepareForGeneration() {
      calls.add("prepareForGeneration");
    }

    @Override
    public void cancelPendingAsync() {
      calls.add("cancelPendingAsync");
    }

    @Override
    public void exportRecipeAndLock() {
      calls.add("exportRecipeAndLock");
    }

    @Override
    public void executeCommandPaletteAction(CommandPaletteAction action) {
      calls.add("executeCommandPaletteAction");
      commandPaletteAction = action;
    }

    @Override
    public void applyCatalogLoadSuccess(CatalogLoadSuccess success) {
      calls.add("applyCatalogLoadSuccess");
      this.success = success;
    }

    @Override
    public void startGeneration() {
      calls.add("startGeneration");
    }

    @Override
    public void transitionGenerationState(CoreTuiController.GenerationState targetState) {
      calls.add("transitionGenerationState");
      this.targetState = targetState;
    }

    @Override
    public void requestGenerationCancellation() {
      calls.add("requestGenerationCancellation");
    }

    @Override
    public void requestAsyncRepaint() {
      calls.add("requestAsyncRepaint");
    }

    @Override
    public void applyMetadataSelectorKey(FocusTarget focusTarget, KeyEvent keyEvent) {
      calls.add("applyMetadataSelectorKey");
      metadataFocusTarget = focusTarget;
      metadataKeyEvent = keyEvent;
    }

    @Override
    public void applyTextInputKey(FocusTarget focusTarget, KeyEvent keyEvent) {
      calls.add("applyTextInputKey");
      textInputFocusTarget = focusTarget;
      textInputKeyEvent = keyEvent;
    }
  }
}
