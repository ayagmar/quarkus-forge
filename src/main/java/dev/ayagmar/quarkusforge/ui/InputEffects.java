package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

final class InputEffects {
  interface Callbacks {
    ProjectRequest currentRequest();

    ProjectRequest syncMetadataSelectors(ProjectRequest request);

    ValidationReport validateRequest(ProjectRequest request);

    UiIntent.SubmitEditRecoveryIntent submitRecoveryIntent(ProjectRequest request);

    UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent();

    boolean isGenerationInProgress();

    void scheduleFilteredRefresh(String query, IntConsumer onFiltered);

    void dispatchIntents(List<UiIntent> intents);
  }

  private final EnumMap<FocusTarget, TextInputState> inputStates;
  private final MetadataSelectorManager metadataSelectors;
  private final Callbacks callbacks;

  InputEffects(
      EnumMap<FocusTarget, TextInputState> inputStates,
      MetadataSelectorManager metadataSelectors,
      Callbacks callbacks) {
    this.inputStates = Objects.requireNonNull(inputStates);
    this.metadataSelectors = Objects.requireNonNull(metadataSelectors);
    this.callbacks = Objects.requireNonNull(callbacks);
  }

  void moveTextInputCursorToEnd(FocusTarget target) {
    TextInputState inputState = inputStates.get(target);
    if (inputState != null) {
      inputState.moveCursorToEnd();
    }
  }

  List<UiIntent> applyMetadataSelectorKey(FocusTarget target, KeyEvent keyEvent) {
    if (keyEvent.isLeft()
        || UiKeyMatchers.isVimLeftKey(keyEvent)
        || keyEvent.isUp()
        || UiKeyMatchers.isVimUpKey(keyEvent)) {
      return applySelectorCycle(target, -1);
    }
    if (keyEvent.isRight()
        || UiKeyMatchers.isVimRightKey(keyEvent)
        || keyEvent.isDown()
        || UiKeyMatchers.isVimDownKey(keyEvent)) {
      return applySelectorCycle(target, 1);
    }
    if (keyEvent.isHome()) {
      return applySelectorEdge(target, true);
    }
    if (keyEvent.isEnd()) {
      return applySelectorEdge(target, false);
    }
    return List.of();
  }

  List<UiIntent> applyTextInputKey(FocusTarget target, KeyEvent keyEvent) {
    if (!handleTextInputKey(inputStates.get(target), keyEvent)) {
      return List.of();
    }
    if (target == FocusTarget.EXTENSION_SEARCH) {
      TextInputState searchState = inputStates.get(FocusTarget.EXTENSION_SEARCH);
      callbacks.scheduleFilteredRefresh(searchState.text(), this::publishFilteredExtensionUpdate);
      return extensionUpdateIntents("Searching extensions...");
    }
    ProjectRequest currentRequest = callbacks.currentRequest();
    ProjectRequest nextRequest =
        buildRequestFromInputs(
            currentRequest.platformStream(),
            currentRequest.buildTool(),
            currentRequest.javaVersion());
    return List.of(
        new UiIntent.FormStateUpdatedIntent(nextRequest, callbacks.validateRequest(nextRequest)),
        callbacks.submitRecoveryIntent(nextRequest));
  }

  ProjectRequest buildRequestFromInputs(
      String platformStream, String buildTool, String javaVersion) {
    return CliPrefillMapper.map(
        new CliPrefill(
            inputStates.get(FocusTarget.GROUP_ID).text(),
            inputStates.get(FocusTarget.ARTIFACT_ID).text(),
            inputStates.get(FocusTarget.VERSION).text(),
            inputStates.get(FocusTarget.PACKAGE_NAME).text(),
            inputStates.get(FocusTarget.OUTPUT_DIR).text(),
            platformStream,
            buildTool,
            javaVersion));
  }

  private List<UiIntent> applySelectorCycle(FocusTarget target, int delta) {
    String newValue =
        metadataSelectors.cycle(target, selectorValue(callbacks.currentRequest(), target), delta);
    if (newValue == null) {
      return List.of();
    }
    return applySelector(target, newValue);
  }

  private List<UiIntent> applySelectorEdge(FocusTarget target, boolean first) {
    String newValue = metadataSelectors.selectEdge(target, first);
    if (newValue == null) {
      return List.of();
    }
    return applySelector(target, newValue);
  }

  private List<UiIntent> applySelector(FocusTarget target, String selectedValue) {
    ProjectRequest request = callbacks.currentRequest();
    String platformStream = request.platformStream();
    String buildTool = request.buildTool();
    String javaVersion = request.javaVersion();

    if (target == FocusTarget.PLATFORM_STREAM) {
      platformStream = selectedValue;
    } else if (target == FocusTarget.BUILD_TOOL) {
      buildTool = selectedValue;
    } else if (target == FocusTarget.JAVA_VERSION) {
      javaVersion = selectedValue;
    }

    ProjectRequest nextRequest =
        callbacks.syncMetadataSelectors(
            buildRequestFromInputs(platformStream, buildTool, javaVersion));
    ValidationReport nextValidation = callbacks.validateRequest(nextRequest);
    return List.of(
        new UiIntent.FormStateUpdatedIntent(nextRequest, nextValidation),
        new UiIntent.StatusMessageIntent(selectorStatusMessage(target, nextRequest)),
        callbacks.submitRecoveryIntent(nextRequest));
  }

  private void publishFilteredExtensionUpdate(int filteredCount) {
    callbacks.dispatchIntents(filteredExtensionUpdateIntents(filteredCount));
  }

  private List<UiIntent> extensionUpdateIntents(String statusMessage) {
    return ExtensionIntentFactory.updateWithStatus(
        callbacks.extensionStateUpdatedIntent(), statusMessage);
  }

  private List<UiIntent> filteredExtensionUpdateIntents(int filteredCount) {
    return ExtensionIntentFactory.updateWithStatus(
        callbacks.extensionStateUpdatedIntent(),
        callbacks.isGenerationInProgress() ? null : "Extensions filtered: " + filteredCount);
  }

  private static String selectorStatusMessage(FocusTarget target, ProjectRequest request) {
    return switch (target) {
      case PLATFORM_STREAM ->
          "Platform stream selected: "
              + (request.platformStream().isBlank() ? "default" : request.platformStream());
      case BUILD_TOOL -> "Build tool selected: " + request.buildTool();
      case JAVA_VERSION -> "Java version selected: " + request.javaVersion();
      default -> "Selection updated";
    };
  }

  private static String selectorValue(ProjectRequest request, FocusTarget target) {
    return switch (target) {
      case PLATFORM_STREAM -> request.platformStream();
      case BUILD_TOOL -> request.buildTool();
      case JAVA_VERSION -> request.javaVersion();
      default -> "";
    };
  }

  private static boolean handleTextInputKey(TextInputState state, KeyEvent event) {
    if (state == null || !UiTextInputKeys.isSupportedEditKey(event)) {
      return false;
    }
    if (event.code() == KeyCode.CHAR) {
      state.insert(event.character());
      return true;
    }
    if (event.isDeleteBackward()) {
      state.deleteBackward();
      return true;
    }
    if (event.isDeleteForward()) {
      state.deleteForward();
      return true;
    }
    if (event.isLeft()) {
      state.moveCursorLeft();
      return true;
    }
    if (event.isRight()) {
      state.moveCursorRight();
      return true;
    }
    if (event.isHome()) {
      state.moveCursorToStart();
      return true;
    }
    if (event.isEnd()) {
      state.moveCursorToEnd();
      return true;
    }
    return false;
  }
}
