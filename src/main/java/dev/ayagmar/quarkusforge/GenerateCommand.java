package dev.ayagmar.quarkusforge;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "generate", description = "Generate a Quarkus project without starting the TUI")
public final class GenerateCommand implements Callable<Integer> {
  @ParentCommand private QuarkusForgeCli rootCommand;

  @Mixin RequestOptions requestOptions = new RequestOptions();

  @Option(
      names = {"-e", "--extension"},
      split = ",",
      description = "Extension id to include (repeatable and comma-separated)")
  List<String> extensions = new ArrayList<>();

  @Option(
      names = "--preset",
      split = ",",
      description = "Extension preset(s) from code.quarkus.io plus favorites")
  List<String> presets = new ArrayList<>();

  @Option(
      names = "--recipe",
      description =
          "Path or recipe name for Forge recipe file. "
              + "If local path is missing, resolves from ~/.quarkus-forge/recipes")
  String recipeFile;

  @Option(
      names = "--write-recipe",
      description =
          "Write effective recipe to this path. "
              + "Simple names are written under ~/.quarkus-forge/recipes")
  String writeRecipeFile;

  @Option(names = "--lock", description = "Path to lock file (example: forge.lock)")
  String lockFile;

  @Option(names = "--write-lock", description = "Write lock file to this path")
  String writeLockFile;

  @Option(
      names = "--refresh-lock",
      defaultValue = "false",
      description = "Allow lock drift and rewrite lock (requires --lock or --write-lock)")
  boolean refreshLock;

  @Option(
      names = "--dry-run",
      defaultValue = "false",
      description = "Validate full generation request without writing files")
  boolean dryRun;

  @Override
  public Integer call() {
    return rootCommand.runHeadlessGenerate(this);
  }
}
