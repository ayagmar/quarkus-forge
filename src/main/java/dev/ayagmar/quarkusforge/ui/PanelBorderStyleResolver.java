package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.style.Style;

@FunctionalInterface
interface PanelBorderStyleResolver {
  Style resolve(boolean focused, boolean hasError, boolean isLoading);
}
