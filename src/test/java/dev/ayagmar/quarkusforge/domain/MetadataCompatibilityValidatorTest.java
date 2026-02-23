package dev.ayagmar.quarkusforge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataCompatibilityValidatorTest {
  private final MetadataCompatibilityValidator validator = new MetadataCompatibilityValidator();

  @Test
  void acceptsSupportedCombination() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", ".", "maven", "25");
    MetadataDto metadata =
        new MetadataDto(
            List.of("17", "21", "25"),
            List.of("maven", "gradle"),
            Map.of("maven", List.of("17", "21", "25"), "gradle", List.of("21", "25")));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void rejectsUnsupportedCombinationWithActionableMessage() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", ".", "gradle", "17");
    MetadataDto metadata =
        new MetadataDto(
            List.of("17", "21", "25"),
            List.of("maven", "gradle"),
            Map.of("maven", List.of("17", "21", "25"), "gradle", List.of("21", "25")));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.errors()).extracting(ValidationError::field).contains("compatibility");
    assertThat(report.errors().getFirst().message()).contains("does not support Java 17");
  }

  @Test
  void detectsContractMismatchWhenCompatibilityEntryIsMissing() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", ".", "gradle", "21");
    MetadataDto metadata =
        new MetadataDto(
            List.of("17", "21", "25"),
            List.of("maven", "gradle"),
            Map.of("maven", List.of("17", "21", "25")));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.errors()).extracting(ValidationError::field).contains("metadata");
  }

  @Test
  void acceptsSupportedCombinationWhenMetadataUsesDifferentBuildToolCasing() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", ".", "maven", "25");
    MetadataDto metadata =
        new MetadataDto(
            List.of("17", "21", "25"),
            List.of("Maven", "Gradle"),
            Map.of("MAVEN", List.of("17", "21", "25"), "GRADLE", List.of("21", "25")));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void rejectsWhenCompatibilityMatrixIsMissing() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", ".", "maven", "25");
    MetadataDto metadata = new MetadataDto(List.of("17", "21", "25"), List.of("maven"), Map.of());

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.errors()).extracting(ValidationError::field).containsExactly("metadata");
  }

  @Test
  void acceptsSupportedCombinationWhenMetadataContainsTrimPadding() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", ".", "maven", "25");
    MetadataDto metadata =
        new MetadataDto(
            List.of("21", "25"),
            List.of(" Maven ", " Gradle "),
            Map.of(" maven ", List.of("21", "25"), " gradle ", List.of("25")));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void rejectsUnknownPlatformStreamWhenMetadataProvidesPlatformOptions() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0",
            "com.example.forge",
            ".",
            "io.quarkus.platform:9.99",
            "maven",
            "25");
    MetadataDto metadata =
        new MetadataDto(
            List.of("21", "25"),
            List.of("maven"),
            Map.of("maven", List.of("21", "25")),
            List.of(
                new MetadataDto.PlatformStream(
                    "io.quarkus.platform:3.31", "3.31", true, List.of("21", "25"))));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.errors()).extracting(ValidationError::field).contains("platformStream");
  }

  @Test
  void rejectsPlatformAndJavaCombinationWhenStreamDoesNotSupportJavaVersion() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0",
            "com.example.forge",
            ".",
            "io.quarkus.platform:3.20",
            "maven",
            "25");
    MetadataDto metadata =
        new MetadataDto(
            List.of("17", "21", "25"),
            List.of("maven"),
            Map.of("maven", List.of("17", "21", "25")),
            List.of(
                new MetadataDto.PlatformStream(
                    "io.quarkus.platform:3.31", "3.31", true, List.of("17", "21", "25")),
                new MetadataDto.PlatformStream(
                    "io.quarkus.platform:3.20", "3.20", false, List.of("17", "21"))));

    ValidationReport report = validator.validate(request, metadata);

    assertThat(report.errors()).extracting(ValidationError::field).contains("compatibility");
    assertThat(report.errors().getFirst().message()).contains("platform stream");
  }
}
