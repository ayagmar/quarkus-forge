package dev.ayagmar.quarkusforge;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(
    name = "quarkus-forge",
    mixinStandardHelpOptions = true,
    versionProvider = CliVersionProvider.class,
    subcommands = {GenerateCommand.class},
    description = "Quarkus forge headless CLI")
public final class HeadlessCli implements Callable<Integer>, HeadlessRunner {
  private final HeadlessGenerationService headlessService;

  @Mixin RequestOptions requestOptions = new RequestOptions();

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
    this.headlessService =
        new HeadlessGenerationService(new HeadlessCatalogClient(runtimeConfig), runtimeConfig);
  }

  @Override
  public Integer call() {
    System.err.println("No subcommand specified. Use 'generate' to create a project.");
    return ExitCodes.VALIDATION;
  }

  @Override
  public int runHeadlessGenerate(GenerateCommand command) {
    return headlessService.run(command, dryRun, verbose);
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
