package dev.ayagmar.quarkusforge.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeZipExtractorTest {
  @TempDir Path tempDir;

  @Test
  void extractsZipUsingSingleRootDirectory() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("project.zip"),
            Map.of(
                "demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8),
                "demo/src/main/java/App.java", "class App {}".getBytes(StandardCharsets.UTF_8)));

    SafeZipExtractor extractor = new SafeZipExtractor();
    Path destination = tempDir.resolve("generated-project");

    ExtractionResult result =
        extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS);

    assertThat(result.extractedRoot()).isEqualTo(destination);
    assertThat(Files.readString(destination.resolve("pom.xml"))).isEqualTo("<project/>");
    assertThat(Files.readString(destination.resolve("src/main/java/App.java")))
        .isEqualTo("class App {}");
  }

  @Test
  void keepsOutputDirectoryForSingleTopLevelFileArchive() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("single-file-root.zip"),
            Map.of("pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8)));

    SafeZipExtractor extractor = new SafeZipExtractor();
    Path destination = tempDir.resolve("generated-project");

    ExtractionResult result =
        extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS);

    assertThat(result.extractedRoot()).isEqualTo(destination);
    assertThat(Files.isDirectory(destination)).isTrue();
    assertThat(Files.readString(destination.resolve("pom.xml"))).isEqualTo("<project/>");
  }

  @Test
  void acceptsBackslashEntryNamesFromTheCentralDirectory() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("backslash.zip"),
            Map.of(
                "demo\\pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8),
                "demo/src/main/java/App.java", "class App {}".getBytes(StandardCharsets.UTF_8)));

    SafeZipExtractor extractor = new SafeZipExtractor();
    Path destination = tempDir.resolve("generated-project");

    ExtractionResult result =
        extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS);

    assertThat(result.extractedRoot()).isEqualTo(destination);
    assertThat(Files.readString(destination.resolve("pom.xml"))).isEqualTo("<project/>");
    assertThat(Files.readString(destination.resolve("src/main/java/App.java")))
        .isEqualTo("class App {}");
  }

  @Test
  void rejectsZipSlipTraversalEntries() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("traversal.zip"),
            Map.of(
                "../evil.txt", "evil".getBytes(StandardCharsets.UTF_8),
                "demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8)));

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("parent traversal");
  }

  @Test
  void rejectsSymlinkEntriesFromUnixModeMetadata() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("symlink.zip"),
            Map.of(
                "demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8),
                "demo/link", "pom.xml".getBytes(StandardCharsets.UTF_8)));
    ArchiveTestUtils.patchUnixMode(zipPath, "demo/link", 0120777);

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("symlink");
  }

  @Test
  void rejectsSuspiciousUnixModeBits() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("mode.zip"),
            Map.of("demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8)));
    ArchiveTestUtils.patchUnixMode(zipPath, "demo/pom.xml", 0104755);

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("suspicious unix mode");
  }

  @Test
  void rejectsHighCompressionRatioZipBomb() throws IOException {
    byte[] payload = new byte[2 * 1024 * 1024];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = 'A';
    }
    Path zipPath =
        ArchiveTestUtils.createZip(tempDir.resolve("bomb.zip"), Map.of("demo/big.txt", payload));

    SafeZipExtractor extractor =
        new SafeZipExtractor(new ArchiveSafetyPolicy(1_000, 10L * 1024L * 1024L, 5.0d, 1_024L));

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("compression ratio");
  }

  @Test
  void rejectsEntryThatExceedsDeclaredUncompressedSizeDuringStreaming() throws IOException {
    byte[] payload = new byte[8 * 1024 * 1024];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = 'A';
    }
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("size-mismatch.zip"), Map.of("demo/big.txt", payload));
    ArchiveTestUtils.patchUncompressedSize(zipPath, "demo/big.txt", 1L);

    SafeZipExtractor extractor =
        new SafeZipExtractor(new ArchiveSafetyPolicy(1_000, 32L * 1024L * 1024L, 500.0d, 1L));

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("exceeds declared uncompressed size");
  }

  @Test
  void rejectsEntryThatExceedsDeclaredZeroUncompressedSizeDuringStreaming() throws IOException {
    byte[] payload = new byte[8 * 1024];
    for (int i = 0; i < payload.length; i++) {
      payload[i] = 'A';
    }
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("zero-size-mismatch.zip"), Map.of("demo/big.txt", payload));
    ArchiveTestUtils.patchUncompressedSize(zipPath, "demo/big.txt", 0L);

    SafeZipExtractor extractor =
        new SafeZipExtractor(new ArchiveSafetyPolicy(1_000, 32L * 1024L * 1024L, 500.0d, 1L));

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("exceeds declared uncompressed size");
  }

  @Test
  void rejectsEntriesThatCollideAfterNameNormalization() throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("demo\\pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8));
    entries.put("demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8));
    Path zipPath = ArchiveTestUtils.createZip(tempDir.resolve("normalized-collision.zip"), entries);

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("after normalization");
  }

  @Test
  void rejectsZipWhenEntryCountExceedsPolicy() throws IOException {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("demo/a.txt", "a".getBytes(StandardCharsets.UTF_8));
    entries.put("demo/b.txt", "b".getBytes(StandardCharsets.UTF_8));
    entries.put("demo/c.txt", "c".getBytes(StandardCharsets.UTF_8));
    Path zipPath = ArchiveTestUtils.createZip(tempDir.resolve("many.zip"), entries);

    SafeZipExtractor extractor =
        new SafeZipExtractor(new ArchiveSafetyPolicy(2, 1024L * 1024L, 20.0d, 1L));

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("entry count");
  }

  @Test
  void failIfExistsPolicyRefusesToOverwriteTargetDirectory() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("replace.zip"),
            Map.of("demo/new.txt", "new".getBytes(StandardCharsets.UTF_8)));
    Path destination = tempDir.resolve("generated-project");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("old.txt"), "old");

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(
            () -> extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasRootCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class)
        .hasRootCauseMessage("Output directory already exists: " + destination);
    assertThat(Files.readString(destination.resolve("old.txt"))).isEqualTo("old");
  }

  @Test
  void failIfExistsOnRootOutputPathDoesNotCrashOnNullFileName() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("root-output.zip"),
            Map.of("demo/new.txt", "new".getBytes(StandardCharsets.UTF_8)));
    Path rootOutput = tempDir.getRoot();

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(() -> extractor.extract(zipPath, rootOutput, OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasRootCauseInstanceOf(java.nio.file.FileAlreadyExistsException.class);
  }

  @Test
  void replaceExistingPolicyReplacesTargetDirectory() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("replace-ok.zip"),
            Map.of("demo/new.txt", "new".getBytes(StandardCharsets.UTF_8)));
    Path destination = tempDir.resolve("generated-project");
    Files.createDirectories(destination);
    Files.writeString(destination.resolve("old.txt"), "old");

    SafeZipExtractor extractor = new SafeZipExtractor();
    extractor.extract(zipPath, destination, OverwritePolicy.REPLACE_EXISTING);

    assertThat(Files.exists(destination.resolve("old.txt"))).isFalse();
    assertThat(Files.readString(destination.resolve("new.txt"))).isEqualTo("new");
  }

  @Test
  void cleanupRemovesStagingDirectoryOnExtractionFailure() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("cleanup.zip"),
            Map.of(
                "../evil.txt", "evil".getBytes(StandardCharsets.UTF_8),
                "demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8)));

    SafeZipExtractor extractor = new SafeZipExtractor();
    Path destination = tempDir.resolve("generated-project");

    assertThatThrownBy(
            () -> extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class);

    assertThat(Files.exists(destination)).isFalse();
    try (var stream = Files.list(tempDir)) {
      assertThat(stream.anyMatch(path -> path.getFileName().toString().contains("-extract-")))
          .isFalse();
    }
  }

  @Test
  void rejectsCorruptZipArchive() throws IOException {
    Path zipPath = tempDir.resolve("corrupt.zip");
    Files.writeString(zipPath, "not-a-zip");

    SafeZipExtractor extractor = new SafeZipExtractor();

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath, tempDir.resolve("generated-project"), OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("Invalid ZIP");
  }
}
