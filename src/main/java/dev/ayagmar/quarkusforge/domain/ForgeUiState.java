package dev.ayagmar.quarkusforge.domain;

import java.util.Objects;

public record ForgeUiState(
    ProjectRequest request,
    ValidationReport validation,
    MetadataCompatibilityContext metadataCompatibility) {
  public ForgeUiState {
    Objects.requireNonNull(request);
    Objects.requireNonNull(validation);
    Objects.requireNonNull(metadataCompatibility);
  }

  public boolean canSubmit() {
    return validation.isValid();
  }
}
