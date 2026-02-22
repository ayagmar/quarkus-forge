package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class CoreTuiConcurrencyGuardTest {
  @Test
  void generationCancellationVisibilityFieldsRemainVolatile() throws Exception {
    assertThat(isVolatileField("generationCancelRequested")).isTrue();
    assertThat(isVolatileField("generationToken")).isTrue();
    assertThat(isVolatileField("extensionCatalogLoadToken")).isTrue();
  }

  private static boolean isVolatileField(String fieldName) throws NoSuchFieldException {
    Field field = CoreTuiController.class.getDeclaredField(fieldName);
    return Modifier.isVolatile(field.getModifiers());
  }
}
