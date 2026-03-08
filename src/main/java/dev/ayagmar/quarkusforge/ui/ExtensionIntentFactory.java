package dev.ayagmar.quarkusforge.ui;

import java.util.ArrayList;
import java.util.List;

final class ExtensionIntentFactory {
  private ExtensionIntentFactory() {}

  static List<UiIntent> updateWithStatus(
      UiIntent.ExtensionStateUpdatedIntent extensionStateUpdatedIntent, String statusMessage) {
    List<UiIntent> intents = new ArrayList<>();
    intents.add(extensionStateUpdatedIntent);
    if (statusMessage != null) {
      intents.add(new UiIntent.ExtensionStatusIntent(statusMessage));
    }
    return List.copyOf(intents);
  }
}
