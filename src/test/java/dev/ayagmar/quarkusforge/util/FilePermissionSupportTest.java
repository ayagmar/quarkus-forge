package dev.ayagmar.quarkusforge.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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

  @Test
  void createOwnerOnlyPathFallsBackWhenAttributesAreUnsupported() throws Exception {
    Path fallbackPath = tempDir.resolve("fallback.json");
    AtomicBoolean hardenerCalled = new AtomicBoolean();

    Path createdPath =
        FilePermissionSupport.createOwnerOnlyPath(
            () -> {
              throw new UnsupportedOperationException("no posix attrs");
            },
            () -> Files.writeString(fallbackPath, "{}"),
            path -> hardenerCalled.set(true));

    assertThat(createdPath).isEqualTo(fallbackPath);
    assertThat(createdPath).exists();
    assertThat(hardenerCalled.get()).isTrue();
  }

  @Test
  void ensureOwnerOnlyHelpersSkipNonPosixFileSystems() throws Exception {
    Path archive = tempDir.resolve("non-posix.zip");
    URI zipUri = URI.create("jar:" + archive.toUri());

    try (FileSystem zipFileSystem = FileSystems.newFileSystem(zipUri, Map.of("create", "true"))) {
      Path zipFile = Files.writeString(zipFileSystem.getPath("/prefs.json"), "{}");
      Path zipDirectory = Files.createDirectory(zipFileSystem.getPath("/recipes"));

      assertThatCode(() -> FilePermissionSupport.ensureOwnerOnlyFile(zipFile))
          .doesNotThrowAnyException();
      assertThatCode(() -> FilePermissionSupport.ensureOwnerOnlyDirectory(zipDirectory))
          .doesNotThrowAnyException();
    }
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
