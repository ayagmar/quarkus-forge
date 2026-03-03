package dev.ayagmar.quarkusforge.archive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZipEntryMetadataTest {

  @Test
  void symbolicLinkDetectedByUnixMode() {
    ZipEntryMetadata entry = new ZipEntryMetadata("link.txt", 100, 200, 0xA000 | 0x01FF);

    assertThat(entry.isSymbolicLink()).isTrue();
  }

  @Test
  void regularFileIsNotSymbolicLink() {
    ZipEntryMetadata entry = new ZipEntryMetadata("file.txt", 100, 200, 0x8000 | 0x01B4);

    assertThat(entry.isSymbolicLink()).isFalse();
  }

  @Test
  void directoryIsNotSymbolicLink() {
    ZipEntryMetadata entry = new ZipEntryMetadata("dir/", 0, 0, 0x4000 | 0x01ED);

    assertThat(entry.isSymbolicLink()).isFalse();
  }

  @Test
  void zeroUnixModeIsNotSuspicious() {
    ZipEntryMetadata entry = new ZipEntryMetadata("file.txt", 100, 200, 0);

    assertThat(entry.hasSuspiciousUnixMode()).isFalse();
  }

  @Test
  void regularFileWithNormalPermissionsIsNotSuspicious() {
    // 0x8000 = regular file, 0x01B4 = rw-r--r-- (644)
    ZipEntryMetadata entry = new ZipEntryMetadata("file.txt", 100, 200, 0x8000 | 0x01B4);

    assertThat(entry.hasSuspiciousUnixMode()).isFalse();
  }

  @Test
  void directoryWithNormalPermissionsIsNotSuspicious() {
    // 0x4000 = directory, 0x01ED = rwxr-xr-x (755)
    ZipEntryMetadata entry = new ZipEntryMetadata("dir/", 0, 0, 0x4000 | 0x01ED);

    assertThat(entry.hasSuspiciousUnixMode()).isFalse();
  }

  @Test
  void symbolicLinkPermissionIsNotSuspicious() {
    ZipEntryMetadata entry = new ZipEntryMetadata("link", 100, 200, 0xA000 | 0x01FF);

    assertThat(entry.hasSuspiciousUnixMode()).isFalse();
  }

  @Test
  void unknownFileTypeIsSuspicious() {
    // 0x6000 = block device, not in knownType list
    ZipEntryMetadata entry = new ZipEntryMetadata("dev", 0, 0, 0x6000 | 0x01B4);

    assertThat(entry.hasSuspiciousUnixMode()).isTrue();
  }

  @Test
  void setuidBitIsSuspicious() {
    // 0x8000 = regular file, 0x0800 = setuid, 0x01ED = 755
    ZipEntryMetadata entry = new ZipEntryMetadata("suid", 100, 200, 0x8000 | 0x0800 | 0x01ED);

    assertThat(entry.hasSuspiciousUnixMode()).isTrue();
  }

  @Test
  void setgidBitIsSuspicious() {
    // 0x8000 = regular file, 0x0400 = setgid, 0x01ED = 755
    ZipEntryMetadata entry = new ZipEntryMetadata("sgid", 100, 200, 0x8000 | 0x0400 | 0x01ED);

    assertThat(entry.hasSuspiciousUnixMode()).isTrue();
  }

  @Test
  void stickyBitIsSuspicious() {
    // 0x4000 = directory, 0x0200 = sticky, 0x01FF = 777
    ZipEntryMetadata entry = new ZipEntryMetadata("sticky", 0, 0, 0x4000 | 0x0200 | 0x01FF);

    assertThat(entry.hasSuspiciousUnixMode()).isTrue();
  }
}
