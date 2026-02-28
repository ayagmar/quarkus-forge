package dev.ayagmar.quarkusforge.archive;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeZipExtractorLargeArchiveTest {
  private static final int LARGE_FILE_SIZE_BYTES = 96 * 1024 * 1024;
  private static final int CHUNK_SIZE = 8 * 1024;

  @TempDir Path tempDir;

  @Test
  void extractsLargeArchiveWithoutInMemoryWholeZipBuffering() throws IOException {
    Path zipPath = tempDir.resolve("large.zip");
    createLargeZip(zipPath, LARGE_FILE_SIZE_BYTES);
    long zipBytes = Files.size(zipPath);
    long heapBefore = usedHeap();

    SafeZipExtractor extractor =
        new SafeZipExtractor(
            new ArchiveSafetyPolicy(10_000, 256L * 1024L * 1024L, 500.0d, 1L * 1024L * 1024L));
    Path output = tempDir.resolve("generated-project");

    ExtractionResult result =
        extractor.extract(zipPath, output, OverwritePolicy.FAIL_IF_EXISTS);
    long heapAfter = usedHeap();
    long heapDelta = heapAfter - heapBefore;

    assertThat(result.extractedRoot()).isEqualTo(output);
    assertThat(Files.size(output.resolve("big.bin"))).isEqualTo(LARGE_FILE_SIZE_BYTES);
    if (Boolean.getBoolean("quarkusforge.enforceHeapDelta")) {
      assertThat(heapDelta).isLessThan(20L * 1024L * 1024L);
    }

    System.out.println(
        "MEM_EVIDENCE zipBytes="
            + zipBytes
            + " extractedBytes="
            + result.extractedBytes()
            + " heapBefore="
            + heapBefore
            + " heapAfter="
            + heapAfter
            + " heapDelta="
            + heapDelta);
  }

  private static void createLargeZip(Path zipPath, int targetSizeBytes) throws IOException {
    byte[] buffer = new byte[CHUNK_SIZE];
    SplittableRandom random = new SplittableRandom(42L);

    try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
      zipOutputStream.setLevel(Deflater.BEST_SPEED);
      zipOutputStream.putNextEntry(new ZipEntry("demo/big.bin"));

      int remaining = targetSizeBytes;
      while (remaining > 0) {
        random.nextBytes(buffer);
        int toWrite = Math.min(remaining, buffer.length);
        zipOutputStream.write(buffer, 0, toWrite);
        remaining -= toWrite;
      }
      zipOutputStream.closeEntry();
    }
  }

  private static long usedHeap() {
    Runtime runtime = Runtime.getRuntime();
    runtime.gc();
    runtime.gc();
    return runtime.totalMemory() - runtime.freeMemory();
  }
}
