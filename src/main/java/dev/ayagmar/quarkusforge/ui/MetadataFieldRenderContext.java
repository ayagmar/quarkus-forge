package dev.ayagmar.quarkusforge.ui;

import dev.ayagmar.quarkusforge.domain.ValidationReport;
import dev.tamboui.widgets.input.TextInputState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record MetadataFieldRenderContext(
    FocusTarget focusTarget,
    ValidationReport validation,
    Map<FocusTarget, TextInputState> inputStates,
    Map<FocusTarget, List<String>> selectorDisplayOptions) {
  MetadataFieldRenderContext {
    focusTarget = Objects.requireNonNull(focusTarget);
    validation = Objects.requireNonNull(validation);
    inputStates = immutableEnumMap(Objects.requireNonNull(inputStates));
    selectorDisplayOptions =
        immutableListValueEnumMap(Objects.requireNonNull(selectorDisplayOptions));
  }

  TextInputState inputState(FocusTarget target) {
    return inputStates.get(target);
  }

  List<String> selectorOptions(FocusTarget target) {
    return selectorDisplayOptions.getOrDefault(target, List.of());
  }

  private static <T> Map<FocusTarget, T> immutableEnumMap(Map<FocusTarget, T> values) {
    EnumMap<FocusTarget, T> copy = new EnumMap<>(FocusTarget.class);
    copy.putAll(values);
    return Map.copyOf(copy);
  }

  private static Map<FocusTarget, List<String>> immutableListValueEnumMap(
      Map<FocusTarget, List<String>> values) {
    EnumMap<FocusTarget, List<String>> copy = new EnumMap<>(FocusTarget.class);
    values.forEach((target, options) -> copy.put(target, List.copyOf(options)));
    return Map.copyOf(copy);
  }
}
