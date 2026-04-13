# Layout Rearrangement: Server Selector & Mode Selector

## Problem

The Server and Global Headers panels use full-width banner-style panels that look visually
heavy compared to the card-style Response panel. The Server panel can be made more compact
by moving it to the hero area as a dropdown.

## Changes

### 1. Server Selector → Bulma Dropdown in Hero Top-Right

The full-width `panel()` with `flat-panel` class is replaced by a Bulma `dropdown()` in the
top-right corner of the hero area (where the Mode Selector currently sits).

**Trigger button:**
- Shows the currently selected server URL in monospace font.
- Dropdown arrow styled by Bulma's native `dropdown()` component.

**Dropdown content (right-aligned):**
- Each server is a dropdown item with:
  - URL in monospace font.
  - Description below it (smaller, muted) — same as the server description from the OpenAPI spec.
- Currently selected server is visually indicated.
- Template servers expand inline within the dropdown to show variable input fields and
  resolved presets (same interaction as today, just inside the dropdown).
- "Add custom URL" at the bottom.

**No servers defined:**
- Trigger shows the resolved origin URL.
- Dropdown contains one item: the resolved origin URL with description "resolved from origin".
- Plus "Add custom URL" below, so the user can add a custom URL and switch back.

**Single server:**
- Dropdown with one server item + "Add custom URL".

### 2. Mode Selector → Near the Send Button

The Mode Selector (pill toggle: try/httpie/curl + dropdown arrow for additional generators)
moves from the hero top-right to the operation detail pane, near the Send button.

- Visually unchanged — same pill group + dropdown arrow styling.
- Always visible regardless of mode or whether an operation is selected.

### 3. Global Headers — Unchanged

The Global Headers panel stays in its current position and styling. Will be addressed separately.

### 4. CSS Changes

- Server panel `flat-panel` styling removed — replaced by Bulma's `dropdown()`.
- `#server-selector` CSS rules replaced/simplified.
- Global Headers keeps its current `flat-panel` styling.
- No new custom CSS for the dropdown — Bulma provides it.
- Custom CSS only for: monospace font on URLs in dropdown items, and template server
  inline expansion within the dropdown.
