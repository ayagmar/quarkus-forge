package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;
import java.util.List;

/**
 * Render orchestrator that consumes immutable {@link UiRenderModel} and delegates drawing to an
 * adapter.
 */
final class UiRenderer {

  interface Adapter {
    List<String> composeFooterLines(int width, FooterSnapshot footerSnapshot);

    int estimateFooterHeight(List<String> lines, int availableWidth);

    void renderHeader(Frame frame, Rect area);

    void renderBody(
        Frame frame,
        Rect area,
        SubmitAlertSnapshot submitAlert,
        MetadataPanelSnapshot metadataPanelSnapshot,
        ExtensionsPanelSnapshot extensionsPanelSnapshot);

    void renderFooter(Frame frame, Rect area, List<String> footerLines);

    void renderGenerationOverlay(Frame frame, Rect viewport, UiState.GenerationView generation);

    void renderCommandPalette(Frame frame, Rect viewport, int selection);

    void renderHelpOverlay(Frame frame, Rect viewport, UiState state);

    void renderPostGenerationOverlay(
        Frame frame, Rect viewport, UiState.PostGenerationView postGeneration);

    void renderStartupStatusOverlay(
        Frame frame, Rect viewport, UiState.StartupOverlayView startupOverlay);
  }

  void render(Frame frame, UiRenderModel renderModel, Adapter adapter) {
    UiState state = renderModel.reducerState();
    Rect area = frame.area();
    List<String> footerLines = adapter.composeFooterLines(area.width(), renderModel.footer());
    int footerHeight = adapter.estimateFooterHeight(footerLines, Math.max(1, area.width() - 2));
    List<Rect> rootLayout =
        Layout.vertical()
            .constraints(Constraint.length(1), Constraint.fill(), Constraint.length(footerHeight))
            .split(area);

    adapter.renderHeader(frame, rootLayout.get(0));
    adapter.renderBody(
        frame,
        rootLayout.get(1),
        renderModel.submitAlert(),
        renderModel.metadataPanel(),
        renderModel.extensionsPanel());
    adapter.renderFooter(frame, rootLayout.get(2), footerLines);
    if (state.overlays().generationVisible()) {
      adapter.renderGenerationOverlay(frame, area, renderModel.generation());
    }
    if (state.overlays().commandPaletteVisible()) {
      adapter.renderCommandPalette(frame, area, state.commandPaletteSelection());
    }
    if (state.overlays().helpOverlayVisible()) {
      adapter.renderHelpOverlay(frame, area, state);
    }
    if (state.postGeneration().visible()) {
      adapter.renderPostGenerationOverlay(frame, area, state.postGeneration());
    }
    if (state.overlays().startupOverlayVisible()) {
      adapter.renderStartupStatusOverlay(frame, area, renderModel.startupOverlay());
    }
  }
}
