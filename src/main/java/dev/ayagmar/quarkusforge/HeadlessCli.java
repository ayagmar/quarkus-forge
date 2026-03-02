package dev.ayagmar.quarkusforge;

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
  private final HeadlessGenerationService headlessGenerationService;

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
    this.headlessGenerationService =
        new HeadlessGenerationService(new HeadlessCatalogClient(runtimeConfig), runtimeConfig);
  }

  @Override
  public Integer call() {
    // No subcommand provided — print help and exit successfully.
    // Returning a non-zero code here would break `if [ $? -eq 0 ]` wrappers that
    // invoke the jar with no arguments just to inspect the help text.
    spec.commandLine().usage(System.out);
    return ExitCodes.OK;
  }

  @Override
  public int runHeadlessGenerate(GenerateCommand command) {
    return headlessGenerationService.run(command, dryRun, verbose);
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
