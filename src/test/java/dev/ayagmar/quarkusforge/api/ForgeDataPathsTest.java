package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.SystemPropertyExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

class ForgeDataPathsTest {
  @TempDir Path tempDir;

  @RegisterExtension final SystemPropertyExtension systemProperties = new SystemPropertyExtension();

  @Test
  void resolvesManagedPathsUnderUserHome() {
    systemProperties.set("user.home", tempDir);

    assertThat(ForgeDataPaths.appDataRoot()).isEqualTo(tempDir.resolve(".quarkus-forge"));
    assertThat(ForgeDataPaths.catalogSnapshotFile())
        .isEqualTo(tempDir.resolve(".quarkus-forge/catalog-snapshot.json"));
    assertThat(ForgeDataPaths.preferencesFile())
        .isEqualTo(tempDir.resolve(".quarkus-forge/preferences.json"));
    assertThat(ForgeDataPaths.favoritesFile())
        .isEqualTo(tempDir.resolve(".quarkus-forge/favorites.json"));
    assertThat(ForgeDataPaths.recipesRoot()).isEqualTo(tempDir.resolve(".quarkus-forge/recipes"));
  }

  @Test
  void detectsManagedPaths() {
    systemProperties.set("user.home", tempDir);

    assertThat(ForgeDataPaths.isManagedPath(null)).isFalse();
    assertThat(ForgeDataPaths.isManagedPath(ForgeDataPaths.preferencesFile())).isTrue();
    assertThat(ForgeDataPaths.isManagedPath(tempDir.resolve("elsewhere/preferences.json")))
        .isFalse();
  }

  @Test
  void ensureManagedDirectoryHierarchyCreatesManagedTree() throws Exception {
    systemProperties.set("user.home", tempDir);
    Path managedDirectory = ForgeDataPaths.recipesRoot().resolve("team-a");

    ForgeDataPaths.ensureManagedDirectoryHierarchy(managedDirectory);

    assertThat(managedDirectory).exists().isDirectory();
    assertOwnerOnlyDirectoryPermissions(ForgeDataPaths.appDataRoot());
    assertOwnerOnlyDirectoryPermissions(ForgeDataPaths.recipesRoot());
    assertOwnerOnlyDirectoryPermissions(managedDirectory);
  }

  @Test
  void ensureManagedDirectoryHierarchyCreatesUnmanagedDirectoriesWithoutRewritingRoot()
      throws Exception {
    systemProperties.set("user.home", tempDir);
    Path unmanagedDirectory = tempDir.resolve("workspace/output/cache");

    ForgeDataPaths.ensureManagedDirectoryHierarchy(unmanagedDirectory);

    assertThat(unmanagedDirectory).exists().isDirectory();
    assertThat(ForgeDataPaths.appDataRoot()).doesNotExist();
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
