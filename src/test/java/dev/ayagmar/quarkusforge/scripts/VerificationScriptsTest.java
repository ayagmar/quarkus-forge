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
            "coverage.sh",
            "docs-build.sh",
            "docs-linkcheck.sh",
            "format-check.sh",
            "headless-compile.sh",
            "integration.sh",
            "native-interactive-smoke-posix.sh",
            "native-interactive-smoke-windows.sh",
            "native-size.sh",
            "unit.sh",
            "verify.sh");

    for (Path script : scripts) {
      String content = Files.readString(script, StandardCharsets.UTF_8);
      assertThat(content).startsWith("#!/usr/bin/env bash\n");
      assertThat(content).contains("set -euo pipefail");
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

    assertThat(posixSmoke).contains("scripts/interactive_native_smoke.py");
    assertThat(windowsSmoke).contains("--interactive-smoke-test --verbose");
    assertThat(windowsSmoke).contains("\"event\":\"tui.render.ready\"");
  }

  private static List<Path> listScripts() throws IOException {
    try (var stream = Files.list(VERIFY_DIR)) {
      return stream.filter(path -> path.getFileName().toString().endsWith(".sh")).toList();
    }
  }
}
