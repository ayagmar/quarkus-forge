package dev.ayagmar.quarkusforge.domain;

public record ForgeUiState(ProjectRequest request, ValidationReport validation) {
  public boolean canSubmit() {
    return validation.isValid();
  }
}
