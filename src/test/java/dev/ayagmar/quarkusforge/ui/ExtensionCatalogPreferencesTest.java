package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ayagmar.quarkusforge.api.ExtensionFavoritesStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionCatalogPreferencesTest {
  @Test
  void retainsOnlyAvailableEntriesAndPersistsChanges() {
    RecordingFavoritesStore store =
        new RecordingFavoritesStore(Set.of("rest", "jdbc"), List.of("rest", "jdbc", "other"));
    ExtensionCatalogPreferences preferences = new ExtensionCatalogPreferences(store, Runnable::run);

    preferences.retainAvailable(Set.of("jdbc"));

    assertThat(preferences.favoriteIdsView()).containsExactly("jdbc");
    assertThat(preferences.recentIdsView()).containsExactly("jdbc");
    assertThat(store.savedFavorites).containsExactly("jdbc");
    assertThat(store.savedRecents).containsExactly("jdbc");
  }

  @Test
  void toggleFavoriteAndRecentSelectionUpdateState() {
    RecordingFavoritesStore store = new RecordingFavoritesStore(Set.of(), List.of("old"));
    ExtensionCatalogPreferences preferences = new ExtensionCatalogPreferences(store, Runnable::run);

    assertThat(preferences.toggleFavorite("rest")).isTrue();
    assertThat(preferences.isFavorite("rest")).isTrue();
    assertThat(preferences.favoriteExtensionCount()).isEqualTo(1);

    preferences.recordRecentSelection("rest");
    preferences.recordRecentSelection("old");

    assertThat(preferences.recentIdsView()).containsExactly("old", "rest");

    assertThat(preferences.toggleFavorite("rest")).isFalse();
    assertThat(preferences.favoriteIdsView()).isEmpty();
  }

  @Test
  void exposedViewsAreReadOnly() {
    ExtensionCatalogPreferences preferences =
        new ExtensionCatalogPreferences(
            new RecordingFavoritesStore(Set.of("rest"), List.of("rest")), Runnable::run);

    assertThatThrownBy(() -> preferences.favoriteIdsView().remove("rest"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> preferences.recentIdsView().add("jdbc"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void persistenceChainRecoversAfterPreviousFailure() {
    RecordingFavoritesStore store = new RecordingFavoritesStore(Set.of(), List.of());
    store.failFirstSave = true;
    ExtensionCatalogPreferences preferences = new ExtensionCatalogPreferences(store, Runnable::run);

    preferences.toggleFavorite("rest");
    preferences.recordRecentSelection("rest");

    assertThat(store.saveCalls).isEqualTo(2);
    assertThat(store.savedFavorites).containsExactly("rest");
    assertThat(store.savedRecents).containsExactly("rest");
  }

  private static final class RecordingFavoritesStore implements ExtensionFavoritesStore {
    private final Set<String> loadedFavorites;
    private final List<String> loadedRecents;
    private Set<String> savedFavorites = Set.of();
    private List<String> savedRecents = List.of();
    private int saveCalls;
    private boolean failFirstSave;

    private RecordingFavoritesStore(Set<String> loadedFavorites, List<String> loadedRecents) {
      this.loadedFavorites = new LinkedHashSet<>(loadedFavorites);
      this.loadedRecents = new ArrayList<>(loadedRecents);
    }

    @Override
    public Set<String> loadFavoriteExtensionIds() {
      return loadedFavorites;
    }

    @Override
    public List<String> loadRecentExtensionIds() {
      return loadedRecents;
    }

    @Override
    public void saveAll(Set<String> favoriteExtensionIds, List<String> recentExtensionIds) {
      saveCalls++;
      if (failFirstSave) {
        failFirstSave = false;
        throw new IllegalStateException("boom");
      }
      savedFavorites = Set.copyOf(favoriteExtensionIds);
      savedRecents = List.copyOf(recentExtensionIds);
    }
  }
}
