package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;

final class UiTestFixtureFactory {
  private UiTestFixtureFactory() {}

  static ForgeUiState defaultForgeUiState() {
    return defaultForgeUiState("maven");
  }

  static ForgeUiState defaultForgeUiState(String buildTool) {
    MetadataCompatibilityContext metadataCompatibility = MetadataCompatibilityContext.loadDefault();
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "./generated",
            buildTool,
            "25");
    ValidationReport validation =
        new ProjectRequestValidator()
            .validate(request)
            .merge(metadataCompatibility.validate(request));
    return new ForgeUiState(request, validation, metadataCompatibility);
  }

  static UiState.ExtensionView defaultExtensionView() {
    return new UiState.ExtensionView(7, 7, 0, false, false, "", "", "", "");
  }
}
