# SplitPane Component Design

## Goal

Extract the resizable split pane from `OpenApiUiGenerator` into a reusable component
(`SplitPane.java`), following the same pattern as `Tree.java`.

## API

```java
splitPane()
    .first(box().content(tree))
    .second(detail)
    .persistAs("openapi-ui-tree-width")  // optional localStorage key
```

- `splitPane()` — static factory, creates the root element
- `first(Renderable)` — sets the left/first panel content
- `second(Renderable)` — sets the right/second panel content
- `persistAs(String key)` — enables localStorage persistence of the split position

## Component Responsibilities

**Owns:**
- CSS grid layout with draggable handle
- Handle visuals (dots, hover effect, cursor)
- Drag behavior (pointer events, 150px min-width constraints)
- localStorage persistence (when `persistAs()` is set)
- Responsive collapse to single column on mobile (`max-width: 1023px`)

**Does NOT own:**
- Panel styling (background, padding, min-height) — app CSS
- Panel content styling (e.g. box rounding) — app CSS

## HTML Structure

```html
<div class="split-layout">
  <div class="split-first">...</div>
  <div class="split-handle"></div>
  <div class="split-second">...</div>
</div>
```

Class names `split-tree` / `split-detail` become `split-first` / `split-second`.

## File Structure

- `SplitPane.java` in `com.github.t1.openapi.ui` with static `css()` and `js()` methods
- CSS and JS extracted from `OpenApiUiGenerator` into static strings in `SplitPane`
- `OpenApiUiGenerator` updated to use `splitPane()` and its app-specific styles remain inline

## Migration

The app-specific styles that remain in `OpenApiUiGenerator`:
- `.split-first` background color, padding, flex layout
- `.split-first > .box` border-radius
- `.split-first` / `.split-second` min-height
