package dev.ayagmar.quarkusforge.api;

import dev.ayagmar.quarkusforge.util.FilePermissionSupport;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public final class AtomicFileStore {
  private static final FileMoveOperation FILE_MOVE_OPERATION = Files::move;

  private AtomicFileStore() {}

  public static void writeBytes(Path targetFile, byte[] payload, String tempFilePrefix)
      throws IOException {
    writeBytes(targetFile, payload, tempFilePrefix, FILE_MOVE_OPERATION);
  }

  static void writeBytes(
      Path targetFile, byte[] payload, String tempFilePrefix, FileMoveOperation moveOperation)
      throws IOException {
    Objects.requireNonNull(targetFile);
    Objects.requireNonNull(payload);
    Objects.requireNonNull(tempFilePrefix);
    Objects.requireNonNull(moveOperation);

    Path normalizedTarget = targetFile.toAbsolutePath().normalize();
    Path parent = resolvedParentDirectory(normalizedTarget);
    boolean managedTarget = ForgeDataPaths.isManagedPath(normalizedTarget);
    ForgeDataPaths.ensureManagedDirectoryHierarchy(parent);
    Path tempFile =
        managedTarget
            ? FilePermissionSupport.createOwnerOnlyTempFile(parent, tempFilePrefix, ".tmp")
            : FilePermissionSupport.createDefaultTempFile(parent, tempFilePrefix, ".tmp");
    try {
      Files.write(
          tempFile, payload, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
      moveAtomicallyWithFallback(tempFile, normalizedTarget, moveOperation);
      if (managedTarget) {
        FilePermissionSupport.ensureOwnerOnlyFile(normalizedTarget);
      }
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  private static void moveAtomicallyWithFallback(
      Path source, Path target, FileMoveOperation moveOperation) throws IOException {
    try {
      moveOperation.move(
          source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException unsupportedException) {
      moveOperation.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path resolvedParentDirectory(Path targetFile) throws IOException {
    Path parent = targetFile.getParent();
    if (parent == null) {
      throw new IOException("Path has no parent directory: " + targetFile);
    }
    return parent;
  }
}
