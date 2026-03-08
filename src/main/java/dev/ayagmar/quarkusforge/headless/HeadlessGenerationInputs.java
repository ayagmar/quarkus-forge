package dev.ayagmar.quarkusforge.headless;

import dev.ayagmar.quarkusforge.forge.Forgefile;
import java.nio.file.Path;
import java.util.List;

record HeadlessGenerationInputs(
    Forgefile template,
    List<String> presetInputs,
    List<String> extensionInputs,
    Forgefile forgefile,
    Path forgefilePath,
    boolean writeLock,
    boolean lockCheck,
    Path saveAsFile) {}
