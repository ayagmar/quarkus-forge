# Core TUI Theme

## Theme Source
- Semantic theme tokens live in `src/main/resources/ui/quarkus-forge.tcss`.
- Runtime theme loading is handled by `UiTheme` (`src/main/java/dev/ayagmar/quarkusforge/ui/UiTheme.java`).
- If the theme file is missing or a token is invalid, defaults are used to keep the UI deterministic.

## Token Set
- `base`: neutral panel baseline.
- `text`: primary foreground text.
- `accent`: default panel/header/footer border.
- `focus`: focused field/list border and active highlight.
- `muted`: non-focused input border and placeholders.
- `success`: success state color.
- `warning`: loading/degraded fallback state color.
- `error`: validation and failure state color.

## Class Naming Policy
- `header`: application title container.
- `panel.metadata`: project metadata panel.
- `panel.extensions`: extension search/list/selection panel.
- `panel.selection`: selected extensions summary panel.
- `field.<name>`: metadata input field (`groupId`, `artifactId`, `version`, etc.).
- `catalog.list`: extension list surface.
- `footer.status`: status/hints/output area.

## Visual State Policy
- Focused interactive components use `focus`.
- Validation errors use `error`.
- Async loading uses `warning`.
- Success and next-step guidance use `success` text context.
- Narrow terminals use condensed hints and summary text; wide terminals show full hints.
