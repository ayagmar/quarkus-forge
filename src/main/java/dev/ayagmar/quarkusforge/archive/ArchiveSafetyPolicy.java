package dev.ayagmar.quarkusforge.archive;

public record ArchiveSafetyPolicy(
    int maxEntries,
    long maxTotalUncompressedBytes,
    double maxCompressionRatio,
    long minBytesForCompressionRatioCheck) {
  public ArchiveSafetyPolicy {
    if (maxEntries < 1) {
      throw new IllegalArgumentException("maxEntries must be >= 1");
    }
    if (maxTotalUncompressedBytes < 1) {
      throw new IllegalArgumentException("maxTotalUncompressedBytes must be >= 1");
    }
    if (maxCompressionRatio < 1.0d) {
      throw new IllegalArgumentException("maxCompressionRatio must be >= 1");
    }
    if (minBytesForCompressionRatioCheck < 0) {
      throw new IllegalArgumentException("minBytesForCompressionRatioCheck must be >= 0");
    }
  }

  public static ArchiveSafetyPolicy defaults() {
    return new ArchiveSafetyPolicy(20_000, 512L * 1024L * 1024L, 150.0d, 1L * 1024L * 1024L);
  }
}
