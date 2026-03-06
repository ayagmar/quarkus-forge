package dev.ayagmar.quarkusforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.MetadataSnapshotLoader;
import dev.ayagmar.quarkusforge.application.StartupMetadataSelection;
import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import org.junit.jupiter.api.Test;

class StartupMetadataSelectionTest {

  @Test
  void validConstructionPreservesValues() {
    MetadataDto metadata = MetadataSnapshotLoader.loadDefault();
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(metadata);
    StartupMetadataSelection selection = new StartupMetadataSelection(ctx, "live", "loaded ok");

    assertThat(selection.metadataCompatibility()).isSameAs(ctx);
    assertThat(selection.sourceLabel()).isEqualTo("live");
    assertThat(selection.detailMessage()).isEqualTo("loaded ok");
  }

  @Test
  void nullSourceLabelDefaultsToEmpty() {
    MetadataDto metadata = MetadataSnapshotLoader.loadDefault();
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(metadata);
    StartupMetadataSelection selection = new StartupMetadataSelection(ctx, null, "detail");

    assertThat(selection.sourceLabel()).isEmpty();
  }

  @Test
  void nullDetailMessageDefaultsToEmpty() {
    MetadataDto metadata = MetadataSnapshotLoader.loadDefault();
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(metadata);
    StartupMetadataSelection selection = new StartupMetadataSelection(ctx, "cache", null);

    assertThat(selection.detailMessage()).isEmpty();
  }

  @Test
  void sourceLabelIsStripped() {
    MetadataDto metadata = MetadataSnapshotLoader.loadDefault();
    MetadataCompatibilityContext ctx = MetadataCompatibilityContext.success(metadata);
    StartupMetadataSelection selection = new StartupMetadataSelection(ctx, "  live  ", "detail");

    assertThat(selection.sourceLabel()).isEqualTo("live");
  }

  @Test
  void nullMetadataCompatibilityThrowsNPE() {
    assertThatThrownBy(() -> new StartupMetadataSelection(null, "live", "detail"))
        .isInstanceOf(NullPointerException.class);
  }
}
