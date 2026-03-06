package dev.ayagmar.quarkusforge.ui;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.select.Select;
import dev.tamboui.widgets.select.SelectState;
import java.util.List;
import java.util.Objects;

final class CompactFieldRenderer implements CompactInputRenderer {
  private final UiTheme theme;
  private final Select focusedSelectorWidget;
  private final Select focusedSelectorErrorWidget;

  CompactFieldRenderer(UiTheme theme) {
    this.theme = Objects.requireNonNull(theme);
    focusedSelectorWidget =
        Select.builder()
            .style(UiInputStyles.focusedField(theme, false))
            .selectedColor(theme.color("focus"))
            .indicatorColor(theme.color("focus"))
            .leftIndicator("◀ ")
            .rightIndicator(" ▶")
            .build();
    focusedSelectorErrorWidget =
        Select.builder()
            .style(UiInputStyles.focusedField(theme, true))
            .selectedColor(theme.color("error"))
            .indicatorColor(theme.color("error"))
            .leftIndicator("◀ ")
            .rightIndicator(" ▶")
            .build();
  }

  @Override
  public void renderCompactSelector(
      Frame frame,
      Rect area,
      String label,
      String value,
      MetadataFieldRenderContext context,
      FocusTarget target,
      int selectedIndex,
      int totalOptions) {
    if (context.focusTarget() == target && MetadataSelectorManager.isSelectorFocus(target)) {
      renderCompactFocusedSelector(frame, area, label, context, target, selectedIndex);
      return;
    }
    String displayValue = value.isBlank() ? "default" : value;
    boolean focused = context.focusTarget() == target;
    String positionHint =
        totalOptions > 1 ? " (" + (selectedIndex + 1) + "/" + totalOptions + ")" : "";
    if (focused) {
      displayValue = "◀ " + displayValue + " ▶" + positionHint;
    } else if (totalOptions > 1) {
      displayValue = displayValue + " ◀▶";
    }
    renderCompactField(frame, area, label, displayValue, context, target, "]");
  }

  @Override
  public void renderCompactText(
      Frame frame,
      Rect area,
      String label,
      String value,
      MetadataFieldRenderContext context,
      FocusTarget target) {
    if (context.focusTarget() == target && UiFocusPredicates.isTextInputFocus(target)) {
      renderCompactFocusedTextInput(frame, area, label, context, target);
      return;
    }
    String displayValue = value.isBlank() ? defaultValueFor(target) : value;
    renderCompactField(frame, area, label, displayValue, context, target, "_]");
  }

