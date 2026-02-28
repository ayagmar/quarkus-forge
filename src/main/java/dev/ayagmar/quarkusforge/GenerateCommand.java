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
      description = "Extension preset(s): web, data, messaging, favorites")
  List<String> presets = new ArrayList<>();

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
