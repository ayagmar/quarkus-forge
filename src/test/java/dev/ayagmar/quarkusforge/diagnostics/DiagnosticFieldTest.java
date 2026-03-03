package dev.ayagmar.quarkusforge.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiagnosticFieldTest {

  @Test
  void validFieldStoresNameAndValue() {
    DiagnosticField field = new DiagnosticField("action", "open-ide");

    assertThat(field.name()).isEqualTo("action");
    assertThat(field.value()).isEqualTo("open-ide");
  }

  @Test
  void ofFactoryCreatesEquivalentField() {
    DiagnosticField field = DiagnosticField.of("count", 42);

    assertThat(field.name()).isEqualTo("count");
    assertThat(field.value()).isEqualTo(42);
  }

  @Test
  void nullValueIsAllowed() {
    DiagnosticField field = DiagnosticField.of("nullable", null);

    assertThat(field.name()).isEqualTo("nullable");
    assertThat(field.value()).isNull();
  }

  @Test
  void nullNameThrowsNullPointerException() {
    assertThatThrownBy(() -> new DiagnosticField(null, "value"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void blankNameThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new DiagnosticField("  ", "value"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @Test
  void emptyNameThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new DiagnosticField("", "value"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }
}
