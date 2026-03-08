package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiStateOwnershipTest {

  @Test
  void classifiesEveryUiStateRecordComponentExactlyOnce() {
    List<String> classifiedFields =
        UiStateOwnership.entries().stream().map(UiStateOwnership.Entry::fieldName).toList();
    List<String> uiStateFields =
        List.of(UiState.class.getRecordComponents()).stream()
            .map(RecordComponent::getName)
            .toList();

    assertThat(classifiedFields).containsExactlyElementsOf(uiStateFields);
    assertThat(classifiedFields).doesNotHaveDuplicates();
  }

  @Test
  void renderOnlyOwnershipIsExplicitForDerivedViewSlices() {
    assertThat(entry("metadataPanel").category()).isEqualTo(UiStateOwnership.Category.RENDER_ONLY);
    assertThat(entry("extensionsPanel").category())
        .isEqualTo(UiStateOwnership.Category.RENDER_ONLY);
    assertThat(entry("footer").category()).isEqualTo(UiStateOwnership.Category.RENDER_ONLY);
    assertThat(entry("generation").category()).isEqualTo(UiStateOwnership.Category.RENDER_ONLY);
    assertThat(entry("startupOverlay").category()).isEqualTo(UiStateOwnership.Category.RENDER_ONLY);
  }

  private static UiStateOwnership.Entry entry(String fieldName) {
    return UiStateOwnership.entries().stream()
        .filter(entry -> entry.fieldName().equals(fieldName))
        .findFirst()
        .orElseThrow();
  }
}
