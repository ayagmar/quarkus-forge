package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicFileStoreTest {
  @TempDir Path tempDir;

  @Test
  void writeBytesPersistsPayload() throws Exception {
    Path target = tempDir.resolve("prefs.json");

    AtomicFileStore.writeBytes(target, "{\"ok\":true}".getBytes(), "atomic-test-");

    assertThat(Files.readString(target)).isEqualTo("{\"ok\":true}");
  }

  @Test
  void writeBytesFallsBackWhenAtomicMoveIsNotSupported() throws Exception {
    Path target = tempDir.resolve("catalog.json");
    AtomicInteger moveCalls = new AtomicInteger();
    AtomicFileStore.MoveOperation mover =
        (source, destination, options) -> {
          moveCalls.incrementAndGet();
          if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
            throw new AtomicMoveNotSupportedException(
                source.toString(), destination.toString(), "");
          }
          Files.move(source, destination, options);
        };

    AtomicFileStore.writeBytes(target, "{\"fallback\":true}".getBytes(), "atomic-test-", mover);

    assertThat(Files.readString(target)).isEqualTo("{\"fallback\":true}");
    assertThat(moveCalls.get()).isEqualTo(2);
  }
}
