package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.terminal.Frame;

interface CompactInputRenderer {
  void renderCompactSelector(
      Frame frame, Rect area, String label, String value, FocusTarget target);

  void renderCompactText(Frame frame, Rect area, String label, String value, FocusTarget target);
}
