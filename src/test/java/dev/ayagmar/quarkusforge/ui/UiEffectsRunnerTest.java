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
    UiIntent.ExtensionCommand extensionCommand = UiIntent.ExtensionCommand.TOGGLE_SELECTED_FILTER;
    KeyEvent selectorKey = KeyEvent.ofKey(KeyCode.LEFT);
    KeyEvent textInputKey = KeyEvent.ofChar('a');
    RecordingUiEffectsPort port = new RecordingUiEffectsPort();
    List<UiIntent> followUpIntents =
        new UiEffectsRunner()
            .run(
                List.of(
                    new UiEffect.StartCatalogLoad(loader),
                    new UiEffect.RequestCatalogReload(),
                    new UiEffect.PrepareForGeneration(),
                    new UiEffect.CancelPendingAsync(),
                    new UiEffect.ExportRecipeAndLock(),
                    new UiEffect.ExecuteExtensionCommand(extensionCommand),
                    new UiEffect.ApplyExtensionNavigationKey(KeyEvent.ofChar('j')),
                    new UiEffect.ApplyCatalogLoadSuccess(success),
                    new UiEffect.StartGeneration(),
                    new UiEffect.TransitionGenerationState(
                        CoreTuiController.GenerationState.LOADING),
                    new UiEffect.RequestGenerationCancellation(),
                    new UiEffect.RequestAsyncRepaint(),
                    new UiEffect.MoveTextInputCursorToEnd(FocusTarget.EXTENSION_SEARCH),
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
            "executeExtensionCommand",
            "applyExtensionNavigationKey",
            "applyCatalogLoadSuccess",
            "startGeneration",
            "transitionGenerationState",
            "requestGenerationCancellation",
            "requestAsyncRepaint",
            "moveTextInputCursorToEnd",
            "applyMetadataSelectorKey",
            "applyTextInputKey");
    assertThat(followUpIntents)
        .containsExactly(new UiIntent.ExtensionStatusIntent("Selected-only view enabled"));
    assertThat(port.loader).isSameAs(loader);
    assertThat(port.extensionCommand).isEqualTo(extensionCommand);
    assertThat(port.extensionNavigationKeyEvent).isEqualTo(KeyEvent.ofChar('j'));
    assertThat(port.success).isEqualTo(success);
    assertThat(port.targetState).isEqualTo(CoreTuiController.GenerationState.LOADING);
    assertThat(port.cursorFocusTarget).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(port.metadataFocusTarget).isEqualTo(FocusTarget.BUILD_TOOL);
    assertThat(port.metadataKeyEvent).isEqualTo(selectorKey);
    assertThat(port.textInputFocusTarget).isEqualTo(FocusTarget.ARTIFACT_ID);
    assertThat(port.textInputKeyEvent).isEqualTo(textInputKey);
  }

  private static final class RecordingUiEffectsPort implements UiEffectsPort {
    private final List<String> calls = new ArrayList<>();
    private ExtensionCatalogLoader loader;
    private UiIntent.ExtensionCommand extensionCommand;
    private KeyEvent extensionNavigationKeyEvent;
    private CatalogLoadSuccess success;
    private CoreTuiController.GenerationState targetState;
    private FocusTarget cursorFocusTarget;
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
    public String executeExtensionCommand(UiIntent.ExtensionCommand command) {
      calls.add("executeExtensionCommand");
      extensionCommand = command;
      return "Selected-only view enabled";
    }

    @Override
    public void applyExtensionNavigationKey(KeyEvent keyEvent) {
      calls.add("applyExtensionNavigationKey");
      extensionNavigationKeyEvent = keyEvent;
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
    public void moveTextInputCursorToEnd(FocusTarget focusTarget) {
      calls.add("moveTextInputCursorToEnd");
      cursorFocusTarget = focusTarget;
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
