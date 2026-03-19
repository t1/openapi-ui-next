# Resizable Split Pane

## Summary

Add a draggable handle between the tree panel and the detail panel so users can adjust the split ratio. The tree width (in pixels) persists to `localStorage` per origin.

## Design

Replace the fixed Bulma `is-one-third` column layout with a CSS `grid-template-columns` layout. A thin drag handle (`<div class="split-handle">`) sits between the two columns. Dragging it adjusts the `grid-template-columns` value.

### Handle

- 6px-wide vertical bar between the two panels
- Styled with `--bulma-border` color, cursor changes to `col-resize` on hover
- Min width: ~150px for each side to prevent collapsing

### Persistence

- `localStorage` key: `openapi-ui-tree-width`
- Stores the tree width in pixels (not ratio), so a preferred width stays consistent across window resizes
- `localStorage` is scoped per origin, so different APIs on different servers get their own value
- Default: previous `is-one-third` behavior (~33%)

### Mobile

- Below 1024px the handle is hidden and columns stack vertically as before

### Implementation

- JS uses pointer events (`pointerdown`/`pointermove`/`pointerup`) — no external dependencies
- JS lives in `APP_JS` alongside existing mode toggle and keyboard nav code
- CSS replaces Bulma columns with CSS grid for the two-panel layout

### Testing

- Browser test: drag the handle and verify column widths change
- Browser test: verify default ratio is ~33% without stored preference
- Demo app exercises the feature by default (existing layout)
