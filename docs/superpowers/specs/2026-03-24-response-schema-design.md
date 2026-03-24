# Response & Request Schema Design

## Context

The OpenAPI UI currently shows a basic JSON schema preview (property names + types as pseudo-JSON)
between the description and Send button. This is confusing — it looks like an actual response, it's
unstyled, and it's positioned as if it were a request parameter. The current implementation also
only handles the JSON 200 response schema.

This design introduces proper schema documentation for both request bodies and responses, with
clear visual separation between documentation (speculative) and actual content (real data).

## Design

### Layout Order

1. **Operation header** — method badge, path, description
2. **Parameters** — path/query params as input fields (existing)
3. **Body box** (POST/PUT/PATCH only) — request body textarea + schema
4. **Response box** — Accept select + response schema
5. **Send button** — with status badge
6. **Actual response** — syntax-highlighted response data

### Body Box (request)

A dashed-border box titled **"Body"** with a **"Schema ▸/▾"** toggle on the right.

- **Collapsed**: title bar + textarea visible (textarea is always shown since it's an input)
- **Expanded**: textarea on the **left**, property tree on the **right** (side by side, separated
  by a subtle vertical border)

The property tree shows:
- Property name (colored, monospace)
- Type badge (`string`, `integer`, `enum`, `object`, etc.)
- Required badge (red) where applicable
- Inline examples in muted italic (e.g. `e.g. "Max"`, `available | adopted`)

### Response Box

A dashed-border box with the title row:
- **"Response"** label (left)
- **"Accept"** label + content type `<select>` (inline, after title) — only shown when multiple
  response content types are documented
- **"Schema ▸/▾"** toggle (right)

When expanded:
- **Status code tabs** — one tab per documented status code (200, 201, 404, 500, etc.),
  colored green for 2xx, red for 4xx/5xx
- **Property tree** — same format as request schema, adapts to selected status code and content type

When collapsed:
- Single line with title + Accept select + toggle. Status codes and content types shown as
  muted hints next to the toggle.

### Accept Select Behavior

- Controls the `Accept` header sent with fetch requests
- Controls which schema variant is shown (if spec documents different schemas per content type)
- Always visible even when Schema is collapsed (it's a request parameter)
- Hidden entirely when only one response content type is documented

### Actual Response vs Schema — Visual Distinction

| Aspect         | Schema (documentation)          | Actual response (real data)     |
|----------------|----------------------------------|----------------------------------|
| Border         | Dashed (`border: 1px dashed`)   | Solid (`border: 1px solid`)     |
| Background     | Muted (`--bulma-scheme-main`)   | White/prominent                 |
| Position       | Above Send                      | Below Send                      |
| Labels         | "Schema" toggle, type badges    | Status badge (200 OK)           |
| Auto-behavior  | Collapses when response arrives | Appears prominently             |

### GET vs POST

- **GET**: No Body box. Only Response box + Send + actual response.
- **POST/PUT/PATCH**: Body box (with textarea + schema) + Response box + Send + actual response.
- **DELETE**: Typically no Body, but Response box shows if responses are documented.

### Styling

- Schema property trees use the same Bulma CSS variables as syntax highlighting
  (`--bulma-warning` for names, `--bulma-link` for types, etc.)
- Type badges use muted background (`#f0f0f0` / Bulma equivalent)
- Required badges use danger-tinted background
- Examples in `--bulma-text-weak`, italic
- Dark mode: all adapts via Bulma CSS variables

## Files to Modify

- `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java` — rewrite
  response schema generation; add request schema; restructure fragment layout
- `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css` — schema box styles,
  property tree styles, toggle styles
- `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` — schema toggle behavior,
  status code tab switching, auto-collapse on response, Accept header from select
- `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java` — fixture methods for
  schema interactions
- `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` — tests for schema
  display, toggle, status code tabs, Accept header
- `core/src/test/resources/*.yaml` — test specs with rich response schemas, multiple status codes
- `demo/` — extend demo endpoints with richer response documentation

## Verification

- `mvn test -pl core` with `dangerouslyDisableSandbox: true`
- Review screenshots for visual correctness in both light and dark mode
- Verify schema auto-collapses when actual response appears
- Verify Accept header is sent from Response box select
- Build and run demo app to exercise the feature E2E

## Mockups

Visual mockups are in `.superpowers/brainstorm/` — see `response-schema-v4.html` for the
approved design.
