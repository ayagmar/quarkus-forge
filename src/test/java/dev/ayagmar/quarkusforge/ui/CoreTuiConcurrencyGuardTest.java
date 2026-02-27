package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class CoreTuiConcurrencyGuardTest {
  @Test
  void generationCancellationVisibilityFieldsRemainVolatile() throws Exception {
    assertThat(isVolatileField(GenerationFlowCoordinator.class, "generationCancelRequested"))
        .isTrue();
    assertThat(isVolatileField(GenerationFlowCoordinator.class, "generationToken")).isTrue();
    assertThat(isVolatileField(CoreTuiController.class, "extensionCatalogLoadToken")).isTrue();
  }

  private static boolean isVolatileField(Class<?> owner, String fieldName)
      throws NoSuchFieldException {
    Field field = owner.getDeclaredField(fieldName);
    return Modifier.isVolatile(field.getModifiers());
  }
}
