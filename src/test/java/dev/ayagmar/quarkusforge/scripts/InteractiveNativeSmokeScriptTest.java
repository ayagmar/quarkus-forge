package dev.ayagmar.quarkusforge.scripts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InteractiveNativeSmokeScriptTest {
  private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir")).toAbsolutePath();
  private static final Path SCRIPT_PATH =
      REPO_ROOT.resolve("scripts").resolve("interactive_native_smoke.py");

  @TempDir Path tempDir;

  @Test
  void passesWhenBinaryEntersAlternateScreenAndRendersVisibleMilestone() throws Exception {
    Assumptions.assumeTrue(!isWindows());
    Path fakeBinary =
        executableScript(
            "fake-tui.sh",
            """
            #!/bin/sh
            printf '\\033[?1049h'
            printf '\\033[2J\\033[H'
            printf 'Project Metadata'
            sleep 5
            """);

    ProcessResult result = runScript(fakeBinary);

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout())
        .contains("Interactive native smoke reached alternate-screen render milestone");
    assertThat(result.stderr()).isBlank();
  }

  @Test
  void failsWhenVisibleMilestoneNeverAppears() throws Exception {
    Assumptions.assumeTrue(!isWindows());
    Path fakeBinary =
        executableScript(
            "fake-tui-missing-title.sh",
            """
            #!/bin/sh
            printf '\\033[?1049h'
            printf 'Loading extension catalog...'
            sleep 1
            """);

    ProcessResult result = runScript(fakeBinary);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.stderr()).contains("Interactive smoke failed");
    assertThat(result.stderr()).contains("Loading extension catalog...");
    assertThat(result.stderr())
        .contains("Expected alternate-screen enter plus visible text markers");
  }

  private ProcessResult runScript(Path binary) throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    command.add("python3");
    command.add(SCRIPT_PATH.toString());
    command.add("--binary");
    command.add(binary.toString());
    command.add("--timeout-seconds");
    command.add("2");

    Process process = new ProcessBuilder(command).directory(REPO_ROOT.toFile()).start();
    ExecutorService streamReaders = Executors.newFixedThreadPool(2);
    Future<String> stdoutFuture =
        streamReaders.submit(
            () -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    Future<String> stderrFuture =
        streamReaders.submit(
            () -> new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
    boolean finished = process.waitFor(10, TimeUnit.SECONDS);
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
              ? "Process timed out after 10s"
              : stderr + System.lineSeparator() + "Process timed out after 10s";
    }
    return new ProcessResult(exitCode, stdout, stderr);
  }

  private Path executableScript(String fileName, String body) throws IOException {
    Path script = tempDir.resolve(fileName);
    Files.writeString(script, body, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        script,
        EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE));
    return script;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
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
