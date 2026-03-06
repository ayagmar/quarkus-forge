package dev.ayagmar.quarkusforge.postgen;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

final class CommandUtils {
  private CommandUtils() {}

  static boolean isCommandAvailable(String command) {
    return isCommandAvailable(command, System.getenv("PATH"));
  }

  static boolean isCommandAvailable(String command, String pathValue) {
    if (command == null || command.isBlank()) {
      return false;
    }
    if (pathValue == null || pathValue.isBlank()) {
      return false;
    }

    String executable = command.strip();
    String[] pathEntries = pathValue.split(File.pathSeparator);
    boolean windows = isWindowsOs();
    for (String pathEntry : pathEntries) {
      if (pathEntry == null || pathEntry.isBlank()) {
        continue;
      }
      Path directory;
      try {
        directory = Path.of(pathEntry);
      } catch (InvalidPathException invalidPathException) {
        continue;
      }
      if (isExecutableFile(directory.resolve(executable), windows)) {
        return true;
      }
      if (windows) {
        if (isExecutableFile(directory.resolve(executable + ".exe"), true)
            || isExecutableFile(directory.resolve(executable + ".cmd"), true)
            || isExecutableFile(directory.resolve(executable + ".bat"), true)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isExecutableFile(Path path, boolean windows) {
    if (!Files.isRegularFile(path)) {
      return false;
    }
    return windows || Files.isExecutable(path);
  }

  private static boolean isWindowsOs() {
    String osName = System.getProperty("os.name", "");
    return osName.toLowerCase(Locale.ROOT).contains("win");
  }
}
