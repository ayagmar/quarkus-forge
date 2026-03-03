package dev.ayagmar.quarkusforge.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZipCentralDirectoryReaderTest {
  @TempDir Path tempDir;

  @Test
  void readValidZipReturnsSortedEntries() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("valid.zip"),
            Map.of(
                "demo/pom.xml", "<project/>".getBytes(StandardCharsets.UTF_8),
                "demo/src/App.java", "class App {}".getBytes(StandardCharsets.UTF_8)));

    Map<String, ZipEntryMetadata> entries = ZipCentralDirectoryReader.read(zipPath);

    assertThat(entries).containsKey("demo/pom.xml").containsKey("demo/src/App.java");
    assertThat(entries.get("demo/pom.xml").uncompressedSize()).isEqualTo(10);
  }

  @Test
  void rejectsFileTooSmall() throws IOException {
    Path tiny = tempDir.resolve("tiny.zip");
    Files.write(tiny, new byte[10]); // Less than EOCD_MIN_LENGTH (22)

    assertThatThrownBy(() -> ZipCentralDirectoryReader.read(tiny))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("file is too small");
  }

  @Test
  void rejectsFileWithNoEocdSignature() throws IOException {
    Path noEocd = tempDir.resolve("no-eocd.zip");
    // Write 50 bytes of zeros - no valid EOCD signature
    Files.write(noEocd, new byte[50]);

    assertThatThrownBy(() -> ZipCentralDirectoryReader.read(noEocd))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("end-of-central-directory record not found");
  }

  @Test
  void rejectsCentralDirectoryPointingOutsideArchive() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("bad-offset.zip"),
            Map.of("demo/file.txt", "hello".getBytes(StandardCharsets.UTF_8)));

    // Patch the CD offset to point beyond the file size
    ArchiveTestUtils.patchEocdCentralDirectoryOffset(zipPath, 999_999L);

    assertThatThrownBy(() -> ZipCentralDirectoryReader.read(zipPath))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("central directory points outside archive");
  }

  @Test
  void rejectsMalformedCentralDirectoryHeader() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("malformed-cd.zip"),
            Map.of("demo/test.txt", "data".getBytes(StandardCharsets.UTF_8)));

    // Corrupt the central directory header signature
    ArchiveTestUtils.corruptRandomCentralDirectoryHeader(zipPath, 42L);

    assertThatThrownBy(() -> ZipCentralDirectoryReader.read(zipPath))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("malformed central directory header");
  }

  @Test
  void rejectsDuplicateEntryAfterNormalization() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("dup.zip"),
            Map.of("demo/file.txt", "original".getBytes(StandardCharsets.UTF_8)));

    // Duplicate the entry in the central directory
    ArchiveTestUtils.duplicateCentralDirectoryEntry(zipPath, "demo/file.txt");

    assertThatThrownBy(() -> ZipCentralDirectoryReader.read(zipPath))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("Duplicate ZIP entry found after normalization");
  }

  @Test
  void readsEntryWithBackslashNormalized() throws IOException {
    // Create entry with backslash in central directory name
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("backslash.zip"),
            Map.of("demo/src/Main.java", "class Main {}".getBytes(StandardCharsets.UTF_8)));

    // Patch entry name byte: replace '/' with '\' in "demo/src/Main.java"
    ArchiveTestUtils.patchCentralDirectoryEntryNameByte(
        zipPath, "demo/src/Main.java", 4, (byte) '\\');
    // Also patch the local file header to match
    ArchiveTestUtils.patchFirstLocalFileHeaderNameByte(zipPath, 4, (byte) '\\');

    Map<String, ZipEntryMetadata> entries = ZipCentralDirectoryReader.read(zipPath);

    // Backslash should be normalized to forward slash
    assertThat(entries).containsKey("demo/src/Main.java");
  }

  @Test
  void readsMultipleEntriesPreservingMetadata() throws IOException {
    byte[] content1 = "hello world".getBytes(StandardCharsets.UTF_8);
    byte[] content2 = "goodbye".getBytes(StandardCharsets.UTF_8);
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("multi.zip"), Map.of("a/one.txt", content1, "a/two.txt", content2));

    Map<String, ZipEntryMetadata> entries = ZipCentralDirectoryReader.read(zipPath);

    assertThat(entries).hasSize(2);
    assertThat(entries.get("a/one.txt").uncompressedSize()).isEqualTo(content1.length);
    assertThat(entries.get("a/two.txt").uncompressedSize()).isEqualTo(content2.length);
  }

  @Test
  void readNonExistentFileThrowsArchiveException() {
    Path missing = tempDir.resolve("missing.zip");

    assertThatThrownBy(() -> ZipCentralDirectoryReader.read(missing))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("Failed to read ZIP central directory");
  }

  @Test
  void readsEntryCountMatchingCentralDirectory() throws IOException {
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("count.zip"),
            Map.of(
                "root/a.txt", "a".getBytes(StandardCharsets.UTF_8),
                "root/b.txt", "b".getBytes(StandardCharsets.UTF_8),
                "root/c.txt", "c".getBytes(StandardCharsets.UTF_8)));

    Map<String, ZipEntryMetadata> entries = ZipCentralDirectoryReader.read(zipPath);

    assertThat(entries).hasSize(3);
  }
}
