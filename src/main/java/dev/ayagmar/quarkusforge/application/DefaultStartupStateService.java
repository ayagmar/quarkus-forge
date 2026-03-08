package dev.ayagmar.quarkusforge.application;

import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.ProjectInputDefaults;

public final class DefaultStartupStateService implements StartupStateService {
  @Override
  public StartupState resolve(StartupRequest request) {
    StartupMetadataSelection startupMetadataSelection = request.metadataLoader().load();
    CliPrefill effectivePrefill =
        mergePrefill(request.requestedPrefill(), request.storedPrefill(), defaultPrefill());
    ForgeUiState initialState =
        InputResolutionService.resolveInitialState(
            effectivePrefill, startupMetadataSelection.metadataCompatibility());
    return new StartupState(initialState, startupMetadataSelection);
  }

  private static CliPrefill defaultPrefill() {
    return new CliPrefill(
        ProjectInputDefaults.GROUP_ID,
        ProjectInputDefaults.ARTIFACT_ID,
        ProjectInputDefaults.VERSION,
        null,
        ProjectInputDefaults.OUTPUT_DIRECTORY,
        ProjectInputDefaults.PLATFORM_STREAM,
        ProjectInputDefaults.BUILD_TOOL,
        ProjectInputDefaults.JAVA_VERSION);
  }

  private static CliPrefill mergePrefill(
      CliPrefill requestedPrefill, CliPrefill storedPrefill, CliPrefill defaults) {
    return new CliPrefill(
        preferRequested(requestedPrefill.groupId(), storedPrefill, CliPrefill::groupId, defaults),
        preferRequested(
            requestedPrefill.artifactId(), storedPrefill, CliPrefill::artifactId, defaults),
        preferRequested(requestedPrefill.version(), storedPrefill, CliPrefill::version, defaults),
        preferRequested(
            requestedPrefill.packageName(), storedPrefill, CliPrefill::packageName, defaults),
        preferRequested(
            requestedPrefill.outputDirectory(),
            storedPrefill,
            CliPrefill::outputDirectory,
            defaults),
        preferRequested(
            requestedPrefill.platformStream(), storedPrefill, CliPrefill::platformStream, defaults),
        preferRequested(
            requestedPrefill.buildTool(), storedPrefill, CliPrefill::buildTool, defaults),
        preferRequested(
            requestedPrefill.javaVersion(), storedPrefill, CliPrefill::javaVersion, defaults));
  }

  private static String preferRequested(
      String requestedValue, CliPrefill storedPrefill, PrefillField field, CliPrefill defaults) {
    if (hasText(requestedValue)) {
      return requestedValue.strip();
    }
    if (storedPrefill != null) {
      String storedValue = field.read(storedPrefill);
      if (hasText(storedValue)) {
        return storedValue.strip();
      }
    }
    return field.read(defaults);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @FunctionalInterface
  private interface PrefillField {
    String read(CliPrefill prefill);
  }
}
