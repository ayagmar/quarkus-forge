package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.MetadataCompatibilityContext;
import dev.ayagmar.quarkusforge.domain.ProjectRequest;
import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.widgets.input.TextInputState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;

final class CatalogEffects {
  interface Callbacks {
    UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent();

    ProjectRequest currentRequest();

    ProjectRequest syncMetadataSelectors(ProjectRequest request);

    ValidationReport validateRequest(ProjectRequest request);
  }

  record ApplyLoadSuccessResult(
      MetadataCompatibilityContext metadataCompatibility, List<UiIntent> followUpIntents) {
    ApplyLoadSuccessResult {
      metadataCompatibility = Objects.requireNonNull(metadataCompatibility);
      followUpIntents = List.copyOf(Objects.requireNonNull(followUpIntents));
    }
  }

  private final CatalogLoadCoordinator catalogLoadCoordinator;
  private final ExtensionCatalogPreferences extensionCatalogPreferences;
  private final ExtensionCatalogNavigation extensionCatalogNavigation;
  private final ExtensionCatalogProjection extensionCatalogProjection;
  private final Callbacks callbacks;

  CatalogEffects(
      CatalogLoadCoordinator catalogLoadCoordinator,
      ExtensionCatalogPreferences extensionCatalogPreferences,
      ExtensionCatalogNavigation extensionCatalogNavigation,
      ExtensionCatalogProjection extensionCatalogProjection,
      Callbacks callbacks) {
    this.catalogLoadCoordinator = Objects.requireNonNull(catalogLoadCoordinator);
    this.extensionCatalogPreferences = Objects.requireNonNull(extensionCatalogPreferences);
    this.extensionCatalogNavigation = Objects.requireNonNull(extensionCatalogNavigation);
    this.extensionCatalogProjection = Objects.requireNonNull(extensionCatalogProjection);
    this.callbacks = Objects.requireNonNull(callbacks);
  }

  void startLoad(ExtensionCatalogLoader loader, CatalogLoadIntentPort catalogLoadIntentPort) {
    catalogLoadCoordinator.startLoad(loader, catalogLoadIntentPort);
  }

  void requestReload(CatalogLoadIntentPort catalogLoadIntentPort) {
    catalogLoadCoordinator.requestReload(catalogLoadIntentPort);
  }

  void cancelPendingAsync(CatalogLoadIntentPort catalogLoadIntentPort) {
    catalogLoadCoordinator.cancel(catalogLoadIntentPort);
    extensionCatalogProjection.cancelPendingAsync();
  }

  ApplyLoadSuccessResult applyLoadSuccess(
      CatalogLoadSuccess success,
      MetadataCompatibilityContext metadataCompatibility,
      String query) {
    MetadataCompatibilityContext nextMetadataCompatibility =
        success.metadata() == null
            ? metadataCompatibility
            : MetadataCompatibilityContext.success(success.metadata());
    replaceCatalog(success.items(), query);
    extensionCatalogProjection.setPresetExtensionsByName(
        success.presetExtensionsByName(),
        extensionCatalogNavigation,
        extensionCatalogPreferences,
        ignored -> {});
    List<UiIntent> followUpIntents = new ArrayList<>();
    followUpIntents.add(callbacks.extensionStateUpdatedIntent());
    if (success.metadata() != null) {
      ProjectRequest nextRequest = callbacks.syncMetadataSelectors(callbacks.currentRequest());
      followUpIntents.add(
          new UiIntent.FormStateUpdatedIntent(nextRequest, callbacks.validateRequest(nextRequest)));
    }
    return new ApplyLoadSuccessResult(nextMetadataCompatibility, followUpIntents);
  }

  void scheduleFilteredRefresh(String query, IntConsumer onFiltered) {
    extensionCatalogProjection.scheduleRefresh(
        query, extensionCatalogNavigation, extensionCatalogPreferences, onFiltered);
  }

  String clearSearchFilter(TextInputState searchState) {
    searchState.setText("");
    searchState.moveCursorToStart();
    extensionCatalogProjection.refreshNow(
        "", extensionCatalogNavigation, extensionCatalogPreferences, ignored -> {});
    return "Extension search cleared";
  }

  private void replaceCatalog(List<ExtensionCatalogItem> items, String query) {
    Set<String> availableExtensionIds = new LinkedHashSet<>();
    for (ExtensionCatalogItem item : items) {
      availableExtensionIds.add(item.id());
    }
    extensionCatalogNavigation.retainAvailableSelections(availableExtensionIds);
    extensionCatalogPreferences.retainAvailable(availableExtensionIds);
    extensionCatalogProjection.replaceCatalog(
        items, query, extensionCatalogNavigation, extensionCatalogPreferences, ignored -> {});
  }
}
