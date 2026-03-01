package dev.ayagmar.quarkusforge;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;
import picocli.CommandLine;

final class CliVersionProvider implements CommandLine.IVersionProvider {
  private static final String VERSION_RESOURCE = "version.properties";
  private static final String VERSION_KEY = "version";
  private static final String UNKNOWN_VERSION = "unknown";

  @Override
  public String[] getVersion() {
    return new String[] {resolveVersion()};
  }

  static String resolveVersion() {
    String implementationVersion = QuarkusForgeCli.class.getPackage().getImplementationVersion();
    if (isUsableVersion(implementationVersion)) {
      return implementationVersion.strip();
    }
    String resourceVersion = readVersionFromResource();
    if (isUsableVersion(resourceVersion)) {
      return resourceVersion.strip();
    }
    return UNKNOWN_VERSION;
  }

  private static String readVersionFromResource() {
    try (InputStream inputStream =
        CliVersionProvider.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
      if (inputStream == null) {
        return null;
      }
      Properties properties = new Properties();
      properties.load(inputStream);
      return properties.getProperty(VERSION_KEY);
    } catch (IOException ignored) {
      return null;
    }
  }

  private static boolean isUsableVersion(String value) {
    return value != null && !value.isBlank() && !Objects.equals(value.strip(), UNKNOWN_VERSION);
  }
}
