package dev.ayagmar.quarkusforge.ui;

import java.util.List;

final class UiStateOwnership {
  enum Category {
    REDUCER_OWNED,
    RENDER_ONLY
  }

  record Entry(String fieldName, Category category, String rationale) {}

  private static final List<Entry> ENTRIES =
      List.of(
          new Entry("request", Category.REDUCER_OWNED, "Authoritative project metadata input."),
          new Entry(
              "validation",
              Category.REDUCER_OWNED,
              "Reducer-visible validation outcome for submit."),
          new Entry("focusTarget", Category.REDUCER_OWNED, "Semantic focus state for routing."),
          new Entry("statusMessage", Category.REDUCER_OWNED, "User-visible semantic feedback."),
          new Entry("errorMessage", Category.REDUCER_OWNED, "Reducer-owned error summary."),
          new Entry(
              "verboseErrorDetails",
              Category.REDUCER_OWNED,
              "Expanded reducer-owned error detail."),
          new Entry("showErrorDetails", Category.REDUCER_OWNED, "Semantic UI toggle state."),
          new Entry("submitRequested", Category.REDUCER_OWNED, "Submit lifecycle flag."),
          new Entry(
              "submitBlockedByValidation",
              Category.REDUCER_OWNED,
              "Submit lifecycle decision state."),
          new Entry(
              "submitBlockedByTargetConflict",
              Category.REDUCER_OWNED,
              "Submit lifecycle decision state."),
          new Entry(
              "commandPaletteSelection",
              Category.REDUCER_OWNED,
              "Overlay selection state owned by reducer."),
          new Entry(
              "metadataPanel",
              Category.RENDER_ONLY,
              "Derived render snapshot assembled from reducer and runtime collaborators."),
          new Entry(
              "extensionsPanel",
              Category.RENDER_ONLY,
              "Derived render snapshot assembled from reducer and runtime collaborators."),
          new Entry(
              "footer",
              Category.RENDER_ONLY,
              "Derived render snapshot assembled from reducer and runtime collaborators."),
          new Entry(
              "overlays",
              Category.REDUCER_OWNED,
              "Semantic overlay visibility state used by routing and rendering."),
          new Entry(
              "generation",
              Category.RENDER_ONLY,
              "Runtime-derived generation progress view synchronized before render."),
          new Entry(
              "catalogLoad",
              Category.REDUCER_OWNED,
              "Reducer-visible catalog lifecycle semantics."),
          new Entry(
              "postGeneration",
              Category.REDUCER_OWNED,
              "Reducer-owned post-generation menu semantics."),
          new Entry(
              "startupOverlay",
              Category.RENDER_ONLY,
              "Runtime-derived startup overlay lines synchronized before render."),
          new Entry(
              "extensions",
              Category.REDUCER_OWNED,
              "Reducer-owned extension summary view for semantic UI state."));

  private UiStateOwnership() {}

  static List<Entry> entries() {
    return ENTRIES;
  }
}
