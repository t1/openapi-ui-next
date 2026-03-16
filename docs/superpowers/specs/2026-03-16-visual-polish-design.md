# Visual Polish Design

Refine the OpenAPI UI's visual presentation toward a cohesive, professional look.
Stays within Bulma's component model where possible; adds targeted custom CSS
where needed (the project already has ~100 lines of custom CSS).

## Overall Direction

Refined / Neutral: subtle depth, standard controls. Professional without being
cold or flashy. Evolve what exists rather than restyle from scratch.

## Changes

### 1. Layout Depth — Inverted Box Model

**Current**: tree on flat light-gray sidebar, detail pane in a Bulma Box.
Conflict: the Box floats with shadow while the tree is visually flat.

**New**: invert the relationship. The tree lives inside a floating Bulma Box
(full height of the content area). The detail content sits directly on the
background surface. The tree becomes the "object," the content becomes the
"ground."

Both halves share a continuous gray background (Bulma's `$scheme-main-bis`).
The tree Box uses Bulma's standard box shadow.

**Tree box height**: the tree column uses flexbox (`display: flex`,
`flex-direction: column`) and the Box gets `flex: 1` to fill the column height.

**Detail pane**: remove the `box()` wrapper, replace with a plain `div()` that
preserves the existing `id="detail"` and `tabindex` attributes (needed for HTMX
targeting and keyboard navigation). Content blocks (code, inputs, pre) that
currently use scheme-main-bis backgrounds should switch to white backgrounds
so they remain visually distinct from the gray ground.

### 2. Mode Toggle — Segmented Control

**Current**: three separate Bulma buttons with teal active state.

**New**: iOS/macOS-style segmented control. A gray pill container with the active
item rendered as a white segment with subtle shadow. Inactive items are plain text
on the gray background.

Implementation: a `<div>` with `display: flex`, gray background, small border-radius,
2px padding. Active child gets white background, border-radius, and
`box-shadow: 0 1px 2px rgba(0,0,0,0.06)`. The existing JS that toggles
`is-selected` should instead toggle a CSS class (e.g. `is-active`) that applies
the white/shadow treatment.

### 3. Path Parameters — Lighter Weight Only

**Current**: path params have purple color + italic + curly braces — triple-encoding.

**New**: curly braces (from the spec, non-negotiable) plus lighter text weight only.
No italic, no special color. Use a lighter gray (e.g. Bulma's `$grey-light`)
vs. the bolder segment names.

### 4. Method Colors — Keep Bulma Semantic

No change. Continue using Bulma's semantic color classes:
- GET → `is-success` (green)
- POST → `is-link` (blue)
- PUT → `is-warning` (yellow)
- DELETE → `is-danger` (red)
- PATCH → `is-primary` (turquoise)

Zero custom color CSS needed.

### 5. Tree Method Indicators — Grouped Tag Addon

**Current**: separate small pills per method, spaced apart. Takes significant
horizontal space, especially with 3+ methods.

**New**: fuse all method tags for a path into a single grouped addon block.
Shared outer border, shared border-radius, methods separated by internal
borders. Reads as one compact unit instead of scattered pills.

Implementation: a container `<span>` with `display: inline-flex`,
`border-radius: 3px`, `overflow: hidden`, `border: 1px solid` (Bulma's
`$border` color). Each method tag inside is a `<span>` with its Bulma
semantic light background, no individual border-radius, separated by
`border-right: 1px solid $border`. Keep the current tag font size (`0.65rem`),
padding `2px 4px`.

## Out of Scope

- Method color changes (kept as Bulma defaults)
- Typography changes (keep Bulma defaults)
- JSON syntax highlighting (separate feature)
- Mobile layout changes
- Dark mode
