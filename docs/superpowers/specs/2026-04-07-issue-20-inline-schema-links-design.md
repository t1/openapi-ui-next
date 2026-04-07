# Inline Schema Links Design

**Issue:** #20 — Unify schema section layout  
**Date:** 2026-04-07

## Problem

The Links section in the response schema panel uses a completely different visual pattern
than Headers and Body: a border-top separator, a styled label with `font-weight: 600`, and
7 dedicated CSS classes (`schema-response-links`, `schema-links-label`, `schema-links`,
`schema-link-name`, `schema-link-details`, `schema-link-operation`, `schema-link-desc`,
`schema-link-param`). Headers and Body use the shared `schema-props` grid with plain
unstyled `<span>` labels.

This inconsistency was introduced by an agent that made the design decision autonomously.

## Design

**Inline links into their source sections.** Instead of a separate "Links" section, each
link appears as a sub-row underneath the property it sources its parameter from.

### Placement Rules

1. **Body-sourced link** (`$response.body#/path`): sub-row appears under the resolved body
   property in the Body schema grid.
2. **Header-sourced link** (`$response.header.Name`): sub-row appears under the matching
   header in the Headers grid.
3. **Multi-source link** (parameters from multiple properties): duplicated — a sub-row
   appears under each source property (whether in body or headers). All parameter mappings
   are shown on each duplicate. This applies both to mixed body/header links and to links
   with multiple body parameters pointing to different properties.
4. **Nested body path** (`$response.body#/owner/id`): sub-row appears under the leaf
   property (`id` inside the expanded `owner` object). The link is only visible when the
   user expands the nested schema to reveal that property.
5. **Unresolvable source** (property not in schema, or expression like `$request.path.id`):
   these don't attach to any response property. They can appear as standalone link rows at
   the end of the body section, or be omitted. (Edge case — decide during implementation.)

### Sub-Row Content

Each link sub-row contains, left to right:

- **Empty first column** (aligns with the property name column in the `schema-props` grid)
- **Link arrow + name** (`→ GetOwner`) — the name is a clickable `<a href>` that navigates
  to the target operation (using the existing hash-based navigation with query parameters)
- **Description** (italic, muted) — the link's `description` field, if present
- **Parameter mappings** — one per parameter, showing `targetParam ← sourceExpression`

### Parameter Display Format

Use a **short form** that omits redundant context:

- **Same-source parameter** (link is under `ownerId`, param sources from `ownerId`):
  show `ownerId ← ownerId`
- **Cross-source parameter** (link is under a body property but param sources from a
  header, or vice versa): show `cursor ← header.X-Next-Cursor` or
  `category ← body.category`

The `$response.` prefix is always omitted — it's implied.

### CSS Changes

- **Remove** all 7 `schema-link-*` / `schema-response-links` CSS classes
- **Remove** the `schema-links-label` styled label
- **Remove** the `border-top` separator
- **Add** a single `schema-link-row` class for the sub-row, using the existing
  `schema-props` grid (spanning the details column)
- The link name uses standard `<a>` styling (inherits from Bulma link color)
- Description and params use `schema-prop-desc`-like muted styling

### Separate "Links" Section

The separate Links section is **removed entirely**. All links are inlined. If a link has
no resolvable source property (e.g., all parameters use `$request.*` expressions), it
appears as a standalone row at the end of the body section.

### Java Changes

- `OperationFragmentGenerator.responseLinks()` method is removed
- Link rendering moves into `SchemaRenderer` (for body links) and `schemaHeaders()`
  (for header links)
- The response's `links` map is passed to both renderers
- Each renderer matches links to properties by parsing the runtime expression and
  resolving the JSON Pointer / header name
- `statusCodePanel()` no longer calls `responseLinks()` or renders a "Links" label

### Body Links in Try-It Response

The existing `renderJsonWithLinks()` JS function (which highlights body values as clickable
links in the try-it response) is unaffected by this change. It operates on the JSON response
body, not the schema view. No changes needed.

## Files Affected

- `OperationFragmentGenerator.java` — remove `responseLinks()`, pass links to schema/header renderers
- `SchemaRenderer.java` — accept links map, render link sub-rows under matching properties
- `app.css` — remove `schema-link-*` classes, add minimal `schema-link-row` class
- `BrowserTest.java` — update link assertion tests
- `AppFixture.java` — update fixture helpers for new link location
- `response-links.yaml` — may need header-sourced link test data
- `demo/PetResource.java` — no changes (demo app link definitions stay the same)

## Out of Scope

- Body links on nested/embedded objects in responses (issue #19)
- Attaching body links to relationship targets instead of parameter sources (issue #18)
- These are separate design questions deferred to their own issues
