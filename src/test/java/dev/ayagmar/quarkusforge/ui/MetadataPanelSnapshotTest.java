package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MetadataPanelSnapshotTest {

  @Test
  void nullStringFieldsDefaultToEmpty() {
    MetadataPanelSnapshot snapshot =
        new MetadataPanelSnapshot(
            "Config", false, false, null, null, null, null, null, null, null, null, null, null,
            null);

    assertThat(snapshot.groupId()).isEmpty();
    assertThat(snapshot.artifactId()).isEmpty();
    assertThat(snapshot.version()).isEmpty();
    assertThat(snapshot.packageName()).isEmpty();
    assertThat(snapshot.outputDir()).isEmpty();
    assertThat(snapshot.platformStream()).isEmpty();
    assertThat(snapshot.buildTool()).isEmpty();
    assertThat(snapshot.javaVersion()).isEmpty();
  }

  @Test
  void nullSelectorInfoDefaultsToEmpty() {
    MetadataPanelSnapshot snapshot =
        new MetadataPanelSnapshot(
            "Config", true, false, "org.acme", "demo", "1.0", "org.acme", ".", "3.31", "maven",
            "25", null, null, null);

    assertThat(snapshot.platformStreamInfo()).isEqualTo(MetadataPanelSnapshot.SelectorInfo.EMPTY);
    assertThat(snapshot.buildToolInfo()).isEqualTo(MetadataPanelSnapshot.SelectorInfo.EMPTY);
    assertThat(snapshot.javaVersionInfo()).isEqualTo(MetadataPanelSnapshot.SelectorInfo.EMPTY);
  }

  @Test
  void nonNullFieldsArePreserved() {
    var selectorInfo = new MetadataPanelSnapshot.SelectorInfo(2, 5);
    MetadataPanelSnapshot snapshot =
        new MetadataPanelSnapshot(
            "Config", false, true, "org.acme", "demo", "1.0.0", "org.acme.demo", "./out",
            "io.quarkus:3.31", "gradle", "21", selectorInfo, selectorInfo, selectorInfo);

    assertThat(snapshot.title()).isEqualTo("Config");
    assertThat(snapshot.focused()).isFalse();
    assertThat(snapshot.invalid()).isTrue();
    assertThat(snapshot.groupId()).isEqualTo("org.acme");
    assertThat(snapshot.platformStreamInfo().selectedIndex()).isEqualTo(2);
    assertThat(snapshot.platformStreamInfo().totalOptions()).isEqualTo(5);
  }

  @Test
  void selectorInfoEmptyHasZeroValues() {
    MetadataPanelSnapshot.SelectorInfo empty = MetadataPanelSnapshot.SelectorInfo.EMPTY;

    assertThat(empty.selectedIndex()).isZero();
    assertThat(empty.totalOptions()).isZero();
  }
}
