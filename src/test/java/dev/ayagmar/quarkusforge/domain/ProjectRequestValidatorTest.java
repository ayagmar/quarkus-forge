package dev.ayagmar.quarkusforge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProjectRequestValidatorTest {
  private final ProjectRequestValidator validator = new ProjectRequestValidator();

  @Test
  void acceptsValidRequest() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0-SNAPSHOT",
            "com.example.forge.app",
            "./out",
            "maven",
            "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void rejectsInvalidGroupArtifactPackageAndMetadataFields() {
    ProjectRequest request =
        new ProjectRequest("1example", "-bad", "1.0.0", "invalid-package", "./out", "", "abc");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors())
        .extracting(ValidationError::field)
        .contains("groupId", "artifactId", "packageName", "buildTool", "javaVersion");
  }

  @Test
  void rejectsInvalidVersion() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "??", "com.example.forge", "./out", "maven", "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors()).extracting(ValidationError::field).contains("version");
  }

  @Test
  void acceptsVersionWithBuildMetadataSuffix() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0+build.1",
            "com.example.forge",
            "./out",
            "maven",
            "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors()).extracting(ValidationError::field).doesNotContain("version");
  }

  @Test
  void acceptsArtifactIdWithUnderscore() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge_app", "1.0.0", "com.example.forge_app", "./out", "maven", "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors()).extracting(ValidationError::field).doesNotContain("artifactId");
  }

  @Test
  void rejectsWindowsReservedOutputSegment() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", "tmp/CON", "maven", "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors()).extracting(ValidationError::field).contains("outputDirectory");
  }

  @Test
  void rejectsWindowsInvalidOutputCharacters() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", "tmp/te|st", "maven", "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors()).extracting(ValidationError::field).contains("outputDirectory");
  }

  @Test
  void allowsRelativeParentSegmentsInOutputDirectoryByPolicy() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example",
            "forge-app",
            "1.0.0",
            "com.example.forge",
            "../tmp/./forge-out",
            "maven",
            "25");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors())
        .extracting(ValidationError::field)
        .doesNotContain("outputDirectory");
  }

  @Test
  void derivesPackageNameWhenNotProvided() {
    CliPrefill prefill =
        new CliPrefill("Com.Example", "my-app", "1.0.0", "", "./tmp", "maven", "25");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.packageName()).isEqualTo("com.example.my.app");
  }

  @Test
  void derivesPackageNameFromNumericArtifactUsingSafePrefix() {
    CliPrefill prefill =
        new CliPrefill("Com.Example", "123app", "1.0.0", "", "./tmp", "maven", "25");

    ProjectRequest request = CliPrefillMapper.map(prefill);

    assertThat(request.packageName()).isEqualTo("com.example.x123app");
    assertThat(validator.validate(request).errors())
        .extracting(ValidationError::field)
        .contains("artifactId");
  }

  @Test
  void acceptsNumericJavaFeatureVersionWithoutTwoDigitConstraint() {
    ProjectRequest request =
        new ProjectRequest(
            "com.example", "forge-app", "1.0.0", "com.example.forge", "./out", "maven", "8");

    ValidationReport report = validator.validate(request);

    assertThat(report.errors()).extracting(ValidationError::field).doesNotContain("javaVersion");
  }
}
