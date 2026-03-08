package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiFocusTargetsTest {

  @Test
  void orderedReturnsExpectedTraversalOrder() {
    assertThat(UiFocusTargets.ordered())
        .containsExactly(
            FocusTarget.GROUP_ID,
            FocusTarget.ARTIFACT_ID,
            FocusTarget.BUILD_TOOL,
            FocusTarget.PLATFORM_STREAM,
            FocusTarget.VERSION,
            FocusTarget.PACKAGE_NAME,
            FocusTarget.JAVA_VERSION,
            FocusTarget.OUTPUT_DIR,
            FocusTarget.EXTENSION_SEARCH,
            FocusTarget.EXTENSION_LIST,
            FocusTarget.SUBMIT);
  }

  @Test
  void moveWrapsForwardAndBackwardAcrossTraversalOrder() {
    assertThat(UiFocusTargets.move(FocusTarget.GROUP_ID, -1)).isEqualTo(FocusTarget.SUBMIT);
    assertThat(UiFocusTargets.move(FocusTarget.SUBMIT, 1)).isEqualTo(FocusTarget.GROUP_ID);
    assertThat(UiFocusTargets.move(FocusTarget.VERSION, 3)).isEqualTo(FocusTarget.OUTPUT_DIR);
  }

  @Test
  void nameAndDisplayNameCoverEveryTarget() {
    assertThat(FocusTarget.values())
        .allSatisfy(
            target -> {
              assertThat(UiFocusTargets.nameOf(target)).isNotBlank();
              assertThat(UiFocusTargets.displayNameOf(target)).isNotBlank();
            });
    assertThat(UiFocusTargets.nameOf(FocusTarget.OUTPUT_DIR)).isEqualTo("outputDirectory");
    assertThat(UiFocusTargets.displayNameOf(FocusTarget.OUTPUT_DIR)).isEqualTo("Output directory");
  }

  @Test
  void orderedViewIsImmutable() {
    assertThat(UiFocusTargets.ordered()).isEqualTo(List.copyOf(UiFocusTargets.ordered()));
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> UiFocusTargets.ordered().add(FocusTarget.GROUP_ID))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
