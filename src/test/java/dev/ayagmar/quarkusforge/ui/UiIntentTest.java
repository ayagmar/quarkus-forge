package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.domain.ValidationError;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiIntentTest {

  @Test
  void submitEvaluationFactoryBuildsReducerInputFromCurrentValidation() {
    ValidationReport validation =
        new ValidationReport(
            List.of(
                new ValidationError("unknownField", "ignored for focus"),
                new ValidationError("artifactId", "must be lowercase"),
                new ValidationError("groupId", "must not be blank")));

    UiIntent.SubmitEvaluation evaluation =
        UiIntent.SubmitEvaluation.from(
            true, 3, validation, "Output directory already exists: /tmp/demo");

    assertThat(evaluation.generationConfigured()).isTrue();
    assertThat(evaluation.selectedExtensionCount()).isEqualTo(3);
    assertThat(evaluation.firstInvalidTarget()).isEqualTo(FocusTarget.ARTIFACT_ID);
    assertThat(evaluation.validationIssueCount()).isEqualTo(3);
    assertThat(evaluation.firstValidationError()).isEqualTo("unknownField: ignored for focus");
    assertThat(evaluation.targetConflictErrorMessage())
        .isEqualTo("Output directory already exists: /tmp/demo");
  }

  @Test
  void submitRecoveryFactoryReflectsCurrentValidationAndConflictState() {
    ValidationReport validation =
        new ValidationReport(List.of(new ValidationError("artifactId", "must be lowercase")));

    UiIntent.SubmitEditRecovery recovery =
        UiIntent.SubmitEditRecovery.from(
            validation, true, true, "Output directory already exists: /tmp/demo");

    assertThat(recovery.submitBlockedByValidation()).isTrue();
    assertThat(recovery.validationValid()).isFalse();
    assertThat(recovery.firstValidationError()).isEqualTo("artifactId: must be lowercase");
    assertThat(recovery.submitBlockedByTargetConflict()).isTrue();
    assertThat(recovery.targetConflictErrorMessage())
        .isEqualTo("Output directory already exists: /tmp/demo");
  }
}
