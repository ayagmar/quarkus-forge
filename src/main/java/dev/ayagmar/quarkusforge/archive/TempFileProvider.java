package dev.ayagmar.quarkusforge.archive;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
interface TempFileProvider {
  Path create() throws IOException;
}
