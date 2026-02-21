package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.ExtensionDto;
import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityValidator;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CoreTuiExtensionSearchPilotTest {
  @Test
  void apiLoadedCatalogIsIndexedAndSearchable() {
    CoreTuiController controller =
        CoreTuiController.from(validInitialState(), UiScheduler.immediate(), Duration.ZERO);
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                List.of(
                    new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                    new ExtensionDto("io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi"),
                    new ExtensionDto(
                        "io.quarkus:quarkus-jdbc-postgresql",
                        "JDBC PostgreSQL",
                        "jdbc-postgresql"))));

    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    controller.onEvent(KeyEvent.ofChar('j'));
    controller.onEvent(KeyEvent.ofChar('d'));
    controller.onEvent(KeyEvent.ofChar('b'));

    assertThat(controller.filteredExtensionCount()).isEqualTo(1);
    assertThat(controller.firstFilteredExtensionId()).contains("jdbc-postgresql");
  }

  @Test
  void selectionByStableIdPersistsAcrossFiltering() {
    CoreTuiController controller =
        CoreTuiController.from(validInitialState(), UiScheduler.immediate(), Duration.ZERO);
    controller.loadExtensionCatalogAsync(
        () ->
            CompletableFuture.completedFuture(
                List.of(
                    new ExtensionDto("io.quarkus:quarkus-rest", "REST", "rest"),
                    new ExtensionDto(
                        "io.quarkus:quarkus-rest-client", "REST Client", "rest-client"),
                    new ExtensionDto(
                        "io.quarkus:quarkus-jdbc-postgresql",
                        "JDBC PostgreSQL",
                        "jdbc-postgresql"))));

    moveFocusTo(controller, FocusTarget.EXTENSION_LIST);
    controller.onEvent(KeyEvent.ofChar(' '));
    String selectedId = controller.selectedExtensionIds().getFirst();

    moveFocusTo(controller, FocusTarget.EXTENSION_SEARCH);
    for (char character : "jdbc".toCharArray()) {
      controller.onEvent(KeyEvent.ofChar(character));
    }
    assertThat(controller.selectedExtensionIds()).contains(selectedId);

    for (int i = 0; i < 4; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.BACKSPACE));
    }
    assertThat(controller.selectedExtensionIds()).contains(selectedId);
  }

  private static void moveFocusTo(CoreTuiController controller, FocusTarget target) {
    for (int i = 0; i < 20 && controller.focusTarget() != target; i++) {
      controller.onEvent(KeyEvent.ofKey(KeyCode.TAB));
    }
    assertThat(controller.focusTarget()).isEqualTo(target);
  }

  private static ForgeUiState validInitialState() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "./generated",
            "maven",
            "25");
    ValidationReport validation =
        new ProjectRequestValidator()
            .validate(request)
            .merge(
                new MetadataCompatibilityValidator()
                    .validate(request, MetadataSnapshotLoader.loadDefault()));
    return new ForgeUiState(request, validation);
  }
}
