package dev.ayagmar.quarkusforge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetadataCompatibilityContextTest {
  @Test
  void validateReturnsMetadataLoadErrorWhenContextFailedToLoad() {
    MetadataCompatibilityContext context = MetadataCompatibilityContext.failure("snapshot missing");

    ValidationReport report = context.validate(validRequest());

    assertThat(report.errors())
        .extracting(ValidationError::field, ValidationError::message)
        .containsExactly(tuple("metadata", "snapshot missing"));
  }

  @Test
  void constructorNormalizesBlankLoadErrorWhenSnapshotIsPresent() {
    MetadataCompatibilityContext context =
        new MetadataCompatibilityContext(validMetadata(), "   ");

    ValidationReport report = context.validate(validRequest());

    assertThat(report.isValid()).isTrue();
  }

  @Test
  void constructorRejectsMissingSnapshotAndError() {
    assertThatThrownBy(() -> new MetadataCompatibilityContext(null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ProjectRequest validRequest() {
    return new ProjectRequest(
        "com.example",
        "forge-app",
        "1.0.0-SNAPSHOT",
        "com.example.forge.app",
        "./generated",
        "maven",
        "25");
  }

  private static MetadataDto validMetadata() {
    return new MetadataDto(
        List.of("21", "25"), List.of("maven"), Map.of("maven", List.of("21", "25")));
  }
}
