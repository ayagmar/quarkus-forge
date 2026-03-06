package dev.ayagmar.quarkusforge.domain;

public final class ProjectInputDefaults {
  public static final String GROUP_ID = "org.acme";
  public static final String ARTIFACT_ID = "quarkus-app";
  public static final String VERSION = "1.0.0-SNAPSHOT";
  public static final String OUTPUT_DIRECTORY = ".";
  public static final String PLATFORM_STREAM = "";
  public static final String BUILD_TOOL = "maven";
  public static final String JAVA_VERSION = "25";

  private ProjectInputDefaults() {}
}
