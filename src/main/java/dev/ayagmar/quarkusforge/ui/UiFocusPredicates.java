package dev.ayagmar.quarkusforge.ui;

final class UiFocusPredicates {
  private UiFocusPredicates() {}

  static boolean isTextInputFocus(FocusTarget focusTarget) {
    return focusTarget == FocusTarget.GROUP_ID
        || focusTarget == FocusTarget.ARTIFACT_ID
        || focusTarget == FocusTarget.VERSION
        || focusTarget == FocusTarget.PACKAGE_NAME
        || focusTarget == FocusTarget.OUTPUT_DIR
        || focusTarget == FocusTarget.EXTENSION_SEARCH;
  }
}
