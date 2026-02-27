package dev.ayagmar.quarkusforge.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SafeZipExtractor {
  private static final int COPY_BUFFER_SIZE = 8 * 1024;
  private final ArchiveSafetyPolicy safetyPolicy;

  public SafeZipExtractor() {
    this(ArchiveSafetyPolicy.defaults());
  }

  public SafeZipExtractor(ArchiveSafetyPolicy safetyPolicy) {
    this.safetyPolicy = Objects.requireNonNull(safetyPolicy);
  }

  public ExtractionResult extract(
      Path zipFile, Path outputDirectory, OverwritePolicy overwritePolicy) {
    Objects.requireNonNull(zipFile);
    Objects.requireNonNull(outputDirectory);
    Objects.requireNonNull(overwritePolicy);

    Map<String, ZipEntryMetadata> metadataByEntry = ZipCentralDirectoryReader.read(zipFile);
    validateEntryMetadata(metadataByEntry);

    Path absoluteTarget = outputDirectory.toAbsolutePath().normalize();
    Path parent = absoluteTarget.getParent();
    if (parent == null) {
      parent = Paths.get(".").toAbsolutePath().normalize();
    }

    try {
      Files.createDirectories(parent);
    } catch (IOException ioException) {
      throw new ArchiveException(
          "Failed to create output parent directory: " + parent, ioException);
    }

    String targetName =
        absoluteTarget.getFileName() == null
            ? "quarkus-forge"
            : absoluteTarget.getFileName().toString();
    Path stagingRoot = createTempDirectory(parent, targetName + "-extract-");
    Path stagingContent = stagingRoot.resolve("content");
    try {
      Files.createDirectories(stagingContent);
      ExtractionResult stagedResult = extractIntoStaging(zipFile, stagingContent, metadataByEntry);
      Path extractedRoot = stagedResult.extractedRoot();
      applyOverwritePolicy(extractedRoot, absoluteTarget, overwritePolicy);
      return new ExtractionResult(
          absoluteTarget, stagedResult.entryCount(), stagedResult.extractedBytes());
    } catch (IOException ioException) {
      String causeMessage = ioException.getMessage();
      String detail =
          (causeMessage == null || causeMessage.isBlank()) ? "" : ": " + causeMessage.strip();
      throw new ArchiveException(
          "Failed to extract ZIP archive " + zipFile + " to " + absoluteTarget + detail,
          ioException);
    } finally {
      deleteRecursivelyQuietly(stagingRoot);
    }
  }

  private void validateEntryMetadata(Map<String, ZipEntryMetadata> metadataByEntry) {
    if (metadataByEntry.size() > safetyPolicy.maxEntries()) {
      throw new ArchiveException(
          "ZIP entry count "
              + metadataByEntry.size()
              + " exceeds configured maximum "
              + safetyPolicy.maxEntries());
    }

    long totalUncompressedBytes = 0L;
    for (ZipEntryMetadata entry : metadataByEntry.values()) {
      validateEntryName(entry.name());

      if (entry.isSymbolicLink()) {
        throw new ArchiveException("ZIP contains symlink entry '" + entry.name() + "'");
      }
      if (entry.hasSuspiciousUnixMode()) {
        throw new ArchiveException(
            "ZIP contains suspicious unix mode metadata for entry '" + entry.name() + "'");
      }

      totalUncompressedBytes = safeAdd(totalUncompressedBytes, entry.uncompressedSize());
      if (totalUncompressedBytes > safetyPolicy.maxTotalUncompressedBytes()) {
        throw new ArchiveException(
            "ZIP total uncompressed size "
                + totalUncompressedBytes
                + " exceeds configured maximum "
                + safetyPolicy.maxTotalUncompressedBytes());
      }

      if (entry.uncompressedSize() >= safetyPolicy.minBytesForCompressionRatioCheck()) {
        long compressed = entry.compressedSize();
        if (compressed == 0L) {
          throw new ArchiveException(
              "ZIP contains suspicious compression ratio for entry '" + entry.name() + "'");
        }

        double ratio = ((double) entry.uncompressedSize()) / compressed;
        if (ratio > safetyPolicy.maxCompressionRatio()) {
          throw new ArchiveException(
              "ZIP contains suspicious compression ratio for entry '" + entry.name() + "'");
        }
      }
    }
  }

  private ExtractionResult extractIntoStaging(
      Path zipFile, Path stagingContent, Map<String, ZipEntryMetadata> metadataByEntry)
      throws IOException {
    Set<String> seenEntries = new LinkedHashSet<>();
    Set<String> topLevelSegments = new LinkedHashSet<>();
    long extractedBytes = 0L;
    int extractedEntryCount = 0;

    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry zipEntry;
      while ((zipEntry = zipInputStream.getNextEntry()) != null) {
        String normalizedName = normalizeEntryName(zipEntry.getName());
        ZipEntryMetadata metadata = metadataByEntry.get(normalizedName);
        if (metadata == null) {
          throw new ArchiveException(
              "ZIP entry '" + normalizedName + "' missing in central directory");
        }
        if (!seenEntries.add(normalizedName)) {
          throw new ArchiveException("ZIP entry repeated in payload: " + normalizedName);
        }

        Path relativePath = toRelativePath(normalizedName);
        if (relativePath.getNameCount() > 0) {
          topLevelSegments.add(relativePath.getName(0).toString());
        }

        Path destination = stagingContent.resolve(relativePath).normalize();
        if (!destination.startsWith(stagingContent)) {
          throw new ArchiveException("ZIP entry escapes extraction root: " + normalizedName);
        }

        if (zipEntry.isDirectory()) {
          Files.createDirectories(destination);
          extractedEntryCount++;
          continue;
        }

        Path parent = destination.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }

        CopyResult copyResult =
            copyEntry(
                zipInputStream,
                destination,
                normalizedName,
                metadata.uncompressedSize(),
                extractedBytes);
        long copied = copyResult.entryBytes();
        extractedBytes = copyResult.totalExtractedBytes();

        if (copied != metadata.uncompressedSize()) {
          throw new ArchiveException(
              "ZIP entry size mismatch for '"
                  + normalizedName
                  + "', expected "
                  + metadata.uncompressedSize()
                  + " bytes but read "
                  + copied);
        }
        extractedEntryCount++;
      }
    }

    if (seenEntries.size() != metadataByEntry.size()) {
      List<String> missingEntries = new ArrayList<>();
      for (String expectedEntry : metadataByEntry.keySet()) {
        if (!seenEntries.contains(expectedEntry)) {
          missingEntries.add(expectedEntry);
        }
      }
      throw new ArchiveException(
          "ZIP payload missing entries from central directory: " + missingEntries);
    }

    Path extractedRoot = stagingContent;
    if (topLevelSegments.size() == 1) {
      Path singleRoot = stagingContent.resolve(topLevelSegments.iterator().next());
      if (Files.isDirectory(singleRoot)) {
        extractedRoot = singleRoot;
      }
    }
    return new ExtractionResult(extractedRoot, extractedEntryCount, extractedBytes);
  }

  private void applyOverwritePolicy(
      Path extractedRoot, Path outputDirectory, OverwritePolicy overwritePolicy)
      throws IOException {
    Path backup = null;
    if (Files.exists(outputDirectory)) {
      if (overwritePolicy == OverwritePolicy.FAIL_IF_EXISTS) {
        throw new FileAlreadyExistsException("Output directory already exists: " + outputDirectory);
      }
      backup =
          outputDirectory.resolveSibling(
              outputDirectory.getFileName() + ".backup-" + UUID.randomUUID());
      moveWithAtomicFallback(outputDirectory, backup);
    }

    try {
      moveWithAtomicFallback(extractedRoot, outputDirectory);
      if (backup != null) {
        deleteRecursivelyQuietly(backup);
      }
    } catch (IOException ioException) {
      deleteRecursivelyQuietly(outputDirectory);
      if (backup != null && Files.exists(backup)) {
        moveWithAtomicFallback(backup, outputDirectory);
      }
      throw ioException;
    }
  }

  private static void moveWithAtomicFallback(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignored) {
      Files.move(source, target);
    }
  }

  private CopyResult copyEntry(
      ZipInputStream inputStream,
      Path destination,
      String entryName,
      long expectedUncompressedSize,
      long extractedBytesSoFar)
      throws IOException {
    long copied = 0L;
    long totalExtractedBytes = extractedBytesSoFar;
    byte[] buffer = new byte[COPY_BUFFER_SIZE];
    try (OutputStream outputStream =
        Files.newOutputStream(
            destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        long nextCopied = safeAdd(copied, read);
        if (nextCopied > expectedUncompressedSize) {
          throw new ArchiveException(
              "ZIP entry '"
                  + entryName
                  + "' exceeds declared uncompressed size "
                  + expectedUncompressedSize
                  + " bytes");
        }

        long nextTotalExtractedBytes = safeAdd(totalExtractedBytes, read);
        if (nextTotalExtractedBytes > safetyPolicy.maxTotalUncompressedBytes()) {
          throw new ArchiveException(
              "ZIP extracted bytes exceed configured maximum "
                  + safetyPolicy.maxTotalUncompressedBytes());
        }

        outputStream.write(buffer, 0, read);
        copied = nextCopied;
        totalExtractedBytes = nextTotalExtractedBytes;
      }
    }
    return new CopyResult(copied, totalExtractedBytes);
  }

  private record CopyResult(long entryBytes, long totalExtractedBytes) {}

  static String normalizeEntryName(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      throw new ArchiveException("ZIP entry name must not be blank");
    }
    String normalized = rawName.replace('\\', '/');
    while (normalized.startsWith("./")) {
      normalized = normalized.substring(2);
    }
    return normalized;
  }

  private static Path toRelativePath(String normalizedName) {
    validateEntryName(normalizedName);
    Path relative = Paths.get(normalizedName).normalize();
    if (relative.isAbsolute()) {
      throw new ArchiveException("ZIP entry uses absolute path: " + normalizedName);
    }
    if (relative.startsWith("..")) {
      throw new ArchiveException("ZIP entry uses parent traversal: " + normalizedName);
    }
    return relative;
  }

  private static void validateEntryName(String name) {
    if (name.indexOf('\0') >= 0) {
      throw new ArchiveException("ZIP entry contains NUL byte");
    }
    String normalized = normalizeEntryName(name);
    if (normalized.startsWith("/")) {
      throw new ArchiveException("ZIP entry uses absolute path: " + name);
    }
    if (normalized.matches("^[A-Za-z]:.*")) {
      throw new ArchiveException("ZIP entry uses absolute drive path: " + name);
    }
  }

  private static long safeAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException arithmeticException) {
      throw new ArchiveException("ZIP size overflow detected");
    }
  }

  private static Path createTempDirectory(Path parent, String prefix) {
    try {
      return Files.createTempDirectory(parent, prefix);
    } catch (IOException ioException) {
      throw new ArchiveException("Failed to create temporary extraction directory", ioException);
    }
  }

  static void deleteRecursivelyQuietly(Path path) {
    if (path == null || Files.notExists(path)) {
      return;
    }
    try {
      Files.walkFileTree(
          path,
          new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              Files.deleteIfExists(file);
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException {
              if (exc != null) {
                throw exc;
              }
              Files.deleteIfExists(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException ignored) {
      // Best-effort cleanup path.
    }
  }

  public record ExtractionResult(Path extractedRoot, int entryCount, long extractedBytes) {}
}
