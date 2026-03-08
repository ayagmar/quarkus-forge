package dev.ayagmar.quarkusforge.ui;

import java.util.List;

final class ExtensionIntentFactory {
  private ExtensionIntentFactory() {}

  static List<UiIntent> updateWithStatus(
      UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent, String statusMessage) {
    return statusMessage == null
        ? List.of(extensionStateUpdatedIntent)
        : List.of(extensionStateUpdatedIntent, new UiIntent.ExtensionStatusIntent(statusMessage));
  }
}