  private void renderCompactFocusedSelector(
      Frame frame,
      Rect area,
      String label,
      MetadataFieldRenderContext context,
      FocusTarget target,
      int selectedIndex) {
    List<String> displayOptions = context.selectorOptions(target);
    if (displayOptions.isEmpty()) {
      renderCompactField(frame, area, label, "default", context, target, "]");
      return;
    }

    int index = Math.max(0, Math.min(selectedIndex, displayOptions.size() - 1));
    boolean hasError = hasValidationErrorFor(context, target);
    String errorHint = validationErrorHint(context, target);
    Style fieldStyle = UiInputStyles.focusedField(theme, hasError);
    String prefix = "  " + label + ": [";
    String suffix = "]" + (errorHint.isEmpty() ? "" : " " + errorHint);
    int selectorWidth = area.width() - prefix.length() - suffix.length();
    if (selectorWidth < 5) {
      renderCompactField(frame, area, label, displayOptions.get(index), context, target, "]");
      return;
    }

    frame.renderWidget(
        Paragraph.builder().text(prefix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(area.left(), area.top(), Math.min(prefix.length(), area.width()), area.height()));

    Rect selectArea =
        new Rect(area.left() + prefix.length(), area.top(), selectorWidth, area.height());
    Select selectWidget = hasError ? focusedSelectorErrorWidget : focusedSelectorWidget;
    frame.renderStatefulWidget(selectWidget, selectArea, new SelectState(displayOptions, index));

    frame.renderWidget(
        Paragraph.builder().text(suffix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(
            selectArea.left() + selectArea.width(),
            area.top(),
            area.width() - prefix.length() - selectArea.width(),
            area.height()));
  }

  private void renderCompactFocusedTextInput(
      Frame frame,
      Rect area,
      String label,
      MetadataFieldRenderContext context,
      FocusTarget target) {
    TextInputState state = context.inputState(target);
    if (state == null) {
      renderCompactField(frame, area, label, "", context, target, "_]");
      return;
    }

    String errorHint = validationErrorHint(context, target);
    String prefix = "  " + label + ": [";
    String suffix = "]" + (errorHint.isEmpty() ? "" : " " + errorHint);
    int inputWidth = area.width() - prefix.length() - suffix.length();
    if (inputWidth < 1) {
      String fallbackValue = state.text().isBlank() ? defaultValueFor(target) : state.text();
      renderCompactField(frame, area, label, fallbackValue, context, target, "_]");
      return;
    }

    Style fieldStyle = UiInputStyles.focusedField(theme, hasValidationErrorFor(context, target));
    frame.renderWidget(
        Paragraph.builder().text(prefix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(area.left(), area.top(), Math.min(prefix.length(), area.width()), area.height()));

    Rect inputArea = new Rect(area.left() + prefix.length(), area.top(), inputWidth, area.height());
    TextInput.builder()
        .style(fieldStyle)
        .cursorStyle(UiInputStyles.cursor(theme))
        .placeholder(defaultValueFor(target))
        .placeholderStyle(Style.EMPTY.fg(theme.color("muted")).italic())
        .build()
        .renderWithCursor(inputArea, frame.buffer(), state, frame);

    frame.renderWidget(
        Paragraph.builder().text(suffix).style(fieldStyle).overflow(Overflow.ELLIPSIS).build(),
        new Rect(
            inputArea.left() + inputArea.width(),
            area.top(),
            area.width() - prefix.length() - inputArea.width(),
            area.height()));
  }

  private void renderCompactField(
      Frame frame,
      Rect area,
      String label,
      String displayValue,
      MetadataFieldRenderContext context,
      FocusTarget target,
      String focusSuffix) {
    boolean focused = context.focusTarget() == target;
    String errorHint = validationErrorHint(context, target);
    int reservedForError = errorHint.isEmpty() ? 0 : errorHint.length() + 1;
    int maxValueLen = Math.max(8, area.width() - label.length() - 5 - reservedForError);
    if (displayValue.length() > maxValueLen) {
      displayValue = displayValue.substring(0, maxValueLen - 2) + "..";
    }

    String line = String.format("  %s: %s", label, displayValue);
    if (focused) {
      line = "  " + label + ": [" + displayValue + focusSuffix;
    }
    if (!errorHint.isEmpty()) {
      line = line + " " + errorHint;
    }

    Style style = Style.EMPTY.fg(focused ? theme.color("focus") : theme.color("text"));
    if (hasValidationErrorFor(context, target)) {
      style = Style.EMPTY.fg(theme.color("error"));
    }
    if (focused) {
      style = style.bold();
    }

    frame.renderWidget(
        Paragraph.builder().text(line).style(style).overflow(Overflow.ELLIPSIS).build(), area);
  }

  private boolean hasValidationErrorFor(MetadataFieldRenderContext context, FocusTarget target) {
    if ((!UiFocusPredicates.isTextInputFocus(target)
            && !MetadataSelectorManager.isSelectorFocus(target))
        || target == FocusTarget.EXTENSION_SEARCH) {
      return false;
    }
    String fieldName = UiFocusTargets.nameOf(target);
    return context.validation().errors().stream()
        .anyMatch(error -> error.field().equalsIgnoreCase(fieldName));
  }

  private String validationErrorHint(MetadataFieldRenderContext context, FocusTarget target) {
    if (!hasValidationErrorFor(context, target)) {
      return "";
    }
    String fieldName = UiFocusTargets.nameOf(target);
    return context.validation().errors().stream()
        .filter(error -> error.field().equalsIgnoreCase(fieldName))
        .findFirst()
        .map(error -> "⚠ " + error.message())
        .orElse("");
  }

  private static String defaultValueFor(FocusTarget target) {
    return switch (target) {
      case GROUP_ID -> "org.acme";
      case ARTIFACT_ID -> "quarkus-app";
      case VERSION -> "1.0.0-SNAPSHOT";
      case PACKAGE_NAME -> "org.acme.quarkus";
      case OUTPUT_DIR -> ".";
      default -> "";
    };
  }
}
