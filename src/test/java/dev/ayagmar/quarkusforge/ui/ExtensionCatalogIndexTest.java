package dev.ayagmar.quarkusforge.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtensionCatalogIndexTest {
  @Test
  void emptyQueryUsesOrderThenPopularBaselineThenAlphabetical() {
    ExtensionCatalogIndex index =
        new ExtensionCatalogIndex(
            List.of(
                new ExtensionCatalogItem("io.quarkus:quarkus-zeta", "Zeta", "zeta", "Misc", null),
                new ExtensionCatalogItem(
                    "io.quarkus:quarkus-rest-jackson", "REST Jackson", "rest-jackson", "Web", null),
                new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", 20),
                new ExtensionCatalogItem("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 5),
                new ExtensionCatalogItem(
                    "io.quarkus:quarkus-smallrye-openapi", "OpenAPI", "openapi", "Web", 20)));

    List<ExtensionCatalogItem> results = index.search("", Set.of());

    assertThat(results)
        .extracting(ExtensionCatalogItem::id)
        .containsExactly(
            "io.quarkus:quarkus-arc",
            "io.quarkus:quarkus-rest",
            "io.quarkus:quarkus-smallrye-openapi",
            "io.quarkus:quarkus-rest-jackson",
            "io.quarkus:quarkus-zeta");
  }

  @Test
  void favoritesAreRankingSignalWithoutOverridingApiOrderPrecedence() {
    ExtensionCatalogIndex index =
        new ExtensionCatalogIndex(
            List.of(
                new ExtensionCatalogItem("io.quarkus:quarkus-arc", "CDI", "cdi", "Core", 10),
                new ExtensionCatalogItem("io.quarkus:quarkus-alpha", "Alpha", "alpha", "Misc", 40),
                new ExtensionCatalogItem("io.quarkus:quarkus-beta", "Beta", "beta", "Misc", 40)));

    List<ExtensionCatalogItem> results = index.search("", Set.of("io.quarkus:quarkus-beta"));

    assertThat(results)
        .extracting(ExtensionCatalogItem::id)
        .containsExactly(
            "io.quarkus:quarkus-arc", "io.quarkus:quarkus-beta", "io.quarkus:quarkus-alpha");
  }

  @Test
  void searchPrioritizesExactAndPrefixMatchesDeterministically() {
    ExtensionCatalogIndex index =
        new ExtensionCatalogIndex(
            List.of(
                new ExtensionCatalogItem(
                    "io.quarkus:quarkus-rest-jackson", "REST Jackson", "rest-jackson", "Web", null),
                new ExtensionCatalogItem(
                    "io.quarkus:quarkus-rest-client", "REST Client", "rest-client", "Web", null),
                new ExtensionCatalogItem(
                    "io.quarkus:quarkus-jdbc-postgresql",
                    "JDBC PostgreSQL",
                    "jdbc-postgresql",
                    "Data",
                    null),
                new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", null)));

    List<ExtensionCatalogItem> results = index.search("rest", Set.of());

    assertThat(results).extracting(ExtensionCatalogItem::shortName).startsWith("rest");
    assertThat(results).extracting(ExtensionCatalogItem::shortName).contains("rest-client");
  }

  @Test
  void multiTokenSearchRequiresAllTokens() {
    ExtensionCatalogIndex index =
        new ExtensionCatalogIndex(
            List.of(
                new ExtensionCatalogItem(
                    "io.quarkus:quarkus-jdbc-postgresql",
                    "JDBC PostgreSQL",
                    "jdbc-postgresql",
                    "Data",
                    null),
                new ExtensionCatalogItem("io.quarkus:quarkus-rest", "REST", "rest", "Web", null)));

    List<ExtensionCatalogItem> results = index.search("jdbc postgresql", Set.of());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().id()).isEqualTo("io.quarkus:quarkus-jdbc-postgresql");
  }
}
