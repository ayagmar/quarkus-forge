package dev.ayagmar.quarkusforge.archive;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class ZipCentralDirectoryReader {
  private static final int EOCD_SIGNATURE = 0x06054B50;
  private static final int CEN_SIGNATURE = 0x02014B50;
  private static final int EOCD_MIN_LENGTH = 22;
  private static final int EOCD_MAX_COMMENT_LENGTH = 65_535;
  private static final int CEN_FIXED_LENGTH = 46;

  private ZipCentralDirectoryReader() {}

  static Map<String, ZipEntryMetadata> read(Path zipFile) {
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(zipFile.toFile(), "r");
        FileChannel channel = randomAccessFile.getChannel()) {
      Eocd eocd = findEocd(channel);
      return readCentralDirectory(channel, eocd);
    } catch (IOException ioException) {
      throw new ArchiveException("Failed to read ZIP central directory: " + zipFile, ioException);
    }
  }

  private static Eocd findEocd(FileChannel channel) throws IOException {
    long size = channel.size();
    if (size < EOCD_MIN_LENGTH) {
      throw new ArchiveException("Invalid ZIP: file is too small");
    }

    int tailLength = (int) Math.min(size, EOCD_MIN_LENGTH + EOCD_MAX_COMMENT_LENGTH);
    byte[] tail = new byte[tailLength];
    ByteBuffer tailBuffer = ByteBuffer.wrap(tail);
    channel.position(size - tailLength);
    readFully(channel, tailBuffer);

    for (int i = tailLength - EOCD_MIN_LENGTH; i >= 0; i--) {
      if (u32(tail, i) != Integer.toUnsignedLong(EOCD_SIGNATURE)) {
        continue;
      }

      int entries = u16(tail, i + 10);
      long centralDirectorySize = u32(tail, i + 12);
      long centralDirectoryOffset = u32(tail, i + 16);
      if (centralDirectoryOffset + centralDirectorySize > size) {
        throw new ArchiveException("Invalid ZIP: central directory points outside archive");
      }
      return new Eocd(entries, centralDirectorySize, centralDirectoryOffset);
    }

    throw new ArchiveException("Invalid ZIP: end-of-central-directory record not found");
  }

  private static Map<String, ZipEntryMetadata> readCentralDirectory(FileChannel channel, Eocd eocd)
      throws IOException {
    channel.position(eocd.centralDirectoryOffset());
    Map<String, ZipEntryMetadata> entries = new LinkedHashMap<>();

    long consumed = 0;
    while (consumed < eocd.centralDirectorySize()) {
      byte[] header = new byte[CEN_FIXED_LENGTH];
      ByteBuffer headerBuffer = ByteBuffer.wrap(header);
      readFully(channel, headerBuffer);

      long signature = u32(header, 0);
      if (signature != Integer.toUnsignedLong(CEN_SIGNATURE)) {
        throw new ArchiveException("Invalid ZIP: malformed central directory header");
      }

      long compressedSize = u32(header, 20);
      long uncompressedSize = u32(header, 24);
      int fileNameLength = u16(header, 28);
      int extraLength = u16(header, 30);
      int commentLength = u16(header, 32);
      int unixMode = (int) ((u32(header, 38) >>> 16) & 0xFFFF);

      if (compressedSize == 0xFFFF_FFFFL || uncompressedSize == 0xFFFF_FFFFL) {
        throw new ArchiveException("ZIP64 archives are not supported");
      }

      byte[] nameBytes = new byte[fileNameLength];
      ByteBuffer nameBuffer = ByteBuffer.wrap(nameBytes);
      readFully(channel, nameBuffer);
      String entryName = new String(nameBytes, StandardCharsets.UTF_8);
      String normalizedEntryName = SafeZipExtractor.normalizeEntryName(entryName);

      long skip = (long) extraLength + commentLength;
      channel.position(channel.position() + skip);

      if (entries.containsKey(normalizedEntryName)) {
        throw new ArchiveException(
            "Duplicate ZIP entry found after normalization: " + normalizedEntryName);
      }
      entries.put(
          normalizedEntryName,
          new ZipEntryMetadata(normalizedEntryName, compressedSize, uncompressedSize, unixMode));

      consumed += CEN_FIXED_LENGTH + fileNameLength + skip;
    }

    if (entries.size() != eocd.entries()) {
      throw new ArchiveException(
          "ZIP central directory entry count mismatch, expected "
              + eocd.entries()
              + " but found "
              + entries.size());
    }

    return Collections.unmodifiableMap(entries);
  }

  private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      int read = channel.read(buffer);
      if (read < 0) {
        throw new ArchiveException("Unexpected end of ZIP file");
      }
    }
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
}
