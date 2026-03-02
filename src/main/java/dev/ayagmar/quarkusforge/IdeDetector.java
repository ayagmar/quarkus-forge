package dev.ayagmar.quarkusforge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Detects installed IDEs on the current system (macOS, Linux, Windows). Returns a list of detected
 * IDEs with their launch commands, ordered by preference.
 */
public final class IdeDetector {
  public record DetectedIde(String name, String command) {}

  private IdeDetector() {}

  public static List<DetectedIde> detect() {
    var result = new ArrayList<DetectedIde>();
    var seen = new LinkedHashSet<String>();

    // Custom IDE from env var takes priority
    String custom = System.getenv("QUARKUS_FORGE_IDE_COMMAND");
    if (custom != null && !custom.isBlank()) {
      add(result, seen, new DetectedIde("Custom (" + custom.strip() + ")", custom.strip()));
    }

    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) {
      detectMacOs(result, seen);
    } else if (os.contains("win")) {
      detectWindows(result, seen);
    } else {
      detectLinux(result, seen);
    }

    return List.copyOf(result);
  }

  private static void detectMacOs(List<DetectedIde> result, LinkedHashSet<String> seen) {
    // IntelliJ IDEA
    if (macAppExists("IntelliJ IDEA Ultimate")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA Ultimate", "idea ."));
    } else if (macAppExists("IntelliJ IDEA CE")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA CE", "idea ."));
    } else if (commandExists("idea")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA", "idea ."));
    }

    // VS Code
    if (macAppExists("Visual Studio Code")) {
      add(result, seen, new DetectedIde("VS Code", "code ."));
    } else if (commandExists("code")) {
      add(result, seen, new DetectedIde("VS Code", "code ."));
    }

    // Eclipse
    if (macAppExists("Eclipse")) {
      add(result, seen, new DetectedIde("Eclipse", "eclipse ."));
    }

    detectCommonEditors(result, seen);
  }

  private static void detectLinux(List<DetectedIde> result, LinkedHashSet<String> seen) {
    // IntelliJ IDEA
    if (commandExists("idea")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA", "idea ."));
    } else if (commandExists("idea.sh")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA", "idea.sh ."));
    }

    // VS Code
    if (commandExists("code")) {
      add(result, seen, new DetectedIde("VS Code", "code ."));
    }

    // Eclipse
    if (commandExists("eclipse")) {
      add(result, seen, new DetectedIde("Eclipse", "eclipse ."));
    }

    detectCommonEditors(result, seen);
  }

  private static void detectWindows(List<DetectedIde> result, LinkedHashSet<String> seen) {
    // IntelliJ IDEA — JetBrains Toolbox adds idea64.exe to PATH
    if (commandExists("idea64.exe")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA", "idea64.exe ."));
    } else if (commandExists("idea.exe")) {
      add(result, seen, new DetectedIde("IntelliJ IDEA", "idea.exe ."));
    }

    // VS Code — installer adds code.cmd to PATH
    if (commandExists("code.cmd")) {
      add(result, seen, new DetectedIde("VS Code", "code.cmd ."));
    } else if (commandExists("code")) {
      add(result, seen, new DetectedIde("VS Code", "code ."));
    }

    // Eclipse
    if (commandExists("eclipse.exe")) {
      add(result, seen, new DetectedIde("Eclipse", "eclipse.exe ."));
    }

    // Cursor
    if (commandExists("cursor.cmd")) {
      add(result, seen, new DetectedIde("Cursor", "cursor.cmd ."));
    } else if (commandExists("cursor")) {
      add(result, seen, new DetectedIde("Cursor", "cursor ."));
    }

    // Neovim — opens terminal-based, so open current dir
    if (commandExists("nvim.exe")) {
      add(result, seen, new DetectedIde("Neovim", "nvim.exe ."));
    }
  }

  /** Editors available on all Unix-like systems (macOS, Linux). */
  private static void detectCommonEditors(List<DetectedIde> result, LinkedHashSet<String> seen) {
    if (commandExists("cursor")) {
      add(result, seen, new DetectedIde("Cursor", "cursor ."));
    }
    if (commandExists("zed")) {
      add(result, seen, new DetectedIde("Zed", "zed ."));
    }
    if (commandExists("nvim")) {
      add(result, seen, new DetectedIde("Neovim", "nvim ."));
    }
  }

  private static void add(List<DetectedIde> result, LinkedHashSet<String> seen, DetectedIde ide) {
    if (seen.add(ide.command())) {
      result.add(ide);
    }
  }

  private static boolean macAppExists(String appName) {
    return Files.isDirectory(Path.of("/Applications", appName + ".app"));
  }

  private static boolean commandExists(String command) {
    return PostTuiActionExecutor.isCommandAvailable(command);
  }
}
