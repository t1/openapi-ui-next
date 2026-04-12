# Server & Headers Panes — Bulma Panel Restyle

## Problem

The server selector pane uses custom CSS for its collapsible container, toggle button, and
server body. Template server headings, preset labels, "+ Add preset" buttons, preset forms,
and preset delete buttons have **zero CSS** — they render with browser defaults.

The global headers panel duplicates the same custom collapsible pattern. Both panels look
inconsistent with Bulma components used elsewhere in the app.

## Solution

Replace both custom collapsible panels with Bulma's `panel()` component:

- `panel()` provides the outer container with border, radius, and shadow.
- `panel-heading` replaces `.server-toggle` / `.global-headers-toggle` as the collapsible
  header (same JS toggle behavior, just different CSS class).
- `panel-block` rows replace `.server-radio-label` for fixed servers.
- Template servers: one `panel-block` per template, containing a sub-heading + all preset
  rows + "+ Add preset" button inside it. Radio buttons in preset rows align at the same
  left edge as fixed-server radios.
- The preset form renders inside the template group block.
- `is-active` class highlights the selected server's panel-block (Bulma-native).

## Server Panel Structure

```
panel#server-selector
├── panel-heading (button)          "▶ Server  https://..."
├── panel-block.server-row          ● https://petclinic.example.com — Production
├── panel-block.server-row          ○ https://staging.example.com — Staging
├── panel-block.template-group      (one block per template server)
│   ├── .template-group-heading     https://{env}.api.example.com — Multi-env
│   ├── .template-preset-row        ○ https://prod.api.example.com
│   ├── .template-preset-row        ○ https://dev.api.example.com  ×
│   ├── .template-add-preset        [+ Add preset]
│   └── .template-preset-form       (when open: variable inputs + Save/Cancel)
├── panel-block#server-override     (OOB slot for per-operation overrides)
└── panel-block.custom-url-block    [+ Add custom URL]
```

## Global Headers Panel Structure

```
panel#global-headers
├── panel-heading (button)          "▶ Global Headers  (0)"
└── panel-block.global-headers-body (header rows inside)
```

## CSS Changes

**Remove:** `.server-toggle`, `.server-url`, `.server-body`, `#server-selector`,
`#server-selector.is-collapsed .server-body`, `.server-radio-label`,
`.server-description`, `.global-headers`, `.global-headers-toggle`,
`.global-headers-count`, `.global-headers-body`,
`.global-headers.is-collapsed .global-headers-body`.

**Add (minimal custom CSS on top of Bulma panel):**

- `.template-group-heading` — background `scheme-main-bis`, smaller font, link-colored
  monospace URL, description in `text-light`.
- `.template-preset-row` — flex row with same padding as `panel-block` for radio alignment;
  hover background; `is-active` highlight.
- `.template-preset-row .preset-delete` — danger color, opacity 0 by default, visible on
  row hover.
- `.template-add-preset button` — dashed border, same style as existing `.custom-url-add`.
- `.template-preset-form` — background `scheme-main-bis`, border-top separator.
- Collapse behavior: `#server-selector.is-collapsed .panel-block { display: none }` (same
  pattern as before, targeting panel-blocks instead of `.server-body`).
- Focus styles on panel-heading (outline on focus, matching existing toggle focus styles).

All colors use `var(--bulma-...)` CSS variables for dark mode compatibility.

## Java Changes

**`OpenApiUiGenerator.java`:**
- `serverSelector()`: replace `div().id("server-selector")` with `panel().id("server-selector")`.
  Use panel-heading for the toggle button. Wrap each fixed server in a panel-block label.
  Wrap each template server group (heading + presets + add-preset button) in a single
  panel-block div. Server override slot and custom-URL footer also become panel-blocks.
- `globalHeadersPanel()` (or equivalent): replace with `panel().id("global-headers")`.
  Panel-heading for the toggle. Panel-block for the body content.

**`OperationFragmentGenerator.java`:** No changes — the `#server-override` OOB swap targets
the same element id regardless of whether it's a div or a panel-block.

## JavaScript Changes

**`app.js`:**
- Toggle handler: change selector from `.server-toggle` to `.panel-heading` within
  `#server-selector` (or keep a class like `server-toggle` on the panel-heading for
  specificity).
- Collapse: toggle `is-collapsed` on `#server-selector` — CSS hides panel-blocks.
- `createTemplatePresetLabel()`: preset rows keep class `template-preset-row` (no change
  to structure, just the parent is now inside a panel-block instead of a server-body).
- Same for global headers toggle.
- Radio change handler and `updateBaseUrl()` remain unchanged.

## Test Impact

Existing browser tests in `BrowserTest.java` and helper methods in `AppFixture.java` use
CSS selectors like `.server-toggle`, `.server-body`, `.server-radio-label`,
`.global-headers-toggle`, `.global-headers-body`. These need updating to the new Bulma
panel selectors (`.panel-heading`, `.panel-block`, etc.). Behavioral assertions stay the same.

## Out of Scope

- Changing server selection behavior or localStorage persistence.
- Changing the per-operation server override mechanism.
- Changing the custom URL or template preset JavaScript logic.
- Restyling other parts of the UI.
