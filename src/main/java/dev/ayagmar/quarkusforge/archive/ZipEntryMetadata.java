package dev.ayagmar.quarkusforge.archive;

record ZipEntryMetadata(String name, long compressedSize, long uncompressedSize, int unixMode) {
  boolean isSymbolicLink() {
    return (unixMode & 0xF000) == 0xA000;
  }

  boolean hasSuspiciousUnixMode() {
    if (unixMode == 0) {
      return false;
    }

    int type = unixMode & 0xF000;
    boolean knownType = type == 0x8000 || type == 0x4000 || type == 0xA000;
    if (!knownType) {
      return true;
    }

    int elevatedBits = unixMode & 0x0E00;
    return elevatedBits != 0;
  }
}
