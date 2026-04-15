# Pure Spatial Navigation

Refactor the keyboard navigation system so that spatial navigation is a generic,
component-agnostic function, and all component-specific keyboard behavior is handled
by the components themselves.

## Problem

`findSpatialTarget` contains 8 component-specific exceptions (tab rect coalescing,
toggle exclusions, status tab filtering, hardcoded candidate allowlist, etc.). Most
components hardcode their boundary navigation targets with `querySelector(...).focus()`
instead of delegating to spatial nav. Adding a new focusable element (like the filter
icon) requires wiring it into multiple hardcoded chains. This doesn't scale and invites
violations with every new component.

## Principle

Spatial navigation finds the nearest focusable element in a direction based on screen
position. Components handle their own internal keys. These two concerns never mix.

## Architecture

### Three layers

1. **`findSpatialTarget(el, direction)`** — pure function. Discovers all focusable
   elements on the page, filters by visibility, picks the nearest one in the given
   direction using corridor + euclidean distance. No component-specific logic.

2. **Global keydown handler (bubble phase)** — listens for arrow keys on `document`.
   If the event hasn't been stopped by a component, calls `findSpatialTarget` and
   focuses the result. Also handles Enter/Space activation (not spatial nav, but
   co-located for convenience).

3. **Component handlers (on their own elements)** — each component handles keys it
   "owns" and calls `stopPropagation` + `preventDefault`. At boundaries, it does
   nothing — the event bubbles to the global handler.

### Candidate discovery

`findSpatialTarget` queries all naturally focusable elements:

```
input:not(:disabled), select:not(:disabled), textarea:not(:disabled),
button:not(:disabled), a[href], [tabindex="0"]
```

Filters out invisible elements (`offsetParent === null`). No component-specific selector.

### Composite components as single spatial units

Components that contain multiple interactive children but should be navigated to as a
single unit put `tabindex="0"` on their container. The children do not have tabindex.
The container's keydown handler manages internal focus (ArrowLeft/Right to switch items).
Spatial nav only sees the container.

### Event flow

```
User presses ArrowUp at top of tree
  → Tree's keydown handler fires (bubble phase, on tree element)
  → Tree sees idx === 0 → boundary → does nothing
  → Event bubbles to document
  → Global handler fires → calls findSpatialTarget(el, 'up')
  → Finds filter icon above tree → focuses it
```

```
User presses ArrowDown within tree (not at boundary)
  → Tree's keydown handler fires
  → Tree moves to next item → stopPropagation + preventDefault
  → Event does not reach global handler
```

### Escape handling

Escape is not spatial navigation (it has no direction). It is removed from the global
handler. Each component that uses Escape handles it locally:
- Filter panel: Escape closes panel, focuses filter icon (already implemented)
- Server dropdown: Escape closes dropdown (already implemented)
- Detail pane fields: Escape focuses tree (new handler on `#method-content`)

## Component types

Four component types need internal keyboard handling:

| Component | Instances | Internal (stopPropagation) | Boundary (bubbles) |
|-----------|-----------|---------------------------|---------------------|
| Tree | path tree | Up/Down between items, Left/Right expand/collapse | Up at top, Down at bottom, Right at expanded leaf |
| Toggle | view, mode, filter pills | Left/Right switch option | Up/Down |
| Tab bar | method tabs, status tabs | Left/Right switch tab | Up/Down |
| Filter icon | filter icon | Enter/Space toggle panel | Up/Down |

Inputs and textareas are not components with their own handlers. The global handler
has generic logic for them: Left/Right are left to the browser (cursor movement),
Up/Down navigate away only at first/last line of a textarea. This logic stays in the
global handler — it's not component-specific, it's standard text-field awareness.

## Tab bar builder

Method tabs (`PathFragmentGenerator`) and status tabs (`OperationFragmentGenerator`)
are currently built separately but are the same concept: a group of switchable items
with one active. Extract a shared tab bar builder that:
- Sets `tabindex="0"` on the container
- Does NOT set tabindex on individual tab items
- Includes a keydown handler for ArrowLeft/Right to switch active tab
- Lets ArrowUp/Down bubble for spatial nav

## Tab/Shift+Tab

Tab order follows the same principle: components that want custom Tab behavior handle it
with `stopPropagation`. The tree's Tab → tabs → detail flow is custom Tab handling within
the tree component, not spatial nav. This is acceptable — Tab is sequential navigation,
not spatial.

## What gets removed

- Hardcoded candidate selector in `findSpatialTarget` (replaced by generic focusable query)
- All 8 component-specific exceptions in `findSpatialTarget`
- Capture-phase registration of global handler (changed to bubble phase)
- All hardcoded `querySelector(...).focus()` for boundary navigation in: tree ArrowUp
  at top, tree ArrowRight at leaf, view toggle ArrowDown, filter panel ArrowDown/ArrowUp
- Early-return exclusion list in global handler (tree, toggle, server dropdown checks)
- Escape from global handler (moved to detail pane)

## What stays

- `findSpatialTarget` distance algorithm (corridor + euclidean) — already generic
- Enter/Space activation in global handler — not spatial nav, but co-located
- Tree internal navigation (Up/Down between items, Left/Right expand/collapse)
- Toggle internal navigation (Left/Right between options)
- `bump()` animation at boundaries — called by global handler when `findSpatialTarget`
  returns null

## Comments for future agents

After the refactor, `findSpatialTarget` and the global handler get JSDoc comments:

```javascript
/**
 * Finds the nearest focusable element in the given direction from {@code el}'s
 * screen position, using corridor-based primary-axis distance with euclidean
 * fallback.
 *
 * IMPORTANT: This function must remain component-agnostic. No component-specific
 * selectors, exceptions, or rect overrides. Components that need to act as a
 * single spatial unit must be a single focusable element (tabindex="0" on the
 * container, no tabindex on children). Components handle their own internal keys
 * with stopPropagation; boundary navigation bubbles to the global handler which
 * calls this function.
 */
```

## Testing

Existing browser tests cover the navigation behavior. The refactor must not change any
observable behavior — all 555 existing tests must continue to pass. No new tests are
needed for the refactor itself (same behavior, cleaner implementation).
