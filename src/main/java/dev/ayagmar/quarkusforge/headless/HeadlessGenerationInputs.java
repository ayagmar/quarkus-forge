package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.api.ForgeDataPaths;
import dev.ayagmar.quarkusforge.cli.GenerateCommand;
import dev.ayagmar.quarkusforge.forge.Forgefile;
import dev.ayagmar.quarkusforge.forge.ForgefileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

record HeadlessGenerationInputs(
    Forgefile template,
    List<String> presetInputs,
    List<String> extensionInputs,
    Forgefile forgefile,
    Path forgefilePath,
    boolean writeLock,
    boolean lockCheck,
    Path saveAsFile) {
  static HeadlessGenerationInputs fromCommand(GenerateCommand command) {
    Forgefile template = command.explicitTemplate();
    List<String> presetInputs = new ArrayList<>(command.presets());
    List<String> extensionInputs = new ArrayList<>(command.extensions());
    Forgefile forgefile = null;
    Path forgefilePath = null;
    boolean hasFromFile = command.fromFile() != null && !command.fromFile().isBlank();
    Path saveAsPath = saveAsPathOrNull(command.saveAsFile());

    if (command.lock() && !hasFromFile && saveAsPath == null) {
      throw new IllegalArgumentException("--lock requires --from or --save-as");
    }
    if (command.lockCheck() && !hasFromFile) {
      throw new IllegalArgumentException(
          "--lock-check requires --from pointing to a Forgefile with a locked section");
    }

    if (hasFromFile) {
      forgefilePath = resolveForgefileReadPath(command.fromFile());
      forgefile = ForgefileStore.load(forgefilePath);
      template = forgefile.withOverrides(template);
      presetInputs = new ArrayList<>(new LinkedHashSet<>(safeSelections(forgefile.presets())));
      presetInputs.addAll(command.presets());
      extensionInputs =
          new ArrayList<>(new LinkedHashSet<>(safeSelections(forgefile.extensions())));
      extensionInputs.addAll(command.extensions());
    }

    if (command.lockCheck() && (forgefile == null || forgefile.locked() == null)) {
      throw new IllegalArgumentException(
          "--lock-check requires --from pointing to a Forgefile with a locked section");
    }

    return new HeadlessGenerationInputs(
        template,
        List.copyOf(presetInputs),
        List.copyOf(extensionInputs),
        forgefile,
        forgefilePath,
        command.lock(),
        command.lockCheck(),
        saveAsPath);
  }

  private static List<String> safeSelections(List<String> values) {
    return values == null ? List.of() : values;
  }

  private static Path resolveForgefileReadPath(String reference) {
    Path requestedPath = Path.of(reference);
    Path localPath = requestedPath.toAbsolutePath().normalize();
    if (requestedPath.isAbsolute() || Files.exists(localPath)) {
      return localPath;
    }
    return ForgeDataPaths.recipesRoot().resolve(requestedPath).toAbsolutePath().normalize();
  }

  private static Path saveAsPathOrNull(String pathValue) {
    if (pathValue == null || pathValue.isBlank()) {
      return null;
    }
    Path requestedPath = Path.of(pathValue);
    if (requestedPath.isAbsolute() || requestedPath.getParent() != null) {
      return requestedPath.toAbsolutePath().normalize();
    }
    return ForgeDataPaths.recipesRoot().resolve(requestedPath).toAbsolutePath().normalize();
  }
}
