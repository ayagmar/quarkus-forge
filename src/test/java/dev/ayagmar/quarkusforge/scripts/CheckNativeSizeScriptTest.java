package dev.ayagmar.quarkusforge.scripts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckNativeSizeScriptTest {
  private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir")).toAbsolutePath();
  private static final Path SCRIPT_PATH =
      REPO_ROOT.resolve("scripts").resolve("CheckNativeSize.java");
  private static final Path FIXTURES_DIR =
      REPO_ROOT
          .resolve("src")
          .resolve("test")
          .resolve("resources")
          .resolve("native-size")
          .resolve("fixtures");

  @TempDir Path tempDir;

  @Test
  void printsCiSummaryWithTopOrigins() throws Exception {
    Path binary = binaryFile("quarkus-forge", 2048);

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            fixture("valid-build-report.html").toString(),
            "--log",
            fixture("native.log").toString(),
            "--max-bytes",
            "4096",
            "--top-count",
            "2");

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("### native");
    assertThat(result.stdout()).contains("- Build report image total: `23.80MB`");
    assertThat(result.stdout()).contains("- Top code origins:");
    assertThat(result.stdout()).contains("`com.example:demo-app`: `3.10MB`");
    assertThat(result.stdout()).contains("`org.acme:dependency`: `1.25MB`");
  }

  @Test
  void reportsMissingOriginsAsUnavailable() throws Exception {
    Path binary = binaryFile("quarkus-forge", 1024);

    ProcessResult result =
        runScript(
            "--label",
            "headless-native",
            "--binary",
            binary.toString(),
            "--report",
            fixture("valid-build-report.html").toString(),
            "--log",
            fixture("native-no-origins.log").toString(),
            "--max-bytes",
            "2048");

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("- Top code origins: unavailable from native-image log");
  }

  @Test
  void parsesFormattedBuildReportWithWhitespace() throws Exception {
    Path binary = binaryFile("quarkus-forge", 2048);

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            fixture("formatted-build-report.html").toString(),
            "--log",
            fixture("native.log").toString(),
            "--max-bytes",
            "4096");

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout()).contains("- Build report image total: `23.80 MB`");
    assertThat(result.stdout()).contains("- Build report code area: `9.48 MB`");
    assertThat(result.stdout()).contains("- Build report image heap: `8.25 MB`");
  }

  @Test
  void rejectsMalformedReport() throws Exception {
    Path binary = binaryFile("quarkus-forge", 1024);

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            fixture("malformed-build-report.html").toString(),
            "--log",
            fixture("native.log").toString(),
            "--max-bytes",
            "2048");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(
            "failed to parse native build report: "
                + fixture("malformed-build-report.html").toString());
  }

  @Test
  void rejectsReportMissingRequiredSections() throws Exception {
    Path binary = binaryFile("quarkus-forge", 1024);

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            fixture("missing-image-heap-build-report.html").toString(),
            "--log",
            fixture("native.log").toString(),
            "--max-bytes",
            "2048");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr())
        .contains(
            "failed to parse native build report: "
                + fixture("missing-image-heap-build-report.html").toString());
  }

  @Test
  void rejectsNegativeMaxBytes() throws Exception {
    Path binary = binaryFile("binary", 16);
    Path report = textFile("report.html", "stub");
    Path log = textFile("build.log", "stub");

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            report.toString(),
            "--log",
            log.toString(),
            "--max-bytes",
            "-1");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("--max-bytes must be >= 0");
  }

  @Test
  void rejectsNonPositiveTopCount() throws Exception {
    Path binary = binaryFile("binary", 16);
    Path report = textFile("report.html", "stub");
    Path log = textFile("build.log", "stub");

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            report.toString(),
            "--log",
            log.toString(),
            "--max-bytes",
            "100",
            "--top-count",
            "0");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("--top-count must be >= 1");
  }

  @Test
  void exitsWhenBinaryExceedsBudget() throws Exception {
    Path binary = binaryFile("quarkus-forge", 4096);

    ProcessResult result =
        runScript(
            "--label",
            "native",
            "--binary",
            binary.toString(),
            "--report",
            fixture("valid-build-report.html").toString(),
            "--log",
            fixture("native.log").toString(),
            "--max-bytes",
            "1024");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("native binary size 4096 exceeds budget 1024");
  }

  private ProcessResult runScript(String... args) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add(SCRIPT_PATH.toString());
    command.addAll(List.of(args));

    Process process = new ProcessBuilder(command).directory(REPO_ROOT.toFile()).start();
    ExecutorService streamReaders = Executors.newFixedThreadPool(2);
    Future<String> stdoutFuture =
        streamReaders.submit(
            () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    Future<String> stderrFuture =
        streamReaders.submit(
            () -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
    boolean finished = process.waitFor(30, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
    }
    streamReaders.shutdown();
    String stdout = readProcessStream(stdoutFuture);
    String stderr = readProcessStream(stderrFuture);
    int exitCode = finished ? process.exitValue() : 124;
    if (!finished) {
      stderr =
          stderr.isBlank()
              ? "Process timed out after 30s"
              : stderr + System.lineSeparator() + "Process timed out after 30s";
    }
    return new ProcessResult(exitCode, stdout, stderr);
  }

  private Path fixture(String fileName) {
    return FIXTURES_DIR.resolve(fileName);
  }

  private Path binaryFile(String name, int size) throws IOException {
    Path binary = tempDir.resolve(name);
    Files.write(binary, new byte[size]);
    return binary;
  }

  private Path textFile(String name, String content) throws IOException {
    Path file = tempDir.resolve(name);
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  private record ProcessResult(int exitCode, String stdout, String stderr) {}

  private static String readProcessStream(Future<String> streamFuture) throws InterruptedException {
    try {
      return streamFuture.get(5, TimeUnit.SECONDS);
    } catch (ExecutionException executionException) {
      throw new IllegalStateException("Failed to read script output", executionException);
    } catch (java.util.concurrent.TimeoutException timeoutException) {
      throw new IllegalStateException("Timed out reading script output", timeoutException);
    }
  }
}
