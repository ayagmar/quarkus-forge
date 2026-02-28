package dev.ayagmar.quarkusforge.api;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Path;

@FunctionalInterface
interface FileMoveOperation {
  void move(Path source, Path target, CopyOption... options) throws IOException;
}
