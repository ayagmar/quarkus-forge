package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
  @TempDir Path tempDir;

  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  @Test
  void writeBytesPersistsPayload() throws Exception {
    Path target = tempDir.resolve("prefs.json");

    AtomicFileStore.writeBytes(target, "{\"ok\":true}".getBytes(), "atomic-test-");

    assertThat(Files.readString(target)).isEqualTo("{\"ok\":true}");
  }

  @Test
  void writeBytesFallsBackWhenAtomicMoveIsNotSupported() throws Exception {
    Path target = tempDir.resolve("catalog.json");
    AtomicInteger moveCalls = new AtomicInteger();
    FileMoveOperation mover =
        (source, destination, options) -> {
          moveCalls.incrementAndGet();
          if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
            throw new AtomicMoveNotSupportedException(
                source.toString(), destination.toString(), "");
          }
          Files.move(source, destination, options);
        };

    AtomicFileStore.writeBytes(target, "{\"fallback\":true}".getBytes(), "atomic-test-", mover);

    assertThat(Files.readString(target)).isEqualTo("{\"fallback\":true}");
    assertThat(moveCalls.get()).isEqualTo(2);
  }

  @Test
  void writeBytesHardensManagedAppDataFilesOnPosixFileSystems() throws Exception {
    systemProperties.set("user.home", tempDir);
    Path target = ForgeDataPaths.preferencesFile();

    AtomicFileStore.writeBytes(target, "{\"ok\":true}".getBytes(), "atomic-test-");

    Assumptions.assumeTrue(Files.getFileStore(target).supportsFileAttributeView("posix"));
    assertThat(Files.getPosixFilePermissions(target))
        .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    assertThat(Files.getPosixFilePermissions(target.getParent()))
        .containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
  }

  @Test
  void writeBytesKeepsDefaultPermissionsForUnmanagedPosixFiles() throws Exception {
    Path targetDirectory = tempDir.resolve("workspace");
    Files.createDirectories(targetDirectory);
    Path permissionProbe = Files.createFile(targetDirectory.resolve("permission-probe.json"));

    Assumptions.assumeTrue(Files.getFileStore(permissionProbe).supportsFileAttributeView("posix"));
    Set<PosixFilePermission> defaultPermissions = Files.getPosixFilePermissions(permissionProbe);
    Assumptions.assumeFalse(
        defaultPermissions.equals(
            Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
    Files.delete(permissionProbe);

    Path target = targetDirectory.resolve("prefs.json");
    AtomicFileStore.writeBytes(target, "{\"ok\":true}".getBytes(), "atomic-test-");

    assertThat(Files.getPosixFilePermissions(target)).isEqualTo(defaultPermissions);
  }
}
