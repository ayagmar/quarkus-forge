package dev.ayagmar.quarkusforge.headless;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.ayagmar.quarkusforge.util.OutputPathResolver;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_ERR)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class HeadlessOutputPrinterTest {
  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  private PrintStream originalOut;
  private PrintStream originalErr;
  private ByteArrayOutputStream stdout;
  private ByteArrayOutputStream stderr;

  @BeforeEach
  void captureOutput() {
    originalOut = System.out;
    originalErr = System.err;
    stdout = new ByteArrayOutputStream();
    stderr = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  @AfterEach
  void restoreOutput() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  @Test
  void printValidationErrorsOutputsFieldErrors() {
    ValidationReport report =
        new ValidationReport(
            List.of(
                new ValidationError("groupId", "must not be blank"),
                new ValidationError("buildTool", "unsupported")));

    HeadlessOutputPrinter.printValidationErrors(report, "live", "code.quarkus.io");

    String output = stderr.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("Input validation failed:")
        .contains("metadataSource: live")
        .contains("metadataDetail: code.quarkus.io")
        .contains("groupId: must not be blank")
        .contains("buildTool: unsupported");
  }

  @Test
  void printValidationErrorsOmitsBlankSourceFields() {
    ValidationReport report = new ValidationReport(List.of(new ValidationError("field", "error")));

    HeadlessOutputPrinter.printValidationErrors(report, "", null);

    String output = stderr.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("Input validation failed:").doesNotContain("metadataSource");
  }

  @Test
  void printDryRunSummaryOutputsAllFields() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "demo",
            "1.0.0",
            "com.example.demo",
            "/output",
            "io.quarkus.platform:3.31",
            "maven",
            "21");

    HeadlessOutputPrinter.printDryRunSummary(request, List.of("io.quarkus:quarkus-rest"), "live");

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("Dry-run validated successfully:")
        .contains("groupId: com.example")
        .contains("artifactId: demo")
        .contains("extensions: [io.quarkus:quarkus-rest]")
        .contains("catalogSource: live");
  }

  @Test
  void printDryRunSummaryDefaultsSourceLabel() {
    ProjectRequest request =
        new ProjectRequest("org.acme", "app", "1.0.0", "", ".", "", "maven", "25");

    HeadlessOutputPrinter.printDryRunSummary(request, List.of(), null);

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("catalogSource: unknown");
  }

  @Test
  void printPrefillSummaryOutputsRequestAndSource() {
    ProjectRequest request =
        new ProjectRequest(
            "com.acme", "svc", "2.0.0", "com.acme.svc", "/out", "stream:1", "gradle", "21");

    HeadlessOutputPrinter.printPrefillSummary(request, "cache", "stale");

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("Prefill validated successfully:")
        .contains("groupId: com.acme")
        .contains("buildTool: gradle")
        .contains("metadataSource: cache")
        .contains("metadataDetail: stale");
  }

  @Test
  void resolveProjectDirectoryNormalizesPath() {
    String outputDir = Path.of("/tmp", "other", "..", "out").toString();
    ProjectRequest request =
        new ProjectRequest("org.acme", "app", "1.0.0", "", outputDir, "", "maven", "25");

    Path resolved = HeadlessOutputPrinter.resolveProjectDirectory(request);

    assertThat(resolved)
        .isEqualTo(OutputPathResolver.resolveOutputRoot(outputDir).resolve("app").normalize());
  }

  @Test
  void printValidationErrorsOmitsNullSourceLabel() {
    ValidationReport report = new ValidationReport(List.of(new ValidationError("f", "e")));

    HeadlessOutputPrinter.printValidationErrors(report, null, "detail");

    String output = stderr.toString(StandardCharsets.UTF_8);
    assertThat(output).doesNotContain("metadataSource");
    assertThat(output).contains("metadataDetail: detail");
  }

  @Test
  void printValidationErrorsOmitsNullAndBlankSourceDetail() {
    ValidationReport report = new ValidationReport(List.of(new ValidationError("x", "y")));

    HeadlessOutputPrinter.printValidationErrors(report, "live", "   ");

    String output = stderr.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("metadataSource: live");
    assertThat(output).doesNotContain("metadataDetail");
  }

  @Test
  void printPrefillSummaryOmitsBlankSourceFields() {
    ProjectRequest request =
        new ProjectRequest("org.acme", "app", "1.0.0", "", ".", "", "maven", "25");

    HeadlessOutputPrinter.printPrefillSummary(request, "", null);

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output)
        .contains("Prefill validated successfully:")
        .doesNotContain("metadataSource")
        .doesNotContain("metadataDetail");
  }

  @Test
  void printPrefillSummaryIncludesNonBlankSourceFields() {
    ProjectRequest request =
        new ProjectRequest("org.acme", "app", "1.0.0", "", ".", "", "maven", "25");

    HeadlessOutputPrinter.printPrefillSummary(request, "cache", "from disk");

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("metadataSource: cache").contains("metadataDetail: from disk");
  }

  @Test
  void printDryRunSummaryWithBlankSourceLabel() {
    ProjectRequest request =
        new ProjectRequest("org.acme", "app", "1.0.0", "", ".", "", "maven", "25");

    HeadlessOutputPrinter.printDryRunSummary(request, List.of(), "");

    String output = stdout.toString(StandardCharsets.UTF_8);
    assertThat(output).contains("catalogSource: unknown");
  }

  @Test
  void resolveProjectDirectoryWithRelativePath() {
    ProjectRequest request =
        new ProjectRequest("org.acme", "my-app", "1.0.0", "", ".", "", "maven", "25");

    Path resolved = HeadlessOutputPrinter.resolveProjectDirectory(request);

    assertThat(resolved.getFileName().toString()).isEqualTo("my-app");
  }

  @Test
  void resolveProjectDirectoryExpandsTildeAgainstUserHome() {
    Path homePath = Path.of("target", "qf-headless-home").toAbsolutePath().normalize();
    systemProperties.set("user.home", homePath);
    ProjectRequest request =
        new ProjectRequest("org.acme", "my-app", "1.0.0", "", "~/Projects", "", "maven", "25");

    Path resolved = HeadlessOutputPrinter.resolveProjectDirectory(request);

    assertThat(resolved).isEqualTo(homePath.resolve("Projects").resolve("my-app").normalize());
  }
}
