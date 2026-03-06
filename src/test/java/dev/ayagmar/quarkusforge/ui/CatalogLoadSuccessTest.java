package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogLoadSuccessTest {
  @Test
  void presetMapIsDeepImmutableCopy() {
    List<String> presetExtensions = new ArrayList<>(List.of("io.quarkus:quarkus-rest"));
    Map<String, List<String>> presetMap = new LinkedHashMap<>();
    presetMap.put("web", presetExtensions);

    CatalogLoadSuccess success =
        new CatalogLoadSuccess(
            List.of(new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 1)),
            null,
            presetMap,
            CatalogLoadState.loaded("live", false),
            "loaded");

    presetExtensions.clear();
    assertThat(success.presetExtensionsByName().get("web"))
        .containsExactly("io.quarkus:quarkus-rest");
    assertThatThrownBy(() -> success.presetExtensionsByName().get("web").add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
