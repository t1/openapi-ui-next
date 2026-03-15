# Path-Based Tree with Method Tabs

## Problem

When selecting a tree item, the operation summary text appears and can wrap to a second line, causing vertical layout jumps that shift all items below.

## Solution

Restructure the UI into a two-level selection model: the tree navigates **paths only** (no summaries, no per-method items), and the detail pane presents **method tabs** with enriched content.

## Tree Structure

- Tree items represent paths, not operations
- Each item shows the path segment plus small colored method tags as inline addons (Bulma `tag` elements at a smaller size, e.g. `is-small`). These are always visible and purely informational — not clickable
- If a path has many methods, tags may wrap within the tree item. This is acceptable since the tags are always present (no selection-triggered reflow)
- Path nodes that have no operations of their own (e.g. a structural `{id}` that only has children) render as expand/collapse nodes without `hx-get` — clicking them toggles expansion only, no fragment loads
- Remove `.tree-op-summary`, `.tree-op-label`, and the show-on-select CSS rule
- The `hx-get` attribute moves from per-operation labels to the tree item itself, pointing to the path's `index.html` with `hx-target="#detail"` and `hx-swap="innerHTML"` (same as today)

## Path Fragments (HTMX Tabs)

Each path with operations gets an `index.html` fragment. It contains:

```html
<!-- pets/index.html -->
<div class="tabs">
  <ul>
    <li class="is-active"><a hx-get="pets/GET.html" hx-target="#method-content" hx-swap="innerHTML">GET</a></li>
    <li><a hx-get="pets/POST.html" hx-target="#method-content" hx-swap="innerHTML">POST</a></li>
  </ul>
</div>
<div id="method-content">
  <!-- GET.html content pre-rendered here -->
</div>
```

- `#detail` receives the path fragment (tab bar + `#method-content` container with first method pre-rendered)
- `#method-content` (inside `#detail`) receives method fragments on tab switch
- Methods are ordered as they appear in the OpenAPI spec file (preserving `readOperationsMap()` order)
- New JS for tab switching: set `is-active` on clicked `<li>`, let HTMX handle the content swap
- The keyboard focus management across tree/tabs/fields is a more substantial JS addition (see Keyboard Navigation)

### File Structure

```
pets/
├── index.html       # path fragment: tab bar + pre-loaded first method content
├── GET.html         # method fragment
└── POST.html        # method fragment
```

## Enriched Method Fragments

Method fragments show (in this order):

1. **Method + path heading** — method badge + monospace path (as today)
2. **Summary** — bold subtitle text
3. **Description** — body text from `Operation.getDescription()`
4. **Deprecated badge** — warning-colored label, shown inline after description if `Operation.getDeprecated()` is true
5. **Tags** — rendered as small Bulma `tag` elements in a flex row
6. **External docs** — link from `Operation.getExternalDocs()`, if present
7. **Parameters** — input fields (as today)
8. **Request body** — textarea (as today)
9. **Send/Copy button** — (as today)

## Mode Toggle

The Try/curl/httpie mode toggle stays in the detail header (above the tree/detail columns), unchanged. It continues to affect the Send/Copy button behavior in whichever method fragment is currently displayed.

## Keyboard Navigation

Three focus levels: tree → tabs → fields. Escape from any level returns to tree (intentionally skipping intermediate levels for fast bail-out).

### Tree

| Key | On collapsed node | On expanded node | On leaf |
|-----|------------------|-----------------|---------|
| ArrowDown | next visible item | next visible (= first child) | next visible item |
| ArrowUp | prev visible item | prev visible item | prev visible item |
| ArrowRight | expand | enter tabs (first tab) | enter tabs (first tab) |
| ArrowLeft | go to parent | collapse | go to parent |
| Enter/Tab | enter tabs (current tab) | enter tabs (current tab) | enter tabs (current tab) |

Note: ArrowRight on a collapsed node expands it but does not enter tabs. A second ArrowRight enters tabs.

### Tabs

| Key | Action |
|-----|--------|
| ArrowLeft | previous tab; on first tab → back to tree |
| ArrowRight | next tab; on last tab → bump |
| ArrowDown/Enter/Tab | enter fields (first field) |
| ArrowUp | bump (boundary) |
| Escape | back to tree |

### Fields

| Key | Action |
|-----|--------|
| ArrowDown | next field; on last field → bump |
| ArrowUp | prev field; on first field → back to tabs |
| Enter | trigger Send/Copy |
| Escape | back to tree |

### Boundary Feedback

When a navigation key hits a boundary (e.g. ArrowDown on last tree item), a subtle CSS animation plays on the current element. Direction matches the key axis: vertical shake for up/down, horizontal for left/right. Exact animation TBD during implementation (shake, pulse, or flash).

## Auto-Selection

- First tree path is auto-selected on page load
- Selecting a path auto-selects the first method tab
- ArrowRight from tree always enters the first tab; Enter/Tab enters the current (last selected) tab

## Interactive Prototype

An interactive prototype is available at `.superpowers/brainstorm/26488-1773590651/prototype.html`.
