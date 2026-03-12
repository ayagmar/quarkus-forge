package dev.ayagmar.quarkusforge.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePermissionSupportTest {
  @TempDir Path tempDir;

  @Test
  void createOwnerOnlyTempFileCreatesFile() throws Exception {
    Path tempFile = FilePermissionSupport.createOwnerOnlyTempFile("forge-test-", ".tmp");

    assertThat(tempFile).exists().isRegularFile();
    assertOwnerOnlyFilePermissions(tempFile);
  }

  @Test
  void createOwnerOnlyTempFileInDirectoryCreatesFile() throws Exception {
    Path tempFile = FilePermissionSupport.createOwnerOnlyTempFile(tempDir, "forge-test-", ".json");

    assertThat(tempFile).exists().isRegularFile();
    assertThat(tempFile.getParent()).isEqualTo(tempDir);
    assertOwnerOnlyFilePermissions(tempFile);
  }

  @Test
  void createOwnerOnlyTempDirectoryCreatesDirectory() throws Exception {
    Path tempDirectory = FilePermissionSupport.createOwnerOnlyTempDirectory(tempDir, "forge-dir-");

    assertThat(tempDirectory).exists().isDirectory();
    assertThat(tempDirectory.getParent()).isEqualTo(tempDir);
    assertOwnerOnlyDirectoryPermissions(tempDirectory);
  }

  @Test
  void ensureOwnerOnlyHelpersIgnoreNullAndMissingPaths() {
    assertThatCode(() -> FilePermissionSupport.ensureOwnerOnlyFile(null))
        .doesNotThrowAnyException();
    assertThatCode(() -> FilePermissionSupport.ensureOwnerOnlyDirectory(null))
        .doesNotThrowAnyException();
    assertThatCode(() -> FilePermissionSupport.ensureOwnerOnlyFile(tempDir.resolve("missing.txt")))
        .doesNotThrowAnyException();
    assertThatCode(() -> FilePermissionSupport.ensureOwnerOnlyDirectory(tempDir.resolve("missing")))
        .doesNotThrowAnyException();
  }

  @Test
  void ensureOwnerOnlyHelpersTightenExistingPathsOnPosixFileSystems() throws Exception {
    Path file = Files.writeString(tempDir.resolve("prefs.json"), "{}");
    Path directory = Files.createDirectory(tempDir.resolve("recipes"));

    FilePermissionSupport.ensureOwnerOnlyFile(file);
    FilePermissionSupport.ensureOwnerOnlyDirectory(directory);

    assertOwnerOnlyFilePermissions(file);
    assertOwnerOnlyDirectoryPermissions(directory);
  }

  private static void assertOwnerOnlyFilePermissions(Path file) throws Exception {
    Assumptions.assumeTrue(Files.getFileStore(file).supportsFileAttributeView("posix"));
    assertThat(Files.getPosixFilePermissions(file))
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  }

  private static void assertOwnerOnlyDirectoryPermissions(Path directory) throws Exception {
    Assumptions.assumeTrue(Files.getFileStore(directory).supportsFileAttributeView("posix"));
    assertThat(Files.getPosixFilePermissions(directory))
        .containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
  }
}
