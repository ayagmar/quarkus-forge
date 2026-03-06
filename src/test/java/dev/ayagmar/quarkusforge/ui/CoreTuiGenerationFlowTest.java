package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import dev.ayagmar.quarkusforge.api.ApiHttpException;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.TickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class CoreTuiGenerationFlowTest {
  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  @TempDir Path tempDir;

  private record ConflictFixture(
      CoreTuiController controller,
      UiControllerTestHarness.ControlledGenerationRunner generationRunner) {}

  @Test
  void successfulGenerationShowsNextStepHintAndLocksInteractiveInputs() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(generationRunner.callCount()).isEqualTo(1);
    assertThat(generationRunner.lastOutputDirectory())
        .isEqualTo(Path.of("./generated").toAbsolutePath().resolve("forge-app").normalize());
    assertThat(controller.statusMessage()).contains("Generation in progress");

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.GROUP_ID);

    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.SUCCESS);
    assertThat(controller.statusMessage()).contains("Generation succeeded");
    assertThat(controller.postGenerationMenuVisible()).isTrue();
    assertThat(controller.postGenerationSuccessHint()).contains("cd ").contains("mvn quarkus:dev");
  }

  @Test
  void submitBlockedWhenTargetFolderAlreadyExists() throws Exception {
    ConflictFixture fixture = createConflictFixture();
    CoreTuiController controller = fixture.controller();
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        fixture.generationRunner();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(generationRunner.callCount()).isZero();
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.OUTPUT_DIR);
    assertThat(controller.statusMessage()).contains("target folder exists");
    assertThat(controller.errorMessage()).contains("Output directory already exists:");
  }

  @Test
  void targetFolderConflictClearsAfterArtifactChange() throws Exception {
    ConflictFixture fixture = createConflictFixture();
    CoreTuiController controller = fixture.controller();

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(controller.errorMessage()).contains("Output directory already exists:");

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.ARTIFACT_ID);
    controller.onEvent(KeyEvent.ofChar('2'));

    assertThat(controller.request().artifactId()).isEqualTo("forge-app2");
    assertThat(controller.statusMessage()).contains("conflict resolved");
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .doesNotContain("Output directory already exists:");
  }

  @Test
  void postGenerationMenuOpenInTerminalQuitsWithExitPlan() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    UiAction action = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(action.shouldQuit()).isTrue();
    assertThat(controller.postGenerationExitPlan()).isPresent();
    assertThat(controller.postGenerationExitPlan().orElseThrow().action())
        .isEqualTo(PostGenerationExitAction.OPEN_TERMINAL);
    assertThat(controller.postGenerationExitPlan().orElseThrow().projectDirectory())
        .isEqualTo(Path.of("build/generated-project").toAbsolutePath().normalize());
  }

  @Test
  void postGenerationMenuGenerateAgainStaysInTuiAndAllowsResubmit() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    controller.onEvent(KeyEvent.ofKey(KeyCode.DOWN));
    UiAction action = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.postGenerationMenuVisible()).isFalse();
    assertThat(controller.postGenerationSuccessHint()).isEmpty();
    assertThat(controller.statusMessage()).isEqualTo("Ready for next generation");

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(generationRunner.callCount()).isEqualTo(2);
  }

  @Test
  void postGenerationMenuCanWriteLockAndStayOpen() throws Exception {
    systemProperties.set("user.home", tempDir);

    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    Path generated = tempDir.resolve("generated-project");
    Files.createDirectories(generated);
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(generated);
    UiControllerTestHarness.moveSelectionToLabel(controller, "Export Forgefile");
    UiAction action = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(action.shouldQuit()).isFalse();
    assertThat(controller.postGenerationExitPlan()).isEmpty();
    assertThat(generated.resolve("Forgefile")).exists();
  }

  @Test
  void submitResolvesTildeOutputDirectoryAgainstUserHome() {
    systemProperties.set("user.home", tempDir);

    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "~/Projects/Quarkus",
            "maven",
            "25");
    ForgeUiState state =
        new ForgeUiState(
            request,
            new ProjectRequestValidator().validate(request),
            MetadataCompatibilityContext.loadDefault());
    CoreTuiController controller =
        CoreTuiController.from(state, UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(generationRunner.lastOutputDirectory())
        .isEqualTo(tempDir.resolve("Projects").resolve("Quarkus").resolve("forge-app").normalize());
  }

  @Test
  void postGenerationMenuCanSelectPublishToGithubExitPlan() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));
    assertThat(controller.postGenerationMenuVisible()).isTrue();
    UiAction choosePublishAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(choosePublishAction.shouldQuit()).isFalse();
    assertThat(controller.githubVisibilityMenuVisible()).isTrue();

    UiAction action = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(action.shouldQuit()).isTrue();
    assertThat(controller.postGenerationExitPlan()).isPresent();
    PostGenerationExitPlan exitPlan = controller.postGenerationExitPlan().orElseThrow();
    assertThat(exitPlan.action()).isEqualTo(PostGenerationExitAction.PUBLISH_GITHUB);
    assertThat(exitPlan.githubVisibility()).isEqualTo(GitHubVisibility.PRIVATE);
  }

  @Test
  void extensionSearchAcceptsJCharacter() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    UiControllerTestHarness.moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));

    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.EXTENSION_SEARCH);
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34)).contains("Search: [j");
  }

  @Test
  void duplicateSubmitIsIgnoredWhileGenerationIsRunning() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(generationRunner.callCount()).isEqualTo(1);
    assertThat(controller.statusMessage()).contains("already in progress");
  }

  @Test
  void generationLockConsumesKeysWithoutTriggeringQuit() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    UiAction tabAction = controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    UiAction enterAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(tabAction.handled()).isTrue();
    assertThat(tabAction.shouldQuit()).isFalse();
    assertThat(enterAction.handled()).isTrue();
    assertThat(enterAction.shouldQuit()).isFalse();
  }

  @Test
  void escapeCancelsActiveGenerationWithoutImmediateQuit() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    UiAction cancelAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(cancelAction.shouldQuit()).isFalse();
    assertThat(controller.statusMessage())
        .containsAnyOf("Cancellation requested", "Generation cancelled");

    generationRunner.fail(new CancellationException("cancelled"));
    assertThat(controller.statusMessage()).contains("Generation cancelled");

    UiAction quitAction = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));
    assertThat(quitAction.shouldQuit()).isTrue();
  }

  @Test
  void successfulGradleGenerationShowsGradleNextStepHint() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState("gradle"),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .contains("./gradlew quarkusDev");
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .doesNotContain("mvn quarkus:dev");
  }

  @Test
  void successfulGradleKotlinDslGenerationShowsGradleNextStepHint() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState("gradle-kotlin-dsl"),
            UiScheduler.immediate(),
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .contains("./gradlew quarkusDev");
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .doesNotContain("mvn quarkus:dev");
  }

  @Test
  void generationFailureShowsErrorAndReleasesUiLock() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.fail(new RuntimeException("download failed"));

    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(controller.errorMessage()).contains("download failed");

    controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    assertThat(controller.focusTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);
  }

  @Test
  void http400GenerationFailureShowsActionableErrorMessage() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.fail(new ApiHttpException(400, "<binary>"));

    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(controller.errorMessage())
        .contains("Quarkus API rejected generation request (HTTP 400).");
  }

  @Test
  void synchronousGenerationRunnerFailureIsSurfacedAsUiError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) -> {
              throw new IllegalStateException("runner crashed");
            });

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.ERROR);
    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(controller.errorMessage()).contains("runner crashed");
  }

  @Test
  void nullGenerationFutureIsSurfacedAsUiError() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) -> null);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.ERROR);
    assertThat(controller.statusMessage()).contains("Generation failed");
    assertThat(controller.errorMessage()).contains("Generation service returned null future");
  }

  @Test
  void completionStillAppliesWhenSchedulerDropsAsyncCallback() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    UiScheduler droppingScheduler = (delay, task) -> () -> false;
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            droppingScheduler,
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.LOADING);

    UiControllerTestHarness.renderToString(controller, 120, 34);

    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.SUCCESS);
    assertThat(controller.statusMessage()).contains("Generation succeeded");
  }

  @Test
  void escapeDoesNotCancelWhenGenerationAlreadyCompleted() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    UiScheduler droppingScheduler = (delay, task) -> () -> false;
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            droppingScheduler,
            Duration.ZERO,
            generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));

    UiAction action = controller.onEvent(KeyEvent.ofKey(KeyCode.ESCAPE));

    assertThat(action.shouldQuit()).isTrue();
    assertThat(controller.generationState()).isEqualTo(CoreTuiController.GenerationState.SUCCESS);
    assertThat(controller.statusMessage()).contains("Generation succeeded");
  }

  @Test
  void generationProgressOverlayShowsGaugeAndPhase() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 34);
    assertThat(rendered).contains("Generating Project");
    assertThat(rendered).contains("requesting project archive from Quarkus API");
    assertThat(rendered).contains("Esc: cancel");
  }

  @Test
  void blankProgressUsesSameCanonicalPhaseInStatusAndOverlay() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) -> {
              progressListener.accept(
                  new GenerationProgressUpdate(GenerationProgressStep.REQUESTING_ARCHIVE, ""));
              return new CompletableFuture<>();
            });

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    String expectedPhase = "requesting project archive from Quarkus API...";
    assertThat(controller.statusMessage()).isEqualTo("Generation in progress: " + expectedPhase);
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34)).contains(expectedPhase);
  }

  @Test
  void generationProgressOverlayStartsWithRequestingApiStep() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) ->
                new CompletableFuture<>());

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));

    String rendered = UiControllerTestHarness.renderToString(controller, 120, 34);
    assertThat(rendered).contains("Generating Project");
    assertThat(rendered).contains("requesting project archive from Quarkus API");
  }

  @Test
  void tickEventRequestsRepaintWhileGenerationIsLoading() {
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(),
            UiScheduler.immediate(),
            Duration.ZERO,
            (generationRequest, outputDirectory, cancelled, progressListener) ->
                new CompletableFuture<>());

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    UiAction tickAction = controller.onEvent(TickEvent.of(1, Duration.ofMillis(40)));

    assertThat(tickAction.handled()).isTrue();
    assertThat(tickAction.shouldQuit()).isFalse();
    assertThat(controller.statusMessage()).contains("Generation in progress");
  }

  @Test
  void tickEventRequestsRepaintAfterAsyncGenerationCompletion() {
    UiControllerTestHarness.QueueingScheduler scheduler =
        new UiControllerTestHarness.QueueingScheduler();
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        CoreTuiController.from(
            UiTestFixtureFactory.defaultForgeUiState(), scheduler, Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    generationRunner.complete(Path.of("build/generated-project"));
    scheduler.runAll();

    UiAction tickAction = controller.onEvent(TickEvent.of(2, Duration.ofMillis(40)));
    assertThat(tickAction.handled()).isTrue();
    assertThat(controller.statusMessage()).contains("Generation succeeded");
  }

  @Test
  void generationProgressOverlayDisappearsAfterCompletion() {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    CoreTuiController controller =
        UiControllerTestHarness.controller(
            UiScheduler.immediate(), Duration.ZERO, generationRunner);

    controller.onEvent(KeyEvent.ofKey(KeyCode.ENTER));
    assertThat(UiControllerTestHarness.renderToString(controller, 120, 34))
        .contains("Generating Project");

    generationRunner.complete(Path.of("build/output"));
    String afterCompletion = UiControllerTestHarness.renderToString(controller, 120, 34);
    assertThat(afterCompletion).doesNotContain("Generating Project");
    assertThat(afterCompletion).contains("Generation succeeded");
  }

  private ConflictFixture createConflictFixture() throws Exception {
    UiControllerTestHarness.ControlledGenerationRunner generationRunner =
        new UiControllerTestHarness.ControlledGenerationRunner();
    Path outputRoot = tempDir.resolve("generated");
    Path existingTarget = outputRoot.resolve("forge-app");
    Files.createDirectories(existingTarget);
    MetadataCompatibilityContext metadata = MetadataCompatibilityContext.loadDefault();
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            outputRoot.toString(),
            "maven",
            "25");
    ForgeUiState state =
        new ForgeUiState(
            request,
            new ProjectRequestValidator().validate(request).merge(metadata.validate(request)),
            metadata);
    CoreTuiController controller =
        CoreTuiController.from(state, UiScheduler.immediate(), Duration.ZERO, generationRunner);
    return new ConflictFixture(controller, generationRunner);
  }
}
