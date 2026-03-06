package dev.ayagmar.quarkusforge.cli;

import dev.ayagmar.quarkusforge.headless.HeadlessGenerationService;

/**
 * Implemented by CLI entry points that delegate headless project generation to {@link
 * HeadlessGenerationService}. Both {@link QuarkusForgeCli} (TUI entry point) and {@link
 * HeadlessCli} (headless-only jar) implement this interface so {@link GenerateCommand} does not
 * need to know which jar it is running inside.
 */
interface HeadlessRunner {
  int runHeadlessGenerate(GenerateCommand command);
}
