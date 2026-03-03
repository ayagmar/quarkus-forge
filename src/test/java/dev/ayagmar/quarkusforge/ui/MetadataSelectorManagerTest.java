package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ayagmar.quarkusforge.api.MetadataDto;
import dev.ayagmar.quarkusforge.api.PlatformStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MetadataSelectorManagerTest {
  private MetadataSelectorManager manager;

  @BeforeEach
  void setUp() {
    manager = new MetadataSelectorManager();
  }

  static MetadataDto testMetadata() {
    return new MetadataDto(
        List.of("21", "25"),
        List.of("maven", "gradle", "gradle-kotlin-dsl"),
        Map.of(),
        List.of(
            new PlatformStream("io.quarkus.platform:3.20", "3.20", true, List.of("21", "25")),
            new PlatformStream("io.quarkus.platform:3.19", "3.19", false, List.of("21"))));
  }

  @Nested
  class Sync {
    @Test
    void populatesOptionsFromMetadata() {
      MetadataDto metadata = testMetadata();
      MetadataSelectorManager.ResolvedSelections result = manager.sync(metadata, "", "maven", "21");

      assertThat(manager.availableBuildTools())
          .containsExactly("maven", "gradle", "gradle-kotlin-dsl");
      assertThat(manager.availableJavaVersions()).containsExactly("21", "25");
      assertThat(manager.availablePlatformStreams()).hasSize(2);
    }

    @Test
    void resolvesToRecommendedStreamWhenCurrentIsEmpty() {
      MetadataDto metadata = testMetadata();
      MetadataSelectorManager.ResolvedSelections result = manager.sync(metadata, "", "maven", "21");

      assertThat(result.platformStream()).isEqualTo("io.quarkus.platform:3.20");
    }

    @Test
    void preservesCurrentSelectionIfValid() {
      MetadataDto metadata = testMetadata();
      MetadataSelectorManager.ResolvedSelections result =
          manager.sync(metadata, "io.quarkus.platform:3.19", "gradle", "21");

      assertThat(result.platformStream()).isEqualTo("io.quarkus.platform:3.19");
      assertThat(result.buildTool()).isEqualTo("gradle");
      assertThat(result.javaVersion()).isEqualTo("21");
    }

    @Test
    void normalizesUnknownBuildToolToFirstOption() {
      MetadataDto metadata = testMetadata();
      MetadataSelectorManager.ResolvedSelections result =
          manager.sync(metadata, "", "unknown-tool", "21");

      assertThat(result.buildTool()).isEqualTo("maven");
    }

    @Test
    void handlesNullMetadata() {
      MetadataSelectorManager.ResolvedSelections result =
          manager.sync(null, "stream", "maven", "21");

      assertThat(manager.availableBuildTools()).containsExactly("maven");
      assertThat(manager.availableJavaVersions()).containsExactly("21");
      assertThat(result.platformStream()).isEqualTo("stream");
    }
  }

  @Nested
  class Cycling {
    @BeforeEach
    void syncOptions() {
      manager.sync(testMetadata(), "io.quarkus.platform:3.20", "maven", "21");
    }

    @Test
    void cycleForwardWraps() {
      String result = manager.cycle(FocusTarget.BUILD_TOOL, "gradle-kotlin-dsl", 1);
      assertThat(result).isEqualTo("maven");
    }

    @Test
    void cycleBackwardWraps() {
      String result = manager.cycle(FocusTarget.BUILD_TOOL, "maven", -1);
      assertThat(result).isEqualTo("gradle-kotlin-dsl");
    }

    @Test
    void cycleReturnsNullForNonSelectorTarget() {
      String result = manager.cycle(FocusTarget.GROUP_ID, "anything", 1);
      assertThat(result).isNull();
    }
  }

  @Nested
  class SelectEdge {
    @BeforeEach
    void syncOptions() {
      manager.sync(testMetadata(), "", "maven", "21");
    }

    @Test
    void selectFirstReturnsFirstOption() {
      String result = manager.selectEdge(FocusTarget.JAVA_VERSION, true);
      assertThat(result).isEqualTo("21");
    }

    @Test
    void selectLastReturnsLastOption() {
      String result = manager.selectEdge(FocusTarget.JAVA_VERSION, false);
      assertThat(result).isEqualTo("25");
    }

    @Test
    void returnsNullForNonSelectorTarget() {
      String result = manager.selectEdge(FocusTarget.ARTIFACT_ID, true);
      assertThat(result).isNull();
    }
  }

  @Test
  void isSelectorFocusCorrect() {
    assertThat(MetadataSelectorManager.isSelectorFocus(FocusTarget.PLATFORM_STREAM)).isTrue();
    assertThat(MetadataSelectorManager.isSelectorFocus(FocusTarget.BUILD_TOOL)).isTrue();
    assertThat(MetadataSelectorManager.isSelectorFocus(FocusTarget.JAVA_VERSION)).isTrue();
    assertThat(MetadataSelectorManager.isSelectorFocus(FocusTarget.GROUP_ID)).isFalse();
    assertThat(MetadataSelectorManager.isSelectorFocus(FocusTarget.EXTENSION_LIST)).isFalse();
  }
}
