package dev.ayagmar.quarkusforge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@FunctionalInterface
interface ShellProcessRunner {
  int run(List<String> invocation, Path workingDirectory) throws IOException, InterruptedException;
}
