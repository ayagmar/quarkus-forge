package dev.ayagmar.quarkusforge.ui;

import java.util.List;
import java.util.Objects;

record SubmitAlertSnapshot(boolean visible, String title, List<String> lines) {
  static final SubmitAlertSnapshot HIDDEN = new SubmitAlertSnapshot(false, "", List.of());

  SubmitAlertSnapshot {
    title = title == null ? "" : title;
    lines = List.copyOf(Objects.requireNonNull(lines));
  }
}
