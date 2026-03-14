# CSS Cleanup: Replace Custom Colors with Bulma Components and Variables

## Problem

The custom CSS in `OpenApiUiGenerator.java` has 30+ hardcoded HSL color values, all light-mode only. Bulma's automatic dark mode is completely broken because every background, border, and text color is hardcoded.

## Approach

Replace hardcoded colors with Bulma CSS variables and swap custom-styled elements for standard Bulma components where possible. Keep custom CSS only for structural/layout rules that Bulma doesn't cover (tree component, spacing).

## Changes

### 1. Method badges -> Bulma Tag

Replace `span("GET").classes("method-badge", "method-get")` with `tag("GET").is(SUCCESS)`.

Color mapping:
- GET -> `SUCCESS` (green)
- POST -> `LINK` (blue)
- PUT -> `WARNING` (orange)
- DELETE -> `DANGER` (red)
- PATCH -> `PRIMARY` (purple/turquoise)

Detail variant: `.is(MEDIUM)` instead of custom `.detail-badge` class.

Delete all `.method-badge` and `.method-*` CSS rules.

### 2. Detail pane -> Bulma Box

Replace `div().id("detail")` with `box().id("detail")`.

Delete `#detail` background, border, shadow, border-radius CSS. Keep structural rules only:
- `.field` margin
- `button[data-path]` margin
- `pre` styling with Bulma variables for colors

### 3. Body and sidebar colors -> Bulma variables

- Delete `body { background-color }` entirely (Bulma owns this)
- Sidebar background: `var(--bulma-scheme-main-bis)`
- Sidebar border: `var(--bulma-border)`

### 4. Tree component -> Bulma variables for colors

Keep all structural CSS (indentation, toggle rotation, list-style reset, padding, cursor).

Replace colors:
- Tree group border-left: `var(--bulma-border)`
- Hover background: `var(--bulma-scheme-main-ter)`
- Selected background: `var(--bulma-link-light)`
- Focus outline: `var(--bulma-link)`
- Segment text: `var(--bulma-text-strong)`
- Toggle icon: `var(--bulma-text-weak)`
- Operation label: `var(--bulma-text-weak)`

### 5. Detail header and code blocks -> Bulma variables

- Header border-bottom: `var(--bulma-border)`
- Remove `.title` color override (inherit from Bulma)
- `pre` background: `var(--bulma-scheme-main-bis)`
- `pre` border: `var(--bulma-border)`
- Endpoint path color: `var(--bulma-text-strong)`
- Op summary color: `var(--bulma-text-weak)`

### 6. Font families

Keep monospace font-family declarations unchanged (structural, not theme-related).

### 7. Inline style

Keep `style("gap:0.75rem")` on the flex container (structural, not color).

## Result

- Custom CSS drops from ~150 lines to ~50
- Zero hardcoded color values
- Dark mode works automatically via Bulma 1.0's CSS variable system
- Java code uses `Box.box()` and `Tag.tag()` instead of manually styled `div`/`span`

## Files affected

- `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (CUSTOM_CSS constant + Java element construction)
- Test expectations may need updating for changed HTML output
