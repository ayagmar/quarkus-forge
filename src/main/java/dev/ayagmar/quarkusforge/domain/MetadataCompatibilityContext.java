package dev.ayagmar.quarkusforge.domain;

import dev.ayagmar.quarkusforge.api.ApiContractException;
import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import java.util.List;

public record MetadataCompatibilityContext(MetadataDto metadataSnapshot, String loadError) {
  private static final MetadataCompatibilityValidator VALIDATOR =
      new MetadataCompatibilityValidator();

  public MetadataCompatibilityContext {
    if (loadError != null && loadError.isBlank()) {
      loadError = null;
    }
    boolean hasSnapshot = metadataSnapshot != null;
    boolean hasError = loadError != null;
    if (hasSnapshot == hasError) {
      throw new IllegalArgumentException(
          "MetadataCompatibilityContext requires exactly one of snapshot or loadError");
    }
  }

  public static MetadataCompatibilityContext success(MetadataDto metadataSnapshot) {
    return new MetadataCompatibilityContext(metadataSnapshot, null);
  }

  public static MetadataCompatibilityContext failure(String loadError) {
    return new MetadataCompatibilityContext(null, loadError);
  }

  public static MetadataCompatibilityContext loadDefault() {
    try {
      return success(MetadataSnapshotLoader.loadDefault());
    } catch (ApiContractException contractException) {
      return failure(contractException.getMessage());
    }
  }

  public ValidationReport validate(ProjectRequest request) {
    if (loadError != null) {
      return new ValidationReport(List.of(new ValidationError("metadata", loadError)));
    }
    return VALIDATOR.validate(request, metadataSnapshot);
  }
}
