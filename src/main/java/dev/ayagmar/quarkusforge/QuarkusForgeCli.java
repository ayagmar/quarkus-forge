package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.QuarkusApiClient;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.ui.CoreTuiController;
import dev.ayagmar.quarkusforge.ui.UiScheduler;
import dev.tamboui.tui.TuiRunner;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "quarkus-forge",
    version = "0.1.0-SNAPSHOT",
    mixinStandardHelpOptions = true,
    description = "Quarkus forge terminal UI")
public final class QuarkusForgeCli implements Callable<Integer> {
  @Option(
      names = {"-g", "--group-id"},
      defaultValue = "org.acme",
      description = "Maven group id")
  private String groupId;

  @Option(
      names = {"-a", "--artifact-id"},
      defaultValue = "quarkus-app",
      description = "Maven artifact id")
  private String artifactId;

  @Option(
      names = {"-v", "--project-version"},
      defaultValue = "1.0.0-SNAPSHOT",
      description = "Project version")
  private String version;

  @Option(
      names = {"-p", "--package-name"},
      description = "Base package name (defaults from group/artifact)")
  private String packageName;

  @Option(
      names = {"-o", "--output-dir"},
      defaultValue = ".",
      description = "Output directory")
  private String outputDirectory;

  @Option(
      names = {"-b", "--build-tool"},
      defaultValue = "maven",
      description = "Build tool (metadata-driven)")
  private String buildTool;

  @Option(
      names = {"-j", "--java-version"},
      defaultValue = "25",
      description = "Java version for generated project (metadata-driven)")
  private String javaVersion;

  @Option(
      names = "--dry-run",
      defaultValue = "false",
      description = "Validate CLI prefill and print summary without starting TUI")
  private boolean dryRun;

  @Option(
      names = "--smoke",
      defaultValue = "false",
      description = "Start the TUI and auto-exit after a short delay")
  private boolean smokeMode;

  @Option(
      names = "--search-debounce-ms",
      defaultValue = "120",
      description = "Debounce delay for extension search updates")
  private int searchDebounceMs;

  @Override
  public Integer call() throws Exception {
    ForgeUiState initialState = buildInitialState();
    if (!initialState.canSubmit()) {
      printValidationErrors(initialState.validation());
      return CommandLine.ExitCode.USAGE;
    }

    if (dryRun) {
      printPrefillSummary(initialState.request());
      return CommandLine.ExitCode.OK;
    }

    runTui(smokeMode, initialState, searchDebounceMs);
    return CommandLine.ExitCode.OK;
  }

  public static int runWithArgs(String[] args) {
    return new CommandLine(new QuarkusForgeCli()).execute(args);
  }

  public static void main(String[] args) {
    int exitCode = runWithArgs(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static void runTui(boolean smokeMode, ForgeUiState initialState, int searchDebounceMs)
      throws Exception {
    try (var tui = TuiRunner.create()) {
      QuarkusApiClient apiClient = new QuarkusApiClient(URI.create("https://code.quarkus.io"));
      CoreTuiController controller =
          CoreTuiController.from(
              initialState,
              UiScheduler.fromScheduledExecutor(tui.scheduler(), tui::runOnRenderThread),
              Duration.ofMillis(Math.max(0, searchDebounceMs)));
      controller.loadExtensionCatalogAsync(apiClient::fetchExtensions);
      if (smokeMode) {
        tui.scheduler().schedule(tui::quit, 350, TimeUnit.MILLISECONDS);
      }

      tui.run(
          (event, runner) -> {
            CoreTuiController.UiAction action = controller.onEvent(event);
            if (action.shouldQuit()) {
              runner.quit();
            }
            return action.handled();
          },
          controller::render);
    }
  }

  private ForgeUiState buildInitialState() {
    CliPrefill prefill =
        new CliPrefill(
            groupId, artifactId, version, packageName, outputDirectory, buildTool, javaVersion);
    ProjectRequest request = CliPrefillMapper.map(prefill);
    MetadataCompatibilityContext metadataCompatibility = MetadataCompatibilityContext.loadDefault();

    ValidationReport fieldValidation = new ProjectRequestValidator().validate(request);
    ValidationReport compatibilityValidation = metadataCompatibility.validate(request);
    return new ForgeUiState(
        request, fieldValidation.merge(compatibilityValidation), metadataCompatibility);
  }

  private static void printValidationErrors(ValidationReport validation) {
    System.err.println("Input validation failed:");
    for (var error : validation.errors()) {
      System.err.println(" - " + error.field() + ": " + error.message());
    }
  }

  private static void printPrefillSummary(ProjectRequest request) {
    System.out.println("Prefill validated successfully:");
    System.out.println(" - groupId: " + request.groupId());
    System.out.println(" - artifactId: " + request.artifactId());
    System.out.println(" - version: " + request.version());
    System.out.println(" - packageName: " + request.packageName());
    System.out.println(" - outputDirectory: " + request.outputDirectory());
    System.out.println(" - buildTool: " + request.buildTool());
    System.out.println(" - javaVersion: " + request.javaVersion());
  }
}
