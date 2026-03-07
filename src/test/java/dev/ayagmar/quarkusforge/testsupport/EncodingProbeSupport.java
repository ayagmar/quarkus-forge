package dev.ayagmar.quarkusforge.testsupport;

import dev.ayagmar.quarkusforge.api.EncodingProbeMain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class EncodingProbeSupport {
  private EncodingProbeSupport() {}

  public static String probe(String mode, Path path) throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(
                javaExecutable(),
                "-Dfile.encoding=US-ASCII",
                "-cp",
                testClassPath(),
                EncodingProbeMain.class.getName(),
                mode,
                path.toString())
            .start();
    int exitCode = process.waitFor();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    if (exitCode != 0) {
      throw new IOException("Encoding probe failed (" + exitCode + "): " + stderr);
    }
    return stdout;
  }

  private static String javaExecutable() {
    Path javaHome = Path.of(System.getProperty("java.home"));
    Path bin = javaHome.resolve("bin").resolve("java");
    if (bin.toFile().exists()) {
      return bin.toString();
    }
    return javaHome.resolve("bin").resolve("java.exe").toString();
  }

  private static String testClassPath() {
    String testClasspath = System.getProperty("surefire.test.class.path");
    if (testClasspath != null && !testClasspath.isBlank()) {
      return testClasspath;
    }
    String realClasspath = System.getProperty("surefire.real.class.path");
    if (realClasspath != null && !realClasspath.isBlank()) {
      return realClasspath;
    }
    return System.getProperty("java.class.path");
  }
}
