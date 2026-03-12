package dev.ayagmar.quarkusforge.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public final class FilePermissionSupport {
  private static final Set<PosixFilePermission> OWNER_ONLY_FILE_PERMISSIONS =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
  private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);

  private FilePermissionSupport() {}

  public static Path createOwnerOnlyTempFile(String prefix, String suffix) throws IOException {
    Path tempFile;
    try {
      tempFile = Files.createTempFile(prefix, suffix, ownerOnlyFileAttribute());
    } catch (UnsupportedOperationException ignored) {
      tempFile = Files.createTempFile(prefix, suffix);
    }
    ensureOwnerOnlyFile(tempFile);
    return tempFile;
  }

  public static Path createOwnerOnlyTempFile(Path directory, String prefix, String suffix)
      throws IOException {
    Path tempFile;
    try {
      tempFile = Files.createTempFile(directory, prefix, suffix, ownerOnlyFileAttribute());
    } catch (UnsupportedOperationException ignored) {
      tempFile = Files.createTempFile(directory, prefix, suffix);
    }
    ensureOwnerOnlyFile(tempFile);
    return tempFile;
  }

  public static Path createOwnerOnlyTempDirectory(Path directory, String prefix)
      throws IOException {
    Path tempDirectory;
    try {
      tempDirectory = Files.createTempDirectory(directory, prefix, ownerOnlyDirectoryAttribute());
    } catch (UnsupportedOperationException ignored) {
      tempDirectory = Files.createTempDirectory(directory, prefix);
    }
    ensureOwnerOnlyDirectory(tempDirectory);
    return tempDirectory;
  }

  public static void ensureOwnerOnlyFile(Path file) {
    setPosixPermissions(file, OWNER_ONLY_FILE_PERMISSIONS);
  }

  public static void ensureOwnerOnlyDirectory(Path directory) {
    setPosixPermissions(directory, OWNER_ONLY_DIRECTORY_PERMISSIONS);
  }

  private static void setPosixPermissions(Path path, Set<PosixFilePermission> permissions) {
    if (path == null) {
      return;
    }
    try {
      if (Files.exists(path) && Files.getFileStore(path).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(path, permissions);
      }
    } catch (IOException | UnsupportedOperationException ignored) {
      // Best-effort hardening only.
    }
  }

  private static FileAttribute<Set<PosixFilePermission>> ownerOnlyFileAttribute() {
    return PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE_PERMISSIONS);
  }

  private static FileAttribute<Set<PosixFilePermission>> ownerOnlyDirectoryAttribute() {
    return PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS);
  }
}
