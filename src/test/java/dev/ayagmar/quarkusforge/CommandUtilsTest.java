package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandUtilsTest {
  @TempDir Path tempDir;

  @Test
  void nullCommandIsNotAvailable() {
    assertThat(CommandUtils.isCommandAvailable(null, "/usr/bin")).isFalse();
  }

  @Test
  void blankCommandIsNotAvailable() {
    assertThat(CommandUtils.isCommandAvailable("   ", "/usr/bin")).isFalse();
  }

  @Test
  void nullPathValueIsNotAvailable() {
    assertThat(CommandUtils.isCommandAvailable("echo", null)).isFalse();
  }

  @Test
  void blankPathValueIsNotAvailable() {
    assertThat(CommandUtils.isCommandAvailable("echo", "   ")).isFalse();
  }

  @Test
  void commandFoundInPath() throws IOException {
    Path bin = tempDir.resolve("bin");
    Files.createDirectories(bin);
    Path executable = bin.resolve("mytool");
    Files.createFile(executable);
    executable.toFile().setExecutable(true);

    assertThat(CommandUtils.isCommandAvailable("mytool", bin.toString())).isTrue();
  }

  @Test
  void commandNotFoundInPath() {
    assertThat(CommandUtils.isCommandAvailable("nonexistent-tool-xyz", tempDir.toString()))
        .isFalse();
  }

  @Test
  void emptyPathEntryIsSkipped() throws IOException {
    Path bin = tempDir.resolve("bin");
    Files.createDirectories(bin);
    Path executable = bin.resolve("tool");
    Files.createFile(executable);
    executable.toFile().setExecutable(true);

    // Empty segment between separators is safely skipped
    String pathValue = ":" + bin.toString() + ":";
    assertThat(CommandUtils.isCommandAvailable("tool", pathValue)).isTrue();
  }

  @Test
  void invalidPathEntryIsSkipped() throws IOException {
    Path bin = tempDir.resolve("bin");
    Files.createDirectories(bin);
    Path executable = bin.resolve("valid-tool");
    Files.createFile(executable);
    executable.toFile().setExecutable(true);

    // Invalid path entry (null bytes) followed by valid entry
    String pathValue = "/invalid\0path:" + bin.toString();
    assertThat(CommandUtils.isCommandAvailable("valid-tool", pathValue)).isTrue();
  }

  @Test
  void nonExecutableFileNotFound() throws IOException {
    Path bin = tempDir.resolve("bin");
    Files.createDirectories(bin);
    Path file = bin.resolve("notexec");
    Files.createFile(file);
    file.toFile().setExecutable(false);

    assertThat(CommandUtils.isCommandAvailable("notexec", bin.toString())).isFalse();
  }

  @Test
  void multiplePathEntriesScanLeft() throws IOException {
    Path bin1 = tempDir.resolve("bin1");
    Path bin2 = tempDir.resolve("bin2");
    Files.createDirectories(bin1);
    Files.createDirectories(bin2);
    Path executable = bin2.resolve("deep-tool");
    Files.createFile(executable);
    executable.toFile().setExecutable(true);

    String pathValue = bin1.toString() + ":" + bin2.toString();
    assertThat(CommandUtils.isCommandAvailable("deep-tool", pathValue)).isTrue();
  }

  @Test
  void commandWithLeadingTrailingSpacesIsStripped() throws IOException {
    Path bin = tempDir.resolve("bin");
    Files.createDirectories(bin);
    Path executable = bin.resolve("spaced-tool");
    Files.createFile(executable);
    executable.toFile().setExecutable(true);

    assertThat(CommandUtils.isCommandAvailable("  spaced-tool  ", bin.toString())).isTrue();
  }

  @Test
  void directoryInPathIsNotConsideredExecutable() throws IOException {
    Path bin = tempDir.resolve("bin");
    Files.createDirectories(bin);
    // Create a directory with the tool name — should NOT match
    Files.createDirectories(bin.resolve("dir-tool"));

    assertThat(CommandUtils.isCommandAvailable("dir-tool", bin.toString())).isFalse();
  }

  @Test
  void noArgIsCommandAvailableChecksSystemPath() {
    // On any system, there should be some executables in PATH
    // But an intentionally missing command should return false
    assertThat(CommandUtils.isCommandAvailable("nonexistent-binary-" + System.nanoTime()))
        .isFalse();
  }
}
