# HTTP Methods & Request Body Support

## Goal

Make POST, PUT, PATCH, and DELETE fully usable in the generated UI by adding request body input and extending the demo app to exercise all common HTTP methods.

## Current State

The generator already recognizes all HTTP methods with color coding and generates fragments for any method present in the spec. The detail pane renders identically for all methods: parameters, response schema preview, and send button. There is no request body input, so POST/PUT/PATCH cannot send data.

Additionally, the existing JavaScript has bugs that affect all non-GET methods:
- `fetch(url)` does not pass the HTTP method — always defaults to GET
- `curl` mode generates `curl URL` without `-X METHOD`

The demo app defines GET, POST, and DELETE endpoints. PUT and PATCH are missing.

## Design

### 1. Fix HTTP Method in All Modes

Before adding request body support, fix the existing method handling:

- **Try mode:** Pass `{ method: METHOD }` to `fetch()` for all operations.
- **curl mode:** Generate `curl -X METHOD URL` (currently omits `-X`).
- **httpie mode:** Already passes the method correctly (`http METHOD URL`).

### 2. Request Body Textarea

When an operation has a `requestBody` with `application/json` content, render a `<textarea>` in the generated fragment. Layout order: heading, summary, parameters, **request body textarea**, response schema preview, send button.

- Attribute: `data-request-body` for JS selection
- Pre-fill with a JSON skeleton derived from the request body schema
- Label "Request Body" with a content-type indicator (`application/json`)
- Style with monospace font and reasonable default height
- Applies to any method with a requestBody (including DELETE if the spec defines one)

### 3. JSON Skeleton Generation

Derive a skeleton from the schema's properties using simple type mapping. The swagger-parser pre-resolves `$ref` references, so `schema.getProperties()` returns resolved properties directly.

| Schema type | Skeleton value |
|-------------|---------------|
| string      | `""`          |
| integer     | `0`           |
| number      | `0`           |
| boolean     | `false`       |
| array       | `[]`          |
| object      | `{}`          |

Example: `{"name": "", "status": "", "age": 0}`

### 4. Frontend Behavior (with body)

When a `[data-request-body]` textarea is present in the fragment:

- **Try mode:** Include `body: textareaValue`, `method: METHOD`, and `headers: { 'Content-Type': 'application/json' }` in the `fetch()` call.
- **curl mode:** Generate `curl -X METHOD -H 'Content-Type: application/json' -d 'BODY' URL` — body content taken from textarea.
- **httpie mode:** Generate `echo 'BODY' | http METHOD URL Content-Type:application/json`.

### 5. Demo App Endpoints

Add to `PetResource`:

- `PUT /pets/{id}` — full pet replacement (accepts `Pet` request body, returns 200 with updated pet, 404 if not found)
- `PATCH /pets/{id}` — partial update (accepts a JSON object, merges non-null fields into existing pet, returns 200 with updated pet, 404 if not found). Since `Pet` is a record, create a new instance with merged values.

### 6. Error Handling

The existing error handling (display of `resp.status + resp.statusText + body` for non-ok responses, "Network error" for fetch failures) is sufficient. No additional error handling for invalid JSON or empty body — the server's 400 response will be displayed as-is.

## Out of Scope

- Form-field rendering from schema properties (future enhancement)
- Non-JSON content types (e.g., `multipart/form-data`, `application/xml`)
- Request body validation against schema in the UI
