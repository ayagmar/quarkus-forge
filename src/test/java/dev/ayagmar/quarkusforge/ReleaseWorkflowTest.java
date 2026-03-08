package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseWorkflowTest {

  @Test
  void releaseWorkflowUsesSharedNativeSmokeEntrypoint() throws Exception {
    String workflow =
        Files.readString(Path.of(".github", "workflows", "release.yml"), StandardCharsets.UTF_8);

    assertThat(workflow)
        .contains(
            "scripts/verify/native-release-smoke.sh \"$binary\" \"${{ matrix.smoke_mode }}\"");
    assertContainsNativeMatrixEntry(
        workflow, "ubuntu-latest", "linux-x86_64", "''", "-Pnative", "quarkus-forge",
        "interactive-posix");
    assertContainsNativeMatrixEntry(
        workflow, "macos-latest", "macos-aarch64", "''", "-Pnative", "quarkus-forge",
        "interactive-posix");
    assertThat(workflow).contains("smoke_mode: interactive-posix");
    assertThat(workflow).contains("smoke_mode: interactive-windows");
    assertThat(workflow).contains("smoke_mode: headless");
    assertThat(workflow).doesNotContain("\"$binary\" --help > /dev/null");
  }

  private static void assertContainsNativeMatrixEntry(
      String workflow,
      String os,
      String platform,
      String ext,
      String profiles,
      String binaryName,
      String smokeMode) {
    assertThat(workflow)
        .contains(
            String.join(
                "\n",
                "          - os: " + os,
                "            platform: " + platform,
                "            ext: " + ext,
                "            profiles: " + profiles,
                "            binary_name: " + binaryName,
                "            smoke_mode: " + smokeMode));
  }
}
