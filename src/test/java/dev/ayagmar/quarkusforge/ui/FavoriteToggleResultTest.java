package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FavoriteToggleResultTest {

  @Test
  void noneFactoryReturnsUnchangedResult() {
    FavoriteToggleResult result = FavoriteToggleResult.none();

    assertThat(result.changed()).isFalse();
    assertThat(result.extensionName()).isEmpty();
    assertThat(result.favoriteNow()).isFalse();
  }

  @Test
  void constructedResultPreservesValues() {
    FavoriteToggleResult result = new FavoriteToggleResult(true, "quarkus-rest", true);

    assertThat(result.changed()).isTrue();
    assertThat(result.extensionName()).isEqualTo("quarkus-rest");
    assertThat(result.favoriteNow()).isTrue();
  }

  @Test
  void unfavoriteResultReportsNotFavoriteNow() {
    FavoriteToggleResult result = new FavoriteToggleResult(true, "quarkus-arc", false);

    assertThat(result.changed()).isTrue();
    assertThat(result.extensionName()).isEqualTo("quarkus-arc");
    assertThat(result.favoriteNow()).isFalse();
  }
}
