package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class InputEffectsTest {

  @Test
  void textInputEffectUpdatesRequestAndRecoveryForFormFields() {
    TestFixture fixture = new TestFixture();

    List<UiIntent> intents =
        fixture.inputEffects.applyTextInputKey(FocusTarget.ARTIFACT_ID, KeyEvent.ofChar('x'));

    assertThat(intents).hasSize(2);
    UiIntent.FormStateUpdatedIntent formStateUpdatedIntent =
        (UiIntent.FormStateUpdatedIntent) intents.getFirst();
    assertThat(formStateUpdatedIntent.request().artifactId()).isEqualTo("forge-appx");
    assertThat(formStateUpdatedIntent.validation().isValid()).isTrue();
    assertThat(intents.get(1)).isInstanceOf(UiIntent.SubmitEditRecoveryIntent.class);
  }

  @Test
  void extensionSearchEffectSchedulesRefreshAndPublishesAsyncUpdateIntents() {
    TestFixture fixture = new TestFixture();

    List<UiIntent> intents =
        fixture.inputEffects.applyTextInputKey(FocusTarget.EXTENSION_SEARCH, KeyEvent.ofChar('r'));

    assertThat(intents)
        .containsExactly(
            fixture.callbacks.extensionStateUpdatedIntent(),
            new UiIntent.ExtensionStatusIntent("Searching extensions..."));
    assertThat(fixture.callbacks.scheduledQuery).isEqualTo("r");

    fixture.callbacks.publishFilteredResult(3);

    assertThat(fixture.callbacks.dispatchedIntents)
        .containsExactly(
            fixture.callbacks.extensionStateUpdatedIntent(),
            new UiIntent.ExtensionStatusIntent("Extensions filtered: 3"));
  }

  @Test
  void metadataSelectorEffectCyclesBuildToolAndEmitsStatus() {
    TestFixture fixture = new TestFixture();
    String expectedBuildTool =
        fixture.metadataSelectors.optionsFor(FocusTarget.BUILD_TOOL).getLast();

    List<UiIntent> intents =
        fixture.inputEffects.applyMetadataSelectorKey(
            FocusTarget.BUILD_TOOL, KeyEvent.ofKey(KeyCode.END));

    assertThat(intents).hasSize(3);
    UiIntent.FormStateUpdatedIntent formStateUpdatedIntent =
        (UiIntent.FormStateUpdatedIntent) intents.getFirst();
    assertThat(formStateUpdatedIntent.request().buildTool()).isEqualTo(expectedBuildTool);
    assertThat(intents.get(1))
        .isEqualTo(new UiIntent.StatusMessageIntent("Build tool selected: " + expectedBuildTool));
    assertThat(intents.get(2)).isInstanceOf(UiIntent.SubmitEditRecoveryIntent.class);
  }

  private static final class TestFixture {
    private final MetadataCompatibilityContext metadataCompatibility =
        MetadataCompatibilityContext.loadDefault();
    private final ForgeUiState initialState = UiTestFixtureFactory.defaultForgeUiState();
    private final EnumMap<FocusTarget, TextInputState> inputStates =
        new EnumMap<>(FocusTarget.class);
    private final MetadataSelectorManager metadataSelectors = new MetadataSelectorManager();
    private final TestCallbacks callbacks =
        new TestCallbacks(initialState.request(), metadataCompatibility, metadataSelectors);
    private final InputEffects inputEffects =
        new InputEffects(inputStates, metadataSelectors, callbacks);

    private TestFixture() {
      for (FocusTarget target : FocusTarget.values()) {
        inputStates.put(target, new TextInputState(""));
      }
      ProjectRequest request = initialState.request();
      inputStates.get(FocusTarget.GROUP_ID).setText(request.groupId());
      inputStates.get(FocusTarget.ARTIFACT_ID).setText(request.artifactId());
      inputStates.get(FocusTarget.VERSION).setText(request.version());
      inputStates.get(FocusTarget.PACKAGE_NAME).setText(request.packageName());
      inputStates.get(FocusTarget.OUTPUT_DIR).setText(request.outputDirectory());
      for (FocusTarget target : UiFocusTargets.ordered()) {
        if (UiFocusPredicates.isTextInputFocus(target)) {
          inputStates.get(target).moveCursorToEnd();
        }
      }
      metadataSelectors.sync(
          metadataCompatibility.metadataSnapshot(),
          request.platformStream(),
          request.buildTool(),
          request.javaVersion());
    }
  }

  private static final class TestCallbacks implements InputEffects.Callbacks {
    private final MetadataCompatibilityContext metadataCompatibility;
    private final MetadataSelectorManager metadataSelectors;
    private ProjectRequest currentRequest;
    private String scheduledQuery;
    private IntConsumer scheduledRefresh;
    private final List<UiIntent> dispatchedIntents = new ArrayList<>();

    private TestCallbacks(
        ProjectRequest currentRequest,
        MetadataCompatibilityContext metadataCompatibility,
        MetadataSelectorManager metadataSelectors) {
      this.currentRequest = currentRequest;
      this.metadataCompatibility = metadataCompatibility;
      this.metadataSelectors = metadataSelectors;
    }

    @Override
    public ProjectRequest currentRequest() {
      return currentRequest;
    }

    @Override
    public ProjectRequest syncMetadataSelectors(ProjectRequest request) {
      currentRequest = request;
      metadataSelectors.sync(
          metadataCompatibility.metadataSnapshot(),
          request.platformStream(),
          request.buildTool(),
          request.javaVersion());
      return request;
    }

    @Override
    public ValidationReport validateRequest(ProjectRequest request) {
      return new ProjectRequestValidator()
          .validate(request)
          .merge(metadataCompatibility.validate(request));
    }

    @Override
    public UiIntent.SubmitEditRecoveryIntent submitRecoveryIntent(ProjectRequest request) {
      currentRequest = request;
      return new UiIntent.SubmitEditRecoveryIntent(new UiIntent.SubmitRecoveryContext(""));
    }

    @Override
    public UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent() {
      return new UiIntent.ExtensionStateUpdatedIntent(UiTestFixtureFactory.defaultExtensionView());
    }

    @Override
    public boolean isGenerationInProgress() {
      return false;
    }

    @Override
    public void scheduleFilteredRefresh(String query, IntConsumer onFiltered) {
      scheduledQuery = query;
      scheduledRefresh = onFiltered;
    }

    @Override
    public void dispatchIntents(List<UiIntent> intents) {
      dispatchedIntents.addAll(intents);
    }

    private void publishFilteredResult(int filteredCount) {
      scheduledRefresh.accept(filteredCount);
    }
  }
}
