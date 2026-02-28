package dev.ayagmar.quarkusforge.api;

import java.util.List;

public record PlatformStream(
    String key, String platformVersion, boolean recommended, List<String> javaVersions) {
  public PlatformStream {
    key = MetadataDto.normalizeText(key);
    if (key.isBlank()) {
      throw new IllegalArgumentException("platform stream key must not be blank");
    }
    platformVersion = MetadataDto.normalizeText(platformVersion);
    if (platformVersion.isBlank()) {
      platformVersion = derivePlatformVersion(key);
    }
    javaVersions = MetadataDto.copyNormalized(javaVersions == null ? List.of() : javaVersions);
  }

  private static String derivePlatformVersion(String key) {
    int delimiterIndex = key.lastIndexOf(':');
    if (delimiterIndex < 0 || delimiterIndex == key.length() - 1) {
      return key;
    }
    return key.substring(delimiterIndex + 1);
  }
}
