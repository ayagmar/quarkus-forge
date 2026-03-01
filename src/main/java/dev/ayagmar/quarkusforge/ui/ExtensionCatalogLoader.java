package dev.ayagmar.quarkusforge.ui;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface ExtensionCatalogLoader {
  CompletableFuture<ExtensionCatalogLoadResult> load();
}
