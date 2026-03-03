package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GenerationProgressUpdateTest {

  @Test
  void requestingArchiveFactoryCreatesCorrectStep() {
    GenerationProgressUpdate update =
        GenerationProgressUpdate.requestingArchive("Downloading archive");

    assertThat(update.step()).isEqualTo(GenerationProgressStep.REQUESTING_ARCHIVE);
    assertThat(update.message()).isEqualTo("Downloading archive");
  }

  @Test
  void extractingArchiveFactoryCreatesCorrectStep() {
    GenerationProgressUpdate update =
        GenerationProgressUpdate.extractingArchive("Extracting to /tmp");

    assertThat(update.step()).isEqualTo(GenerationProgressStep.EXTRACTING_ARCHIVE);
    assertThat(update.message()).isEqualTo("Extracting to /tmp");
  }

  @Test
  void finalizingFactoryCreatesCorrectStep() {
    GenerationProgressUpdate update = GenerationProgressUpdate.finalizing("Done");

    assertThat(update.step()).isEqualTo(GenerationProgressStep.FINALIZING);
    assertThat(update.message()).isEqualTo("Done");
  }

  @Test
  void nullMessageNormalizesToEmpty() {
    GenerationProgressUpdate update = GenerationProgressUpdate.requestingArchive(null);

    assertThat(update.message()).isEmpty();
  }

  @Test
  void whitespaceMessageIsStripped() {
    GenerationProgressUpdate update = GenerationProgressUpdate.extractingArchive("  padded  ");

    assertThat(update.message()).isEqualTo("padded");
  }

  @Test
  void nullStepThrowsNullPointerException() {
    assertThatThrownBy(() -> new GenerationProgressUpdate(null, "message"))
        .isInstanceOf(NullPointerException.class);
  }
}
