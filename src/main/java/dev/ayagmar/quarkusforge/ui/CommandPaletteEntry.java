package dev.ayagmar.quarkusforge.ui;

import java.util.Objects;

record CommandPaletteEntry(String label, String shortcut, CommandPaletteAction action) {
  CommandPaletteEntry {
    label = Objects.requireNonNull(label);
    shortcut = Objects.requireNonNull(shortcut);
    action = Objects.requireNonNull(action);
  }
}
