# Tag-Based View for OpenAPI UI

## Problem

Large or flat APIs are hard to navigate with a path-only tree. OpenAPI tags provide semantic grouping that cuts across path hierarchy, but the UI currently only displays them as pills in the detail pane.

Two usage patterns exist:
- **Well-structured APIs** with deep path nesting — tags are supplementary (filtering/cross-cutting)
- **Flat APIs** with many top-level paths — tags are the primary organizational structure

The UI should serve both patterns.

## Design

### View Toggle

A segmented control (**Paths** / **Tags**) above the tree inside the left pane's box. Same visual style as the existing try/httpie/curl mode toggle.

- Clicking a segment fetches the other view via HTMX (`hx-get`) and swaps the tree container content.
- The user's choice is persisted to localStorage keyed by API title (same pattern as `SplitPane.persistAs()`).
- On page load, JS checks localStorage and, if the stored preference differs from the inline default, triggers the HTMX fetch automatically.

### Default Heuristic (build-time)

Determines which tree is embedded inline in `index.html` (no extra request on first load):

1. **No tags or only one tag** → Paths default (tag view shows explanatory message)
2. **Tags exist and >50% of paths are single-segment** → Tags default
3. **Tags exist and paths are nested** → Paths default

### Generated Files

- `path-tree.html` — path tree fragment (existing tree structure, extracted to its own file)
- `tag-tree.html` — tag view fragment
- `index.html` — embeds the heuristic-chosen default inline

### Tag View Structure

Each tag is a collapsible tree node. Under it, a flat list of operations:

```
▼ pets
    GET  /pets          List all pets
    POST /pets          Create a pet
    GET  /pets/{petId}  Get a pet by ID
▼ owners
    GET  /owners        List owners
    ...
```

- Operations with multiple tags appear under each tag, with a gentle "also in: X, Y" hint (small gray text below the operation label, not interactive).
- Untagged operations (if any) go under an "Other" group at the bottom.
- Tag order follows the top-level `tags` array in the OpenAPI spec (spec-defined ordering). Undeclared tags appear after declared ones.

### Click Behavior

Clicking an operation in the tag view loads the same path-based fragment as the path tree (e.g. `pets/index.html` into `#detail`). The method tab bar shows all methods for that path, with the clicked method's tab pre-selected (e.g. clicking `POST /pets` opens the path fragment with POST active).

### No-Tags / Single-Tag State

When the API has no tags, the Tags segment is still clickable. The content shows an explanatory message: "This API doesn't define any tags. Tags allow API authors to group endpoints by topic." with a link to the OpenAPI tags documentation.

When the API has only one tag, the message adjusts: "This API has only one tag, so tag grouping provides no additional structure."

### Keyboard Navigation

- The view toggle is focusable and responds to arrow keys to switch views (matching the mode toggle behavior).
- Within the tag view tree: same keyboard navigation as the path tree — arrow keys, Enter to load detail, Tab to move to method tabs.

### Single-Segment Heuristic

A path is "single-segment" if it has exactly one segment after stripping the leading slash. For example, `/pets` is single-segment; `/pets/{petId}` is not.

### Demo App

The petstore demo already uses tags (`pets`, `owners`, `visits`). The demo data should be extended to exercise multi-tag operations and the heuristic (the current demo has nested paths, so it will default to path view — good for verifying the toggle works).

## Out of Scope

- Tag filtering within path view (show/hide by tag) — potential future enhancement
- Hierarchical/nested tags (OpenAPI 3.2+ `parent` field) — not widely adopted yet
- Search/filter within tag view
- Tag descriptions and externalDocs display
