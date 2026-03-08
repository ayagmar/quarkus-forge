package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileLock;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class HeadlessForgefilePersistenceService {
  void validateLockDrift(
      HeadlessGenerationInputs inputs, ProjectRequest request, List<String> extensionIds) {
    validateLockDrift(inputs.forgefile(), request, inputs.presetInputs(), extensionIds);
  }

  void persist(HeadlessGenerationInputs inputs, ProjectRequest request, List<String> extensionIds) {
    saveForgefileIfRequested(
        inputs.template(),
        inputs.presetInputs(),
        inputs.extensionInputs(),
        inputs.writeLock(),
        inputs.saveAsFile(),
        inputs.forgefilePath(),
        request,
        extensionIds);
  }

  void validateLockDrift(
      Forgefile forgefile,
      ProjectRequest request,
      List<String> presetInputs,
      List<String> extensionIds) {
    if (forgefile == null || forgefile.locked() == null) {
      return;
    }
    ForgefileLock lock = forgefile.locked();

    List<ValidationError> errors = new ArrayList<>();
    checkDrift(errors, "platformStream", lock.platformStream(), request.platformStream());
    checkDrift(errors, "buildTool", lock.buildTool(), request.buildTool());
    checkDrift(errors, "javaVersion", lock.javaVersion(), request.javaVersion());
    List<String> lockedExtensions = lock.extensions() == null ? List.of() : lock.extensions();
    if (!HeadlessExtensionResolutionService.normalizedExtensionIdsForComparison(lockedExtensions)
        .equals(
            HeadlessExtensionResolutionService.normalizedExtensionIdsForComparison(extensionIds))) {
      errors.add(
          new ValidationError(
              "locked",
              "extensions drift: locked=" + lockedExtensions + ", request=" + extensionIds));
    }
    List<String> normalizedPresets =
        HeadlessExtensionResolutionService.normalizePresets(presetInputs);
    List<String> lockedPresets = lock.presets() == null ? List.of() : lock.presets();
    if (!HeadlessExtensionResolutionService.normalizedPresetIdsForComparison(lockedPresets)
        .equals(
            HeadlessExtensionResolutionService.normalizedPresetIdsForComparison(
                normalizedPresets))) {
      errors.add(
          new ValidationError(
              "locked",
              "presets drift: locked=" + lockedPresets + ", request=" + normalizedPresets));
    }

    if (!errors.isEmpty()) {
      errors.add(
          new ValidationError(
              "locked", "rerun with --lock to accept and update the locked section"));
      throw new ValidationException(errors);
    }
  }

  void saveForgefileIfRequested(
      Forgefile template,
      List<String> presetInputs,
      List<String> extensionInputs,
      boolean writeLock,
      Path saveAsFile,
      Path forgefilePath,
      ProjectRequest request,
      List<String> extensionIds) {
    List<String> normalizedPresets =
        HeadlessExtensionResolutionService.normalizePresets(presetInputs);
    Forgefile forgefile = template.withSelections(normalizedPresets, extensionInputs);
    if (writeLock) {
      ForgefileLock lock =
          ForgefileLock.of(
              request.platformStream(),
              request.buildTool(),
              request.javaVersion(),
              normalizedPresets,
              extensionIds);
      forgefile = forgefile.withLock(lock);
    }

    Path writePath = saveAsFile;
    if (writePath == null && writeLock && forgefilePath != null) {
      writePath = forgefilePath;
    }
    if (writePath == null) {
      return;
    }
    ForgefileStore.save(writePath, forgefile);
  }

  private static void checkDrift(
      List<ValidationError> errors, String field, String locked, String actual) {
    if (!Objects.equals(locked, actual)) {
      errors.add(
          new ValidationError(
              "locked", field + " drift: locked='" + locked + "', request='" + actual + "'"));
    }
  }
}
