package dev.ayagmar.quarkusforge.scripts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationScriptsTest {
  private static final Path VERIFY_DIR =
      Path.of("scripts").resolve("verify").toAbsolutePath().normalize();

  @Test
  void verificationScriptsExistAndUseStrictShellMode() throws Exception {
    List<Path> scripts = listScripts();

    assertThat(scripts)
        .extracting(path -> VERIFY_DIR.relativize(path).toString())
        .containsExactlyInAnyOrder(
            "_lib.sh",
            "coverage.sh",
            "docs-build.sh",
            "docs-linkcheck.sh",
            "format-check.sh",
            "headless-compile.sh",
            "integration.sh",
            "native-interactive-smoke-posix.sh",
            "native-interactive-smoke-windows.sh",
            "native-release-smoke.sh",
            "native-size.sh",
            "unit.sh",
            "verify.sh");

    for (Path script : scripts) {
      String content = Files.readString(script, StandardCharsets.UTF_8);
      assertThat(content).startsWith("#!/usr/bin/env bash\n");
      assertThat(content).contains("set -euo pipefail");
      if (!script.getFileName().toString().equals("_lib.sh")) {
        assertThat(content).contains("_lib.sh");
      }
    }
  }

  @Test
  void smokeScriptsDelegateToSharedInteractiveSmokeMechanisms() throws Exception {
    String posixSmoke =
        Files.readString(
            VERIFY_DIR.resolve("native-interactive-smoke-posix.sh"), StandardCharsets.UTF_8);
    String windowsSmoke =
        Files.readString(
            VERIFY_DIR.resolve("native-interactive-smoke-windows.sh"), StandardCharsets.UTF_8);
    String releaseSmoke =
        Files.readString(VERIFY_DIR.resolve("native-release-smoke.sh"), StandardCharsets.UTF_8);

    assertThat(posixSmoke).contains("scripts/interactive_native_smoke.py");
    assertThat(windowsSmoke).contains("--interactive-smoke-test --verbose");
    assertThat(windowsSmoke).contains("\"event\":\"tui.render.ready\"");
    assertThat(releaseSmoke).contains("\"$binary\" --help > /dev/null");
    assertThat(releaseSmoke).contains("scripts/verify/native-interactive-smoke-posix.sh");
    assertThat(releaseSmoke).contains("scripts/verify/native-interactive-smoke-windows.sh");
  }

  @Test
  void nativeSizeScriptRecreatesLogDirectoryAfterClean() throws Exception {
    String nativeSize =
        Files.readString(VERIFY_DIR.resolve("native-size.sh"), StandardCharsets.UTF_8);

    assertThat(nativeSize)
        .contains(
            """
            headless)
                ./mvnw clean
                mkdir -p target/native-size
            """);
  }

  private static List<Path> listScripts() throws IOException {
    try (var stream = Files.list(VERIFY_DIR)) {
      return stream.filter(path -> path.getFileName().toString().endsWith(".sh")).toList();
    }
  }
}
