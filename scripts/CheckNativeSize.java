import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CheckNativeSize {
  private static final Pattern IMAGE_TOTAL_PATTERN =
      Pattern.compile("\"title\":\"Image Details\",\"subtitle\":\"([^\"]+) in total\"");
  private static final Pattern CODE_AREA_PATTERN =
      Pattern.compile("\"label\":\"Code Area\",\"text\":\"([^\"]+)\"");
  private static final Pattern IMAGE_HEAP_PATTERN =
      Pattern.compile("\"label\":\"Image Heap\",\"text\":\"([^\"]+)\"");
  private static final Pattern LEFT_ORIGIN_PATTERN =
      Pattern.compile("^\\s*([0-9.]+(?:MB|kB|B))\\s+(.+?)\\s{2,}[0-9.]+(?:MB|kB|B)\\s+.+$");
  private static final String ORIGINS_HEADER = "Top 10 origins of code area:";
  private static final String OBJECT_TYPES_HEADER = "Top 10 object types in image heap:";
  private static final String DETAILS_FOOTER = "                           For more details";

  public static void main(String[] args) {
    try {
      run(args);
    } catch (IllegalArgumentException | IOException exception) {
      System.err.println(exception.getMessage());
      System.exit(1);
    }
  }

  private static void run(String[] args) throws IOException {
    Arguments parsed = Arguments.parse(args);
    requireFile(parsed.binary());
    requireFile(parsed.report());
    requireFile(parsed.log());

    ReportSummary reportSummary = parseReport(parsed.report());
    List<OriginEntry> origins = parseOrigins(parsed.log(), parsed.topCount());
    long binarySize = Files.size(parsed.binary());

    System.out.println("### " + parsed.label());
    System.out.println("- Binary: `" + parsed.binary() + "`");
    System.out.println(
        "- File size: `"
            + binarySize
            + "` bytes ("
            + formatBytes(binarySize)
            + ") / budget `"
            + parsed.maxBytes()
            + "` bytes ("
            + formatBytes(parsed.maxBytes())
            + ")");
    System.out.println("- Build report image total: `" + reportSummary.imageTotal() + "`");
    System.out.println("- Build report code area: `" + reportSummary.codeArea() + "`");
    System.out.println("- Build report image heap: `" + reportSummary.imageHeap() + "`");
    if (origins.isEmpty()) {
      System.out.println("- Top code origins: unavailable from native-image log");
    } else {
      System.out.println("- Top code origins:");
      for (OriginEntry origin : origins) {
        System.out.println("  - `" + origin.label() + "`: `" + origin.size() + "`");
      }
    }

    if (binarySize > parsed.maxBytes()) {
      throw new IllegalArgumentException(
          parsed.label() + " binary size " + binarySize + " exceeds budget " + parsed.maxBytes());
    }
  }

  private static void requireFile(Path path) {
    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("required file missing: " + path);
    }
  }

  private static ReportSummary parseReport(Path reportPath) throws IOException {
    String text = Files.readString(reportPath);
    Matcher total = IMAGE_TOTAL_PATTERN.matcher(text);
    Matcher codeArea = CODE_AREA_PATTERN.matcher(text);
    Matcher imageHeap = IMAGE_HEAP_PATTERN.matcher(text);
    if (!total.find() || !codeArea.find() || !imageHeap.find()) {
      throw new IllegalArgumentException("failed to parse native build report: " + reportPath);
    }
    return new ReportSummary(total.group(1), codeArea.group(1), imageHeap.group(1));
  }

  private static List<OriginEntry> parseOrigins(Path logPath, int topCount) throws IOException {
    List<OriginEntry> origins = new ArrayList<>();
    boolean inSection = false;

    for (String line : Files.readAllLines(logPath)) {
      if (line.contains(ORIGINS_HEADER)) {
        inSection = true;
        continue;
      }
      if (!inSection) {
        continue;
      }
      if (line.contains(OBJECT_TYPES_HEADER) || line.startsWith(DETAILS_FOOTER)) {
        break;
      }

      Matcher match = LEFT_ORIGIN_PATTERN.matcher(line);
      if (!match.matches()) {
        continue;
      }
      origins.add(new OriginEntry(match.group(2).trim(), match.group(1)));
      if (origins.size() >= topCount) {
        break;
      }
    }

    return origins;
  }

  private static String formatBytes(long sizeBytes) {
    return "%.2f MiB".formatted(sizeBytes / (1024.0 * 1024.0));
  }

  private record ReportSummary(String imageTotal, String codeArea, String imageHeap) {}

  private record OriginEntry(String label, String size) {}

  private record Arguments(
      String label, Path binary, Path report, Path log, long maxBytes, int topCount) {
    private static Arguments parse(String[] args) {
      String label = null;
      Path binary = null;
      Path report = null;
      Path log = null;
      Long maxBytes = null;
      int topCount = 5;

      for (int index = 0; index < args.length; index++) {
        String argument = args[index];
        String value = requireValue(args, index, argument);
        switch (argument) {
          case "--label" -> label = value;
          case "--binary" -> binary = Path.of(value);
          case "--report" -> report = Path.of(value);
          case "--log" -> log = Path.of(value);
          case "--max-bytes" -> maxBytes = parseLong(value, "--max-bytes");
          case "--top-count" -> topCount = parseInt(value, "--top-count");
          default -> throw usage("unknown argument: " + argument);
        }
        index++;
      }

      if (label == null || label.isBlank()) {
        throw usage("missing required argument: --label");
      }
      if (binary == null) {
        throw usage("missing required argument: --binary");
      }
      if (report == null) {
        throw usage("missing required argument: --report");
      }
      if (log == null) {
        throw usage("missing required argument: --log");
      }
      if (maxBytes == null) {
        throw usage("missing required argument: --max-bytes");
      }
      if (maxBytes < 0) {
        throw usage("--max-bytes must be >= 0");
      }
      if (topCount < 1) {
        throw usage("--top-count must be >= 1");
      }

      return new Arguments(label, binary, report, log, maxBytes, topCount);
    }

    private static String requireValue(String[] args, int index, String argument) {
      if (index + 1 >= args.length) {
        throw usage("missing value for " + argument);
      }
      return args[index + 1];
    }

    private static long parseLong(String value, String argument) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException numberFormatException) {
        throw usage(argument + " must be an integer");
      }
    }

    private static int parseInt(String value, String argument) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException numberFormatException) {
        throw usage(argument + " must be an integer");
      }
    }

    private static IllegalArgumentException usage(String message) {
      return new IllegalArgumentException(
          message
              + System.lineSeparator()
              + "usage: java scripts/CheckNativeSize.java --label <name> --binary <path> --report <path> --log <path> --max-bytes <bytes> [--top-count <count>]");
    }
  }
}
