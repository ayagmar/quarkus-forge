package dev.ayagmar.quarkusforge.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.io.TempDir;

class SafeZipExtractorPropertyTest {
  private static final long BASE_SEED = 0x5EED5AFECL;

  @TempDir Path tempDir;

  @RepeatedTest(24)
  void randomizedValidArchivesExtractCorrectly(RepetitionInfo repetitionInfo) throws IOException {
    SplittableRandom random = seededRandom(repetitionInfo, 1);
    Map<String, byte[]> entries = randomizedDemoEntries(random);
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("valid-" + repetitionInfo.getCurrentRepetition() + ".zip"), entries);

    SafeZipExtractor extractor = new SafeZipExtractor();
    Path destination = tempDir.resolve("output-valid-" + repetitionInfo.getCurrentRepetition());

    SafeZipExtractor.ExtractionResult result =
        extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS);

    assertThat(result.extractedRoot()).isEqualTo(destination);
    assertExtractedEntries(destination, entries, repetitionInfo, 1);
  }

  @RepeatedTest(16)
  void randomizedReorderedCentralDirectoryEntriesStillExtract(RepetitionInfo repetitionInfo)
      throws IOException {
    SplittableRandom random = seededRandom(repetitionInfo, 11);
    Map<String, byte[]> entries = randomizedDemoEntries(random);
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("reordered-central-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            entries);
    ArchiveTestUtils.reverseCentralDirectoryEntries(zipPath);

    SafeZipExtractor extractor = new SafeZipExtractor();
    Path destination = tempDir.resolve("output-reordered-" + repetitionInfo.getCurrentRepetition());

    SafeZipExtractor.ExtractionResult result =
        extractor.extract(zipPath, destination, OverwritePolicy.FAIL_IF_EXISTS);

    assertThat(result.extractedRoot()).isEqualTo(destination);
    assertExtractedEntries(destination, entries, repetitionInfo, 11);
  }

  @RepeatedTest(12)
  void randomizedDuplicateCentralDirectoryEntriesAreRejected(RepetitionInfo repetitionInfo)
      throws IOException {
    String entryName = "demo/file-" + repetitionInfo.getCurrentRepetition() + ".txt";
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("duplicate-central-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            Map.of(entryName, "payload".getBytes()));
    ArchiveTestUtils.duplicateCentralDirectoryEntry(zipPath, entryName);

    SafeZipExtractor extractor = new SafeZipExtractor();
    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath,
                    tempDir.resolve(
                        "output-duplicate-central-" + repetitionInfo.getCurrentRepetition()),
                    OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("Duplicate ZIP entry found after normalization");
  }

  @RepeatedTest(12)
  void randomizedCentralPayloadNameMismatchesAreRejected(RepetitionInfo repetitionInfo)
      throws IOException {
    String entryName = "demo/file-" + repetitionInfo.getCurrentRepetition() + ".txt";
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve(
                "central-payload-mismatch-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            Map.of(entryName, "payload".getBytes()));
    ArchiveTestUtils.patchCentralDirectoryEntryNameByte(zipPath, entryName, 0, (byte) 'x');

    SafeZipExtractor extractor = new SafeZipExtractor();
    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath,
                    tempDir.resolve(
                        "output-central-payload-mismatch-" + repetitionInfo.getCurrentRepetition()),
                    OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("missing in central directory");
  }

  @RepeatedTest(12)
  void randomizedPayloadCentralNameMismatchesAreRejected(RepetitionInfo repetitionInfo)
      throws IOException {
    String entryName = "demo/file-" + repetitionInfo.getCurrentRepetition() + ".txt";
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve(
                "payload-central-mismatch-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            Map.of(entryName, "payload".getBytes()));
    ArchiveTestUtils.patchFirstLocalFileHeaderNameByte(zipPath, 0, (byte) 'x');

    SafeZipExtractor extractor = new SafeZipExtractor();
    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath,
                    tempDir.resolve(
                        "output-payload-central-mismatch-" + repetitionInfo.getCurrentRepetition()),
                    OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("missing in central directory");
  }

  @RepeatedTest(16)
  void randomizedMalformedCentralHeadersAreRejected(RepetitionInfo repetitionInfo)
      throws IOException {
    SplittableRandom random = seededRandom(repetitionInfo, 14);
    Map<String, byte[]> entries = randomizedDemoEntries(random);
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve(
                "malformed-central-header-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            entries);
    ArchiveTestUtils.corruptRandomCentralDirectoryHeader(
        zipPath, BASE_SEED + repetitionInfo.getCurrentRepetition() * 31L + 14L);

    SafeZipExtractor extractor = new SafeZipExtractor();
    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath,
                    tempDir.resolve(
                        "output-malformed-central-header-" + repetitionInfo.getCurrentRepetition()),
                    OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("malformed central directory header");
  }

  @RepeatedTest(20)
  void randomizedDeclaredSizeMismatchesAreRejected(RepetitionInfo repetitionInfo)
      throws IOException {
    SplittableRandom random = seededRandom(repetitionInfo, 2);
    byte[] payload = randomBytes(random, random.nextInt(2_048, 8_192));
    String entryName = "demo/file-" + repetitionInfo.getCurrentRepetition() + ".txt";
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("size-mismatch-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            Map.of(entryName, payload));
    ArchiveTestUtils.patchUncompressedSize(
        zipPath, entryName, Math.max(0L, payload.length - random.nextInt(1, 64)));

    SafeZipExtractor extractor =
        new SafeZipExtractor(new ArchiveSafetyPolicy(1_000, 64L * 1024L * 1024L, 500.0d, 1L));

    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath,
                    tempDir.resolve(
                        "output-size-mismatch-" + repetitionInfo.getCurrentRepetition()),
                    OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("exceeds declared uncompressed size");
  }

  @RepeatedTest(12)
  void randomizedInvalidEocdOffsetsAreRejected(RepetitionInfo repetitionInfo) throws IOException {
    SplittableRandom random = seededRandom(repetitionInfo, 3);
    Path zipPath =
        ArchiveTestUtils.createZip(
            tempDir.resolve("invalid-eocd-" + repetitionInfo.getCurrentRepetition() + ".zip"),
            Map.of("demo/pom.xml", "<project/>".getBytes()));
    long fileSize = Files.size(zipPath);
    ArchiveTestUtils.patchEocdCentralDirectoryOffset(zipPath, fileSize + random.nextInt(1, 4_096));

    SafeZipExtractor extractor = new SafeZipExtractor();
    assertThatThrownBy(
            () ->
                extractor.extract(
                    zipPath,
                    tempDir.resolve("output-invalid-eocd-" + repetitionInfo.getCurrentRepetition()),
                    OverwritePolicy.FAIL_IF_EXISTS))
        .isInstanceOf(ArchiveException.class)
        .hasMessageContaining("central directory points outside archive");
  }

  private static SplittableRandom seededRandom(RepetitionInfo repetitionInfo, long salt) {
    return new SplittableRandom(
        BASE_SEED + (long) repetitionInfo.getCurrentRepetition() * 31L + salt);
  }

  private static Map<String, byte[]> randomizedDemoEntries(SplittableRandom random) {
    int entryCount = random.nextInt(1, 5);
    Set<String> normalizedNames = new LinkedHashSet<>();
    Map<String, byte[]> entries = new LinkedHashMap<>();
    while (entries.size() < entryCount) {
      boolean useBackslash = random.nextBoolean();
      String separator = useBackslash ? "\\" : "/";
      String name =
          "demo"
              + separator
              + "dir"
              + random.nextInt(0, 5)
              + separator
              + "file"
              + random.nextInt(0, 1_000)
              + ".txt";
      String normalized = SafeZipExtractor.normalizeEntryName(name);
      if (!normalizedNames.add(normalized)) {
        continue;
      }
      entries.put(name, randomBytes(random, random.nextInt(1, 2_048)));
    }
    return entries;
  }

  private static byte[] randomBytes(SplittableRandom random, int size) {
    byte[] bytes = new byte[size];
    random.nextBytes(bytes);
    if (Arrays.equals(bytes, new byte[size])) {
      bytes[0] = 1;
    }
    return bytes;
  }

  private static void assertExtractedEntries(
      Path destination, Map<String, byte[]> entries, RepetitionInfo repetitionInfo, long salt)
      throws IOException {
    for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
      String normalizedEntryName = SafeZipExtractor.normalizeEntryName(entry.getKey());
      String pathInOutput = normalizedEntryName.substring(normalizedEntryName.indexOf('/') + 1);
      assertThat(Files.readAllBytes(destination.resolve(pathInOutput)))
          .as(
              "seed=%s, entry=%s",
              BASE_SEED + (long) repetitionInfo.getCurrentRepetition() * 31L + salt, entry.getKey())
          .isEqualTo(entry.getValue());
    }
  }
}
