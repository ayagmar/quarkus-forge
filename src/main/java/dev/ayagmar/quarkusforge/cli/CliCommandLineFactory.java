package dev.ayagmar.quarkusforge.cli;

import picocli.CommandLine;

final class CliCommandLineFactory {
  private CliCommandLineFactory() {}

  static <T extends HeadlessRunner> CommandLine create(T command) {
    CommandLine commandLine = new CommandLine(command);
    addRequestOptionsMixin(commandLine, command);

    GenerateCommand generateCommand = new GenerateCommand(command);
    CommandLine generateCommandLine = new CommandLine(generateCommand);
    generateCommandLine.addMixin("requestOptions", generateCommand.requestOptions());
    commandLine.addSubcommand("generate", generateCommandLine);

    commandLine.getCommandSpec().version(CliVersionProvider.resolveVersion());
    commandLine.setExecutionStrategy(
        parseResult -> {
          recordMatchedRequestOptions(parseResult);
          return new CommandLine.RunLast().execute(parseResult);
        });
    return commandLine;
  }

  private static void addRequestOptionsMixin(CommandLine commandLine, HeadlessRunner command) {
    if (command instanceof QuarkusForgeCli quarkusForgeCli) {
      commandLine.addMixin("requestOptions", quarkusForgeCli.requestOptions());
    }
  }

  private static void recordMatchedRequestOptions(CommandLine.ParseResult parseResult) {
    for (CommandLine.ParseResult current = parseResult;
        current != null;
        current = current.subcommand()) {
      Object userObject = current.commandSpec().userObject();
      if (userObject instanceof QuarkusForgeCli quarkusForgeCli) {
        quarkusForgeCli.requestOptions().recordMatchedOptions(current);
      } else if (userObject instanceof GenerateCommand generateCommand) {
        generateCommand.requestOptions().recordMatchedOptions(current);
      }
    }
  }
}
