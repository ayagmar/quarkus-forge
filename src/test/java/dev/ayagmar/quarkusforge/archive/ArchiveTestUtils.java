package dev.ayagmar.quarkusforge.archive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ArchiveTestUtils {
  private static final long EOCD_SIGNATURE = 0x06054B50L;
  private static final long CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L;
  private static final int CENTRAL_DIRECTORY_FIXED_LENGTH = 46;
  private static final int EOCD_MIN_LENGTH = 22;

  private ArchiveTestUtils() {}

  static Path createZip(Path zipPath, Map<String, byte[]> entries) throws IOException {
    try (ZipOutputStream zipOutputStream =
        new ZipOutputStream(Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {
      zipOutputStream.setLevel(Deflater.BEST_COMPRESSION);
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zipOutputStream.putNextEntry(zipEntry);
        zipOutputStream.write(entry.getValue());
        zipOutputStream.closeEntry();
      }
    }
    return zipPath;
  }

  static void patchUnixMode(Path zipPath, String entryName, int unixMode) throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    int offset = findCentralDirectoryEntryOffset(bytes, entryName);
    long currentExternalAttributes = u32(bytes, offset + 38);
    long patched = (currentExternalAttributes & 0xFFFFL) | (((long) unixMode) << 16);
    writeU32(bytes, offset + 38, patched);
    Files.write(zipPath, bytes);
  }

  static void patchUncompressedSize(Path zipPath, String entryName, long uncompressedSize)
      throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    int offset = findCentralDirectoryEntryOffset(bytes, entryName);
    writeU32(bytes, offset + 24, uncompressedSize);
    Files.write(zipPath, bytes);
  }

  static void patchEocdCentralDirectoryOffset(Path zipPath, long centralDirectoryOffset)
      throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    int eocdOffset = findEocdOffset(bytes);
    writeU32(bytes, eocdOffset + 16, centralDirectoryOffset);
    Files.write(zipPath, bytes);
  }

  private static int findCentralDirectoryEntryOffset(byte[] bytes, String entryName) {
    int offset = 0;
    while (offset <= bytes.length - CENTRAL_DIRECTORY_FIXED_LENGTH) {
      if (u32(bytes, offset) != CENTRAL_DIRECTORY_SIGNATURE) {
        offset++;
        continue;
      }

      int nameLength = u16(bytes, offset + 28);
      int extraLength = u16(bytes, offset + 30);
      int commentLength = u16(bytes, offset + 32);
      String currentName = new String(bytes, offset + 46, nameLength, StandardCharsets.UTF_8);
      if (currentName.equals(entryName)) {
        return offset;
      }
      offset += CENTRAL_DIRECTORY_FIXED_LENGTH + nameLength + extraLength + commentLength;
    }
    throw new IllegalArgumentException("Entry not found in ZIP central directory: " + entryName);
  }

  private static int findEocdOffset(byte[] bytes) {
    for (int offset = bytes.length - EOCD_MIN_LENGTH; offset >= 0; offset--) {
      if (u32(bytes, offset) == EOCD_SIGNATURE) {
        return offset;
      }
    }
    throw new IllegalArgumentException("EOCD record not found");
  }

  private static int u16(byte[] bytes, int offset) {
    return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
  }

  private static long u32(byte[] bytes, int offset) {
    return Integer.toUnsignedLong(
        (bytes[offset] & 0xFF)
            | ((bytes[offset + 1] & 0xFF) << 8)
            | ((bytes[offset + 2] & 0xFF) << 16)
            | ((bytes[offset + 3] & 0xFF) << 24));
  }

  private static void writeU32(byte[] bytes, int offset, long value) {
    bytes[offset] = (byte) (value & 0xFF);
    bytes[offset + 1] = (byte) ((value >> 8) & 0xFF);
    bytes[offset + 2] = (byte) ((value >> 16) & 0xFF);
    bytes[offset + 3] = (byte) ((value >> 24) & 0xFF);
  }
}
