# OpenAPI UI — Design Spec

## Problem

Developers need a keyboard-driven, visually clean way to explore and try out APIs described by OpenAPI specs. Existing tools (Swagger UI, Redoc) are mouse-heavy and ship as runtime applications. This tool generates static HTML files that work with any static file server.

## MVP Scope

GET requests only. Path parameters and query parameters. No auth, no request bodies, no POST/PUT/DELETE.

## Architecture

Maven multi-module project:

```
openapi-ui-next/
├── pom.xml            # parent POM
├── core/              # spec parsing + HTML generation
├── maven-plugin/      # Maven plugin wrapping core
├── cli/               # CLI fat jar with shell script header
```

### Core

Parses an OpenAPI spec file and generates a directory of static HTML files.

**Dependencies:**
- `swagger-parser-v3` — parses OpenAPI 3.0/3.1 specs, resolves `$ref`
- `bulma-java` — programmatic HTML generation with Bulma CSS components
- HTMX — bundled as a static JS file

### Maven Plugin

Thin wrapper around core. Configured via `<configuration>` in the POM with spec file path and output directory.

### CLI

Fat jar with shell script header (really executable jar). Accepts spec file path and output directory as arguments.

## Output Structure

Given a spec with paths `/pets` and `/pets/{petId}`, the generator produces a directory of static files. Directory names use literal path segments including braces (e.g. `{petId}` is the actual directory name). HTMX fragments are loaded via `hx-get` with relative URLs, which requires an HTTP server (not `file://` protocol).

```
output/
├── index.html              # path tree + Bulma + HTMX + keyboard nav JS
├── htmx.min.js
├── pets/
│   └── GET.html            # HTMX fragment for GET /pets
└── pets/{petId}/
    └── GET.html            # HTMX fragment for GET /pets/{petId}
```

`index.html` contains the full page: Bulma CSS, HTMX, keyboard navigation JS, and the hierarchical path tree. Each operation gets its own HTML fragment loaded via HTMX.

## UI Design

### Layout

Two-panel: path tree on the left, detail pane on the right. Responsive — stacks vertically (tree on top, detail below) on narrow screens. Uses Bulma's `columns` with `is-desktop` breakpoint.

### Theming

Automatic light/dark mode via Bulma's `prefers-color-scheme` support. No manual toggle needed.

### Keyboard Navigation

Arrow-key based. At tree boundaries (first/last node), keys that would move beyond the boundary do nothing.

| Key | Action |
|-----|--------|
| Up/Down | Move focus between tree nodes |
| Right | Expand path node / enter operations |
| Left | Collapse path node / move to parent |
| Enter | Load operation's HTMX fragment into detail pane |
| Tab | Move focus from tree to detail pane |
| Escape | Return focus to tree |

Focused tree node gets a visible highlight.

### Path Tree

Hierarchical. Paths are grouped by segments. All HTTP methods for the same path are grouped under the path node. (MVP: only GET.)

### Operation Detail (HTMX Fragment)

Each fragment shows:
- HTTP method and full path
- Path parameters with input fields
- Query parameters with input fields
- Response schema
- Action button (behavior depends on global mode)

### Response Rendering

In Try mode, responses are rendered inline:
- **JSON** — prettified (formatted/indented)
- **HTML, XML, YAML** — displayed as-is (no pretty printing)
- **Other content types** — displayed as plain text

### Global Mode Switch

One toggle at the top of `index.html` with three modes:

| Mode | Behavior |
|------|----------|
| Try | Sends request from browser via fetch, shows response inline |
| httpie | Copies `http GET ...` to clipboard |
| curl | Copies `curl ...` to clipboard |

The base URL comes from the spec's `servers` array (first entry; falls back to `/` if `servers` is empty or absent, per the OpenAPI 3.0 spec default). Parameter values filled into the form are reflected in the constructed command/request.

## Development Approach

TDD (red-green-refactor) using the tdder skills. Unfolding architecture — build complexity only on demand.

### Testing

- **Unit tests (JUnit):** core logic, HTML generation
- **Integration tests (Playwright):** generated HTML/HTMX behavior served via a local HTTP server in the test harness. Key test areas:
  - Keyboard navigation
  - Responsive layout
  - Parameter forms and URL construction
  - Copy-to-clipboard (curl/httpie)
  - Try mode: JSON (prettified), HTML, XML, YAML responses
  - HTMX fragment loading

## Tech Stack

| Concern | Choice |
|---------|--------|
| Build | Maven multi-module |
| OpenAPI parsing | `swagger-parser-v3` |
| HTML generation | `bulma-java` (programmatic) |
| UI interactivity | HTMX (bundled) |
| CSS | Bulma (via bulma-java, auto light/dark) |
| Keyboard nav | Vanilla JS, bundled in index.html |
| CLI packaging | Fat jar with shell script header |
| Development | TDD with tdder skills |
| Testing | JUnit + Playwright |
