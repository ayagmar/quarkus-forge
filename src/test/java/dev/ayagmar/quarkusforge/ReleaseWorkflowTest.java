package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseWorkflowTest {

  @Test
  void releaseWorkflowUsesSharedNativeSmokeEntrypoint() throws Exception {
    String workflow = normalizedText(Path.of(".github", "workflows", "release.yml"));

    assertThat(workflow)
        .contains(
            "scripts/verify/native-release-smoke.sh \"$binary\" \"${{ matrix.smoke_mode }}\"");
    assertContainsNativeMatrixEntry(
        workflow,
        "ubuntu-latest",
        "linux-x86_64",
        "''",
        "-Pnative",
        "quarkus-forge",
        "interactive-posix");
    assertContainsNativeMatrixEntry(
        workflow,
        "macos-latest",
        "macos-aarch64",
        "''",
        "-Pnative",
        "quarkus-forge",
        "interactive-posix");
    assertContainsNativeMatrixEntry(
        workflow,
        "ubuntu-latest",
        "linux-x86_64",
        "''",
        "-Pheadless,native",
        "quarkus-forge-headless",
        "headless");
    assertContainsNativeMatrixEntry(
        workflow,
        "macos-latest",
        "macos-aarch64",
        "''",
        "-Pheadless,native",
        "quarkus-forge-headless",
        "headless");
    assertContainsNativeMatrixEntry(
        workflow,
        "windows-latest",
        "windows-x86_64",
        "'.exe'",
        "-Pheadless,native",
        "quarkus-forge-headless",
        "headless");
    assertThat(workflow).contains("smoke_mode: interactive-posix");
    assertThat(workflow).contains("smoke_mode: interactive-windows");
    assertThat(workflow).contains("smoke_mode: headless");
    assertThat(workflow).doesNotContain("\"$binary\" --help > /dev/null");
  }

  @Test
  void releaseMetadataPublishesIndividualSha256Checksums() throws Exception {
    String jreleaser = normalizedText(Path.of("jreleaser.yml"));

    assertThat(jreleaser).contains("checksum:");
    assertThat(jreleaser).contains("  individual: true");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-jvm.jar");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-headless.jar");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-linux-x86_64");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-macos-aarch64");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-windows-x86_64.exe");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-headless-linux-x86_64");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-headless-macos-aarch64");
    assertThat(jreleaser).contains("- path: artifacts/quarkus-forge-headless-windows-x86_64.exe");
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

  private static String normalizedText(Path path) throws Exception {
    return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
  }
}
