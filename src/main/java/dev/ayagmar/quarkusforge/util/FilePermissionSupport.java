package dev.ayagmar.quarkusforge.util;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

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
    return createOwnerOnlyPath(
        () -> Files.createTempFile(prefix, suffix, ownerOnlyFileAttribute()),
        () -> Files.createTempFile(prefix, suffix),
        FilePermissionSupport::ensureOwnerOnlyFile);
  }

  public static Path createOwnerOnlyTempFile(Path directory, String prefix, String suffix)
      throws IOException {
    return createOwnerOnlyPath(
        () -> Files.createTempFile(directory, prefix, suffix, ownerOnlyFileAttribute()),
        () -> Files.createTempFile(directory, prefix, suffix),
        FilePermissionSupport::ensureOwnerOnlyFile);
  }

  public static Path createOwnerOnlyTempDirectory(Path directory, String prefix)
      throws IOException {
    return createOwnerOnlyPath(
        () -> Files.createTempDirectory(directory, prefix, ownerOnlyDirectoryAttribute()),
        () -> Files.createTempDirectory(directory, prefix),
        FilePermissionSupport::ensureOwnerOnlyDirectory);
  }

  public static Path createDefaultTempFile(Path directory, String prefix, String suffix)
      throws IOException {
    for (int attempt = 0; attempt < 10; attempt++) {
      try {
        return Files.createFile(directory.resolve(prefix + UUID.randomUUID() + suffix));
      } catch (FileAlreadyExistsException ignored) {
        // Retry with a new random name.
      }
    }
    throw new IOException("Failed to create default temp file in " + directory);
  }

  public static void ensureOwnerOnlyFile(Path file) {
    setPosixPermissions(file, OWNER_ONLY_FILE_PERMISSIONS);
  }

  public static void ensureOwnerOnlyDirectory(Path directory) {
    setPosixPermissions(directory, OWNER_ONLY_DIRECTORY_PERMISSIONS);
  }

  static Path createOwnerOnlyPath(
      IoPathSupplier withAttributes, IoPathSupplier fallback, Consumer<Path> hardener)
      throws IOException {
    Path path;
    try {
      path = withAttributes.get();
    } catch (UnsupportedOperationException ignored) {
      path = fallback.get();
    }
    hardener.accept(path);
    return path;
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

  @FunctionalInterface
  interface IoPathSupplier {
    Path get() throws IOException;
  }
}
