package dev.ayagmar.quarkusforge.cli;

import dev.ayagmar.quarkusforge.headless.HeadlessGenerationService;
import dev.ayagmar.quarkusforge.runtime.RuntimeConfig;
import dev.ayagmar.quarkusforge.runtime.RuntimeWiring;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "quarkus-forge",
    mixinStandardHelpOptions = true,
    versionProvider = CliVersionProvider.class,
    subcommands = {GenerateCommand.class},
    description = "Quarkus forge headless CLI")
public final class HeadlessCli implements Callable<Integer>, HeadlessRunner {
  private final RuntimeConfig runtimeConfig;

  @Spec CommandLine.Model.CommandSpec spec;

  @Option(
      names = "--verbose",
      defaultValue = "false",
      description = "Emit structured JSON-line diagnostics to stderr")
  boolean verbose;

  @Option(
      names = "--dry-run",
      defaultValue = "false",
      description = "Validate options and print request summary without generating files")
  boolean dryRun;

  HeadlessCli() {
    this(RuntimeConfig.defaults());
  }

  HeadlessCli(RuntimeConfig runtimeConfig) {
    this.runtimeConfig = Objects.requireNonNull(runtimeConfig);
  }

  @Override
  public Integer call() {
    spec.commandLine().usage(System.out);
    return ExitCodes.OK;
  }

  @Override
  public int runHeadlessGenerate(GenerateCommand command) {
    try (HeadlessGenerationService service =
        RuntimeWiring.headlessGenerationService(runtimeConfig)) {
      return service.run(command, dryRun, verbose);
    }
  }

  public static int runWithArgs(String[] args) {
    return runWithArgs(args, RuntimeConfig.defaults());
  }

  static int runWithArgs(String[] args, RuntimeConfig runtimeConfig) {
    return new CommandLine(new HeadlessCli(runtimeConfig)).execute(args);
  }

  public static void main(String[] args) {
    int exitCode = runWithArgs(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }
}
