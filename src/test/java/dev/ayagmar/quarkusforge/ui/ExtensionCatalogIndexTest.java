package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExtensionCatalogIndexTest {
  private final ExtensionCatalogIndex index =
      new ExtensionCatalogIndex(
          List.of(
              new ExtensionCatalogItem(
                  "io.quarkus:quarkus-resteasy-jackson", "REST Jackson", "rest-jackson"),
              new ExtensionCatalogItem(
                  "io.quarkus:quarkus-rest-client", "REST Client", "rest-client"),
              new ExtensionCatalogItem(
                  "io.quarkus:quarkus-jdbc-postgresql", "JDBC PostgreSQL", "jdbc-postgresql"),
              new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest")));

  @Test
  void emptyQueryReturnsCatalogSortedByNameThenId() {
    List<ExtensionCatalogItem> results = index.search("");

    assertThat(results)
        .extracting(ExtensionCatalogItem::id)
        .containsExactly(
            "io.quarkus:quarkus-jdbc-postgresql",
            "io.quarkus:quarkus-rest",
            "io.quarkus:quarkus-rest-client",
            "io.quarkus:quarkus-resteasy-jackson");
  }

  @Test
  void searchPrioritizesExactAndPrefixMatches() {
    List<ExtensionCatalogItem> results = index.search("rest");

    assertThat(results).extracting(ExtensionCatalogItem::shortName).startsWith("rest");
    assertThat(results).extracting(ExtensionCatalogItem::shortName).contains("rest-client");
  }

  @Test
  void multiTokenSearchRequiresAllTokens() {
    List<ExtensionCatalogItem> results = index.search("jdbc postgresql");

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().id()).isEqualTo("io.quarkus:quarkus-jdbc-postgresql");
  }
}
