# Path Tree Tag Filter — Design Spec

**Issue:** [#10](https://github.com/t1/openapi-ui-next/issues/10)

## Goal

Focus mode for the path tree: let users filter by a single tag to reduce cognitive load
and hide irrelevant paths when working on a specific API area.

## Scope

- Path tree only (tag tree already groups by tag — filtering there is redundant).
- Single-select: one tag active at a time. Clicking the active pill deselects it.
- CSS-driven filtering with generated rules — minimal JS.

## UI Layout

### Filter Icon

- Font Awesome `fa-filter` icon, placed to the right of the view toggle (inside the
  path-tree sidebar header).
- Only rendered when the API has 2+ unique tags. APIs with 0 or 1 tags get no icon.
- Only visible in path-tree view (not in tag-tree view).
- **Default state:** muted/gray color.
- **Active state (filter selected):** accent/link color.
- Click toggles the pill panel open/closed.

### Pill Panel

- Pre-generated in the path-tree HTML — not dynamically created.
- Positioned between the view toggle row and the tree.
- Pills are **right-aligned** (closer to the filter icon).
- Initially hidden; shown when the filter icon is clicked.
- Contains one pill per spec-level tag, in spec declaration order.
- Untagged operations are not represented in the pills (they are always visible when
  no filter is active, and hidden when any filter is active — since they match no tag).

### Pill States

- **Inactive:** muted background, muted text.
- **Active (selected):** accent background, accent text, bold.
- Single-select: clicking a pill selects it and deselects any other. Clicking the
  active pill deselects it (back to unfiltered).
- When the panel is open but no pill is selected, the tree shows all items (unfiltered).

### Status Line

When a filter is active, show "Showing X of Y operations" at the bottom of the tree
for awareness.

## Filtering Mechanism

### CSS Classes (build-time)

The Java generator adds CSS classes to the path-tree HTML:

- **Tree items** (treeitem elements): `tag-{sanitized}` class for each tag on any of the
  item's operations. A tree item with both a `pets`-tagged GET and an `admin`-tagged POST
  gets `class="tag-pets tag-admin"`.
- **Method badges**: each badge gets a `tag-{sanitized}` class matching its operation's tag(s).
- **Tag name sanitization:** lowercase, replace non-alphanumeric characters with hyphens,
  collapse consecutive hyphens, trim leading/trailing hyphens. E.g., `My Tag` → `my-tag`.

### CSS Rules (build-time)

The generator emits per-tag CSS rules in `openapi-ui.css`:

```css
/* Hide tree items that don't match the filter AND don't contain matching descendants */
.filter-{tag} [role="treeitem"]:not(.tag-{tag}):not(:has(.tag-{tag})) {
    display: none;
}

/* Hide method badges that don't match the filter */
.filter-{tag} .tags > :not(.tag-{tag}) {
    display: none;
}
```

One pair of rules per tag.

### JS (runtime)

Clicking a pill sets/removes a `filter-{tag}` class on the tree root element. That's the
entire filtering logic — CSS does the rest.

- Select a pill: remove any existing `filter-*` class, add `filter-{tag}`.
- Deselect: remove the `filter-*` class.
- Update the filter icon color (add/remove an `is-active` class).
- Update the status line count.

### Filtering Behavior

- **Matching items:** tree items with the selected tag class stay visible, showing only
  matching method badges.
- **Non-matching items with matching descendants:** stay visible as structural breadcrumbs
  (path hierarchy preserved) but their own non-matching method badges are hidden.
- **Non-matching items with no matching descendants:** hidden entirely (`display: none`).
- **Untagged operations:** hidden when any filter is active (they have no tag class, so
  they never match a `.tag-{tag}` selector).

## Keyboard Navigation

### Filter Icon

- Focusable (in tab order after the view toggle).
- **Enter/Space:** toggles the pill panel. When opening, focus moves to the first pill
  (or the previously selected pill if one was active).
- **Down/Tab:** if panel is open, focus moves to the first pill. If closed, to the tree.

### Pills

Pills behave like tabs — arrow navigation immediately activates:

- **Left/Right:** moves between pills. Selecting a pill immediately activates filtering.
- **Enter/Space on active pill:** deselects it (clears filter).
- **Down/Tab:** focus moves into the tree.
- **Up/Shift+Tab:** focus moves to the filter icon.
- **Escape:** closes the filter panel, focus returns to filter icon.
- **Edges:** stop (bump), no wrapping, no deselection.

### Tree (while filtered)

Standard tree keyboard navigation. Hidden items are skipped (not focusable).

## Persistence (localStorage)

Two values:

- `openapi-ui-tag-filter-open` (boolean): whether the pill panel is expanded.
- `openapi-ui-tag-filter` (string): the selected tag name, or empty string for no filter.

On page load, restore both. When switching to tag-tree view, the filter panel hides but
persisted state is preserved for when the user switches back to path-tree.

## Edge Cases

- **API with 0 or 1 tags:** filter icon is not rendered.
- **Currently selected tree item gets filtered out:** clear the detail panel and deselect.
- **URL hash navigation to a filtered-out item:** clear the filter to show the target item.
- **Tag names with special characters:** sanitized to valid CSS class names (see above).
- **Operations with multiple tags:** their tree item and badges get multiple tag classes,
  so they appear under any matching filter.
- **Operations with no tags:** hidden when any filter is active.

## Files Changed

- `OpenApiUiGenerator.java`: add tag classes to tree items and method badges; generate
  filter icon and pill panel in path-tree header; emit per-tag CSS rules.
- `app.css`: styles for filter icon, pill panel, pill states, status line.
- `app.js`: filter icon toggle, pill click/keyboard handlers, persistence, status line
  update, integration with view toggle and hash navigation.
- Test specs and tests: exercise filtering behavior.
- Demo app: verify with real petstore tags.
