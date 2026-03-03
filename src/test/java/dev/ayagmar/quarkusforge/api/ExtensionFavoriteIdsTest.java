package dev.ayagmar.quarkusforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionFavoriteIdsTest {

  @Test
  void normalizeSetRemovesNullElements() {
    Set<String> result = ExtensionFavoriteIds.normalizeSet(listOf("a", null, "b"));

    assertThat(result).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void normalizeSetRemovesBlankElements() {
    Set<String> result = ExtensionFavoriteIds.normalizeSet(listOf("a", "  ", "", "b"));

    assertThat(result).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void normalizeSetTrimsElements() {
    Set<String> result = ExtensionFavoriteIds.normalizeSet(listOf("  a  ", "  b  "));

    assertThat(result).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void normalizeSetDeduplicates() {
    Set<String> result = ExtensionFavoriteIds.normalizeSet(listOf("a", "a", "b"));

    assertThat(result).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void normalizeSetReturnsImmutableSet() {
    Set<String> result = ExtensionFavoriteIds.normalizeSet(listOf("a"));

    assertThat(result).isUnmodifiable();
  }

  @Test
  void normalizeListRemovesNullAndBlank() {
    List<String> result = ExtensionFavoriteIds.normalizeList(listOf("a", null, "", "  ", "b"));

    assertThat(result).containsExactly("a", "b");
  }

  @Test
  void normalizeListDeduplicatesPreservingOrder() {
    List<String> result = ExtensionFavoriteIds.normalizeList(listOf("b", "a", "b", "c", "a"));

    assertThat(result).containsExactly("b", "a", "c");
  }

  @Test
  void normalizeListTrimsElements() {
    List<String> result = ExtensionFavoriteIds.normalizeList(listOf("  x  ", "  y  "));

    assertThat(result).containsExactly("x", "y");
  }

  @Test
  void normalizeListReturnsImmutableList() {
    List<String> result = ExtensionFavoriteIds.normalizeList(listOf("a"));

    assertThat(result).isUnmodifiable();
  }

  @Test
  void normalizeSetEmptySourceReturnsEmptySet() {
    Set<String> result = ExtensionFavoriteIds.normalizeSet(List.of());

    assertThat(result).isEmpty();
  }

  @Test
  void normalizeListEmptySourceReturnsEmptyList() {
    List<String> result = ExtensionFavoriteIds.normalizeList(List.of());

    assertThat(result).isEmpty();
  }

  private static List<String> listOf(String... elements) {
    var list = new java.util.ArrayList<String>();
    for (String element : elements) {
      list.add(element);
    }
    return list;
  }
}
