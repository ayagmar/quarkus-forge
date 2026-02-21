package dev.ayagmar.quarkusforge;

import dev.ayagmar.quarkusforge.api.ApiContractException;
import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import dev.ayagmar.quarkusforge.domain.CliPrefill;
import dev.ayagmar.quarkusforge.domain.CliPrefillMapper;
import dev.ayagmar.quarkusforge.domain.ForgeUiState;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityValidator;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ProjectRequestValidator;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.paragraph.Paragraph;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "quarkus-forge",
    version = "0.1.0-SNAPSHOT",
    description = "Quarkus forge terminal UI")
public final class QuarkusForgeCli implements Callable<Integer> {
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      defaultValue = "false",
      description = "Show this help message and exit")
  private boolean helpRequested;

  @Option(
      names = {"-V", "--version"},
      versionHelp = true,
      defaultValue = "false",
      description = "Print version information and exit")
  private boolean versionRequested;

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

    runTui(smokeMode, initialState);
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

  static void runTui(boolean smokeMode, ForgeUiState initialState) throws Exception {
    try (var tui = TuiRunner.create()) {
      if (smokeMode) {
        tui.scheduler().schedule(tui::quit, 350, TimeUnit.MILLISECONDS);
      }

      tui.run(
          QuarkusForgeCli::handleEvent,
          frame -> frame.renderWidget(renderHome(initialState.request()), frame.area()));
    }
  }

  private ForgeUiState buildInitialState() {
    CliPrefill prefill =
        new CliPrefill(
            groupId, artifactId, version, packageName, outputDirectory, buildTool, javaVersion);
    ProjectRequest request = CliPrefillMapper.map(prefill);

    ValidationReport fieldValidation = new ProjectRequestValidator().validate(request);
    ValidationReport compatibilityValidation = metadataCompatibilityValidation(request);
    return new ForgeUiState(request, fieldValidation.merge(compatibilityValidation));
  }

  private ValidationReport metadataCompatibilityValidation(ProjectRequest request) {
    try {
      return new MetadataCompatibilityValidator()
          .validate(request, MetadataSnapshotLoader.loadDefault());
    } catch (ApiContractException contractException) {
      List<ValidationError> errors = new ArrayList<>();
      errors.add(new ValidationError("metadata", contractException.getMessage()));
      return new ValidationReport(errors);
    }
  }

  private static boolean handleEvent(Event event, TuiRunner runner) {
    if (event instanceof KeyEvent keyEvent && (keyEvent.isQuit() || keyEvent.isCancel())) {
      runner.quit();
      return true;
    }
    return false;
  }

  private static Paragraph renderHome(ProjectRequest request) {
    String content =
        "Quarkus Forge is ready. Press 'q' to quit.\n\n"
            + "groupId="
            + request.groupId()
            + "\n"
            + "artifactId="
            + request.artifactId()
            + "\n"
            + "version="
            + request.version()
            + "\n"
            + "packageName="
            + request.packageName()
            + "\n"
            + "outputDir="
            + request.outputDirectory()
            + "\n"
            + "buildTool="
            + request.buildTool()
            + "\n"
            + "javaVersion="
            + request.javaVersion();

    return Paragraph.builder().text(Text.from(content)).build();
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
