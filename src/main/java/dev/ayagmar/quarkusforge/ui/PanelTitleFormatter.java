package dev.ayagmar.quarkusforge.ui;

@FunctionalInterface
interface PanelTitleFormatter {
  String format(String baseTitle, boolean focused);
}
