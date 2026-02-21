package dev.ayagmar.quarkusforge.footprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FootprintBaselineIT {
  private static final Path BASELINE_PATH = Path.of("config/footprint-baseline.properties");
  private static final Path RUNTIME_TREE_PATH = Path.of("target/runtime-dependency-tree.txt");
  private static final Path SHADED_JAR_PATH = Path.of("target/quarkus-forge.jar");
  private static final Pattern RUNTIME_DEPENDENCY_PATTERN =
      Pattern.compile("([A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+):jar:[^:]+:(compile|runtime)");

  @Test
  void runtimeDependencyGrowthStaysWithinBaselineBudget() throws IOException {
    Properties baseline = loadBaseline();
    Set<String> allowedDependencies =
        splitCsv(baseline.getProperty("runtime.allowed.dependencies", ""));
    int maxDependencyCount =
        Integer.parseInt(baseline.getProperty("runtime.max.dependency.count", "0"));

    assertThat(Files.exists(RUNTIME_TREE_PATH))
        .as("runtime dependency tree report should exist at %s", RUNTIME_TREE_PATH)
        .isTrue();

    Set<String> runtimeDependencies = parseRuntimeDependencies(RUNTIME_TREE_PATH);
    assertThat(runtimeDependencies)
        .as("Runtime dependency count should stay below budget")
        .hasSizeLessThanOrEqualTo(maxDependencyCount);
    assertThat(allowedDependencies)
        .as("Baseline should declare an allow-list of runtime dependencies")
        .isNotEmpty();
    assertThat(allowedDependencies)
        .as("Allow-list should include all currently observed runtime dependencies")
        .containsAll(runtimeDependencies);
  }

  @Test
  void shadedJarSizeRegressionStaysWithinBudget() throws IOException {
    Properties baseline = loadBaseline();

    long baselineBytes = Long.parseLong(baseline.getProperty("jar.baseline.bytes", "0"));
    double maxRegressionPercent =
        Double.parseDouble(baseline.getProperty("jar.max.regression.percent", "0"));

    assertThat(Files.exists(SHADED_JAR_PATH))
        .as("Shaded JAR should exist at %s", SHADED_JAR_PATH)
        .isTrue();
    long currentJarBytes = Files.size(SHADED_JAR_PATH);

    long maxAllowedBytes =
        baselineBytes + Math.round(baselineBytes * (maxRegressionPercent / 100.0d));
    assertThat(currentJarBytes)
        .as(
            "Shaded JAR size must remain within %.2f%% budget (baseline=%d, current=%d, max=%d)",
            maxRegressionPercent, baselineBytes, currentJarBytes, maxAllowedBytes)
        .isLessThanOrEqualTo(maxAllowedBytes);
  }

  @Test
  void nativeBinaryRegressionCheckIsAppliedWhenNativeArtifactExists() throws IOException {
    Properties baseline = loadBaseline();

    long nativeBaselineBytes = Long.parseLong(baseline.getProperty("native.baseline.bytes", "-1"));
    if (nativeBaselineBytes < 0) {
      return;
    }

    double maxRegressionPercent =
        Double.parseDouble(baseline.getProperty("native.max.regression.percent", "0"));
    Path nativeArtifact = Path.of("target/quarkus-forge");
    assertThat(Files.exists(nativeArtifact))
        .as("Expected native artifact at %s", nativeArtifact)
        .isTrue();

    long currentNativeBytes = Files.size(nativeArtifact);
    long maxAllowedBytes =
        nativeBaselineBytes + Math.round(nativeBaselineBytes * (maxRegressionPercent / 100.0d));
    assertThat(currentNativeBytes)
        .as(
            "Native artifact size must remain within %.2f%% budget (baseline=%d, current=%d, max=%d)",
            maxRegressionPercent, nativeBaselineBytes, currentNativeBytes, maxAllowedBytes)
        .isLessThanOrEqualTo(maxAllowedBytes);
  }

  private static Properties loadBaseline() throws IOException {
    Properties properties = new Properties();
    assertThat(Files.exists(BASELINE_PATH))
        .as("Expected footprint baseline file at %s", BASELINE_PATH)
        .isTrue();
    try (var inputStream = Files.newInputStream(BASELINE_PATH)) {
      properties.load(inputStream);
    }
    return properties;
  }

  private static Set<String> parseRuntimeDependencies(Path dependencyTreePath) throws IOException {
    Set<String> runtimeDependencies = new LinkedHashSet<>();
    List<String> lines = Files.readAllLines(dependencyTreePath);
    for (String line : lines) {
      Matcher matcher = RUNTIME_DEPENDENCY_PATTERN.matcher(line);
      if (matcher.find()) {
        runtimeDependencies.add(matcher.group(1));
      }
    }
    return runtimeDependencies;
  }

  private static Set<String> splitCsv(String csv) {
    if (csv.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }
}
