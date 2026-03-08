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
  void currentUiStateFieldsAreAllReducerOwned() {
    assertThat(UiStateOwnership.entries())
        .allMatch(entry -> entry.category() == UiStateOwnership.Category.REDUCER_OWNED);
  }
}
