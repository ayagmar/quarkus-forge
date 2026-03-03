package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogLoadStateTest {

  @Test
  void loadingWithNullPreviousReturnsEmptySourceAndNotStale() {
    CatalogLoadState state = CatalogLoadState.loading();

    assertThat(state.isLoading()).isTrue();
    assertThat(state.errorMessage()).isEmpty();
    assertThat(state.sourceLabel()).isEmpty();
    assertThat(state.isStale()).isFalse();
  }

  @Test
  void loadingFromPreviousLoadedRetainsSourceLabelAndStale() {
    CatalogLoadState previous = CatalogLoadState.loaded("api", true);
    CatalogLoadState loading = CatalogLoadState.loadingFrom(previous);

    assertThat(loading.isLoading()).isTrue();
    assertThat(loading.sourceLabel()).isEqualTo("api");
    assertThat(loading.isStale()).isTrue();
  }

  @Test
  void loadingFromPreviousNonStaleRetainsNonStale() {
    CatalogLoadState previous = CatalogLoadState.loaded("cache", false);
    CatalogLoadState loading = CatalogLoadState.loadingFrom(previous);

    assertThat(loading.isLoading()).isTrue();
    assertThat(loading.sourceLabel()).isEqualTo("cache");
    assertThat(loading.isStale()).isFalse();
  }

  @Test
  void loadedStateHasCorrectProperties() {
    CatalogLoadState state = CatalogLoadState.loaded("live", false);

    assertThat(state.isLoading()).isFalse();
    assertThat(state.errorMessage()).isEmpty();
    assertThat(state.sourceLabel()).isEqualTo("live");
    assertThat(state.isStale()).isFalse();
  }

  @Test
  void loadedStaleStateReportsStale() {
    CatalogLoadState state = CatalogLoadState.loaded("cache", true);

    assertThat(state.isStale()).isTrue();
  }

  @Test
  void failedStateHasErrorMessage() {
    CatalogLoadState state = CatalogLoadState.failed("Connection refused");

    assertThat(state.isLoading()).isFalse();
    assertThat(state.errorMessage()).isEqualTo("Connection refused");
    assertThat(state.sourceLabel()).isEmpty();
    assertThat(state.isStale()).isFalse();
  }

  @Test
  void initialStateIsSnapshotNotStaleNotLoading() {
    CatalogLoadState state = CatalogLoadState.initial();

    assertThat(state.isLoading()).isFalse();
    assertThat(state.sourceLabel()).isEqualTo("snapshot");
    assertThat(state.isStale()).isFalse();
    assertThat(state.errorMessage()).isEmpty();
  }

  @Test
  void loadingFromFailedPreviousPreservesFailedSourceLabel() {
    CatalogLoadState failed = CatalogLoadState.failed("error");
    CatalogLoadState loading = CatalogLoadState.loadingFrom(failed);

    assertThat(loading.isLoading()).isTrue();
    assertThat(loading.sourceLabel()).isEmpty();
    assertThat(loading.isStale()).isFalse();
  }
}
