# Tree Component Styling Redesign

## Goal

Improve the look & feel of the tree navigation component. Keep the current inline layout
(operations on the same line as path segments) but make it polished and scannable.

## Design Decisions

### 1. Selected Item — Colored Left Accent

The active tree item gets:
- A **method-colored left border** (3px, color matches the first operation's HTTP method)
- A **tinted background** matching the method color at low opacity
- **Summaries visible** only on the selected row (e.g., "— List pets")

CSS concept:
```css
[role="treeitem"][aria-selected="true"] {
    background: linear-gradient(90deg, var(--method-color) 3px, var(--method-tint) 3px);
    padding-left: 12px;
}
```

The method colors already exist (`methodColor()` in `OpenApiUiGenerator`). The tint is the same
color at ~10% opacity.

### 2. Non-Selected Rows — Hover-Only

Unselected rows have:
- **No background** by default — transparent
- **Subtle gray background on hover** (`var(--bulma-scheme-main-ter)` or `#efefef`)
- **No summaries** — only path segment + method badges
- Smooth transition: `background 0.15s`

### 3. Path Parameters — Purple Italic

Path parameters like `{petId}` get distinct styling to separate them from literal segments:
- **Purple color** (`#7c5cbf` or a Bulma variable)
- **Italic** font style
- Slightly smaller weight than literal segments (600 vs 700)

This requires detecting `{...}` patterns in segment names when applying the CSS class.

### 4. Keyboard Focus — Blue Outline

When the tree has focus (`:focus-visible`), the selected item additionally shows:
- **Blue outline ring** (`2px solid var(--bulma-link)`)
- `outline-offset: 1px`

This is the existing behavior but needs to work with the new selected-item styling.

### 5. Spacing & Padding

- Row padding: `6px 8px` (up from `4px 0`)
- Row margin: `1px 0` (tighter than current `4px 0`)
- Row border-radius: `4px`
- Badge sizing: `9px` font, `2px 6px` padding, `3px` border-radius, `0.3px` letter-spacing
- Toggle arrow: `10px` font, `14px` width, centered

## Scope

Changes affect:
- `Tree.css()` — the CSS string in `Tree.java`
- `OpenApiUiGenerator` — add `tree-param` CSS class for `{...}` segments
- Possibly `openapi-ui.css` (the `APP_CSS` portion) if tree styling interacts with app styles

The `Tree` component's HTML structure and JS stay unchanged. This is a CSS-only change
plus a minor class addition for path parameters.

## Out of Scope

- Changing the inline layout to separate lines (decided against)
- Dark mode support (not currently supported)
- Animation/transition for expand/collapse (existing JS handles this)
