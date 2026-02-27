package dev.ayagmar.quarkusforge.archive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ArchiveTestUtils {
  private static final long EOCD_SIGNATURE = 0x06054B50L;
  private static final long CENTRAL_DIRECTORY_SIGNATURE = 0x02014B50L;
  private static final long LOCAL_FILE_HEADER_SIGNATURE = 0x04034B50L;
  private static final int CENTRAL_DIRECTORY_FIXED_LENGTH = 46;
  private static final int LOCAL_FILE_HEADER_FIXED_LENGTH = 30;
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

  static void duplicateCentralDirectoryEntry(Path zipPath, String entryName) throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    CentralDirectory centralDirectory = readCentralDirectory(bytes);
    List<CentralDirectoryEntryRecord> entries =
        readCentralDirectoryEntries(bytes, centralDirectory);
    CentralDirectoryEntryRecord entryRecord =
        entries.stream()
            .filter(entry -> entry.name().equals(entryName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Entry not found in ZIP central directory: " + entryName));

    byte[] entryBytes =
        slice(bytes, entryRecord.offset(), entryRecord.offset() + entryRecord.length());
    int centralEnd = centralDirectory.offset() + centralDirectory.size();
    byte[] mutated =
        concat(slice(bytes, 0, centralEnd), entryBytes, slice(bytes, centralEnd, bytes.length));
    int newCentralSize = centralDirectory.size() + entryBytes.length;
    int newEntryCount = centralDirectory.entryCount() + 1;
    int newEocdOffset = centralDirectory.eocdOffset() + entryBytes.length;
    writeU32(mutated, newEocdOffset + 12, newCentralSize);
    writeU16(mutated, newEocdOffset + 8, newEntryCount);
    writeU16(mutated, newEocdOffset + 10, newEntryCount);
    Files.write(zipPath, mutated);
  }

  static void reverseCentralDirectoryEntries(Path zipPath) throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    CentralDirectory centralDirectory = readCentralDirectory(bytes);
    List<CentralDirectoryEntryRecord> entries =
        readCentralDirectoryEntries(bytes, centralDirectory);
    byte[] reorderedCentralDirectory = new byte[centralDirectory.size()];
    int writeOffset = 0;
    for (int index = entries.size() - 1; index >= 0; index--) {
      CentralDirectoryEntryRecord entry = entries.get(index);
      byte[] entryBytes = slice(bytes, entry.offset(), entry.offset() + entry.length());
      System.arraycopy(entryBytes, 0, reorderedCentralDirectory, writeOffset, entryBytes.length);
      writeOffset += entryBytes.length;
    }
    if (writeOffset != reorderedCentralDirectory.length) {
      throw new IllegalStateException("Failed to reorder all central directory records");
    }
    byte[] mutated = bytes.clone();
    System.arraycopy(
        reorderedCentralDirectory,
        0,
        mutated,
        centralDirectory.offset(),
        reorderedCentralDirectory.length);
    Files.write(zipPath, mutated);
  }

  static void patchCentralDirectoryEntryNameByte(
      Path zipPath, String entryName, int relativeByteIndex, byte replacement) throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    int offset = findCentralDirectoryEntryOffset(bytes, entryName);
    int nameLength = u16(bytes, offset + 28);
    if (relativeByteIndex < 0 || relativeByteIndex >= nameLength) {
      throw new IllegalArgumentException(
          "Name byte index out of range for entry '%s': %s"
              .formatted(entryName, relativeByteIndex));
    }
    bytes[offset + CENTRAL_DIRECTORY_FIXED_LENGTH + relativeByteIndex] = replacement;
    Files.write(zipPath, bytes);
  }

  static void patchFirstLocalFileHeaderNameByte(
      Path zipPath, int relativeByteIndex, byte replacement) throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    if (u32(bytes, 0) != LOCAL_FILE_HEADER_SIGNATURE) {
      throw new IllegalArgumentException("ZIP local header signature not found at offset 0");
    }
    int nameLength = u16(bytes, 26);
    if (relativeByteIndex < 0 || relativeByteIndex >= nameLength) {
      throw new IllegalArgumentException("Name byte index out of range: " + relativeByteIndex);
    }
    bytes[LOCAL_FILE_HEADER_FIXED_LENGTH + relativeByteIndex] = replacement;
    Files.write(zipPath, bytes);
  }

  static void corruptRandomCentralDirectoryHeader(Path zipPath, long seed) throws IOException {
    byte[] bytes = Files.readAllBytes(zipPath);
    CentralDirectory centralDirectory = readCentralDirectory(bytes);
    List<CentralDirectoryEntryRecord> entries =
        readCentralDirectoryEntries(bytes, centralDirectory);
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("ZIP central directory is empty");
    }
    SplittableRandom random = new SplittableRandom(seed);
    CentralDirectoryEntryRecord entry = entries.get(random.nextInt(entries.size()));
    long randomSignature = random.nextLong();
    while ((randomSignature & 0xFFFF_FFFFL) == CENTRAL_DIRECTORY_SIGNATURE) {
      randomSignature = random.nextLong();
    }
    writeU32(bytes, entry.offset(), randomSignature);
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

  private static CentralDirectory readCentralDirectory(byte[] bytes) {
    int eocdOffset = findEocdOffset(bytes);
    int centralDirectorySize = (int) u32(bytes, eocdOffset + 12);
    int centralDirectoryOffset = (int) u32(bytes, eocdOffset + 16);
    int entryCount = u16(bytes, eocdOffset + 10);
    if (centralDirectoryOffset < 0 || centralDirectorySize < 0) {
      throw new IllegalArgumentException("Negative central directory bounds");
    }
    int centralDirectoryEnd = centralDirectoryOffset + centralDirectorySize;
    if (centralDirectoryEnd > bytes.length || centralDirectoryOffset > centralDirectoryEnd) {
      throw new IllegalArgumentException("Invalid central directory bounds");
    }
    return new CentralDirectory(
        centralDirectoryOffset, centralDirectorySize, eocdOffset, entryCount);
  }

  private static List<CentralDirectoryEntryRecord> readCentralDirectoryEntries(
      byte[] bytes, CentralDirectory centralDirectory) {
    List<CentralDirectoryEntryRecord> entries = new ArrayList<>();
    int offset = centralDirectory.offset();
    int centralDirectoryEnd = centralDirectory.offset() + centralDirectory.size();
    while (offset < centralDirectoryEnd) {
      if (u32(bytes, offset) != CENTRAL_DIRECTORY_SIGNATURE) {
        throw new IllegalArgumentException("Malformed central directory entry at offset " + offset);
      }
      int nameLength = u16(bytes, offset + 28);
      int extraLength = u16(bytes, offset + 30);
      int commentLength = u16(bytes, offset + 32);
      int length = CENTRAL_DIRECTORY_FIXED_LENGTH + nameLength + extraLength + commentLength;
      if (offset + length > centralDirectoryEnd) {
        throw new IllegalArgumentException("Central directory entry exceeds declared bounds");
      }
      String name =
          new String(
              bytes, offset + CENTRAL_DIRECTORY_FIXED_LENGTH, nameLength, StandardCharsets.UTF_8);
      entries.add(new CentralDirectoryEntryRecord(offset, length, name));
      offset += length;
    }
    if (offset != centralDirectoryEnd) {
      throw new IllegalArgumentException("Central directory parsing did not consume full bounds");
    }
    return entries;
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

  private static void writeU16(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) (value & 0xFF);
    bytes[offset + 1] = (byte) ((value >> 8) & 0xFF);
  }

  private static byte[] slice(byte[] bytes, int startInclusive, int endExclusive) {
    byte[] slice = new byte[endExclusive - startInclusive];
    System.arraycopy(bytes, startInclusive, slice, 0, slice.length);
    return slice;
  }

  private static byte[] concat(byte[]... segments) {
    int totalLength = 0;
    for (byte[] segment : segments) {
      totalLength += segment.length;
    }
    byte[] combined = new byte[totalLength];
    int offset = 0;
    for (byte[] segment : segments) {
      System.arraycopy(segment, 0, combined, offset, segment.length);
      offset += segment.length;
    }
    return combined;
  }

  private record CentralDirectory(int offset, int size, int eocdOffset, int entryCount) {}

  private record CentralDirectoryEntryRecord(int offset, int length, String name) {}
}
