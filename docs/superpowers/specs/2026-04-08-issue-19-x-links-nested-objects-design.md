# x-links: Links on nested/embedded objects in response bodies

Issue: #19 (sub-issue of #4)

## Problem

OpenAPI Link Objects use Runtime Expressions with JSON Pointer (RFC 6901) to reference
response body values. JSON Pointer has no wildcard or array-traversal syntax — you can
reference `$response.body#/visits/0/id` (first element) but not "every element's id."

This makes it impossible to link individual items in embedded arrays (e.g., each visit in
a `visits[]` array) to their detail operations (e.g., `GET /visits/{visitId}`).

## Solution

An OpenAPI extension `x-links` on the response object. Structurally identical to standard
`links`, with one addition: body expressions support `[*]` as an array wildcard.

## Extension Syntax

`x-links` sits alongside `links` on the response object. It uses the same
[Link Object](https://spec.openapis.org/oas/v3.0.3#link-object) structure: `operationId`,
`description`, `parameters`, `server`.

### Expression types

- **Body:** `$response.body#/path[*]/field` — JSON Pointer path with `[*]` for array wildcard
- **Header:** `$response.header.X-Foo` — same as standard links, no array semantics

### Example

```yaml
responses:
  200:
    description: A pet with embedded visits
    content:
      application/json:
        schema:
          type: object
          properties:
            id: {type: integer}
            name: {type: string}
            owner:
              type: object
              properties:
                id: {type: integer}
                name: {type: string}
            visits:
              type: array
              items:
                type: object
                properties:
                  id: {type: integer}
                  date: {type: string}
                  reason: {type: string}
                  veterinarian:
                    type: object
                    properties:
                      id: {type: integer}
                      name: {type: string}
    links:
      GetOwner:
        operationId: getOwner
        parameters:
          ownerId: $response.body#/owner/id
    x-links:
      GetVisit:
        operationId: getVisit
        description: Get details of this visit
        parameters:
          visitId: $response.body#/visits[*]/id
      GetVeterinarian:
        operationId: getVet
        parameters:
          vetId: $response.body#/visits[*]/veterinarian/id
```

### Rules

- `[*]` matches every element in an array at that position in the path.
- Multiple `[*]` segments are allowed for nested arrays
  (e.g., `$response.body#/visits[*]/treatments[*]/id`).
- The link is instantiated once per matching array element, with the actual value substituted.
- `x-links` and `links` can coexist on the same response — both are processed.
- Header expressions (`$response.header.X-Foo`) work identically to standard links (no array
  semantics).
- `[*]` is only recognized in `x-links` expressions. Standard `links` expressions are
  unchanged — a `[*]` in a standard link expression is treated as a literal path segment
  (per JSON Pointer).

## Schema View Rendering

`x-links` render in the response schema view identically to standard links — as inline link
rows below the property they reference.

The `[*]` segments are stripped for schema path matching. The schema renderer already treats
arrays as transparent (walking through `items` without inserting index segments), so
`$response.body#/visits[*]/id` matches schema path `visits/id`. The link row
(`→ GetVisit`, `visitId ← id`) appears below the `id` property inside the `visits` array
items schema.

No new visual pattern — the existing `SchemaRenderer.addPropertyRow()` gains an additional
link source (`x-links` alongside `links`).

## Body View Rendering

After "Try it out", the body link resolver expands `[*]` expressions against the actual
response JSON.

For `$response.body#/visits[*]/id` with body `{"visits": [{"id": 10}, {"id": 11}]}`, the
resolver produces indexed pointer map entries:

```
pointerMap["visits/0/id"] = [{operationId: "getVisit", paramName: "visitId", value: "10", linkName: "GetVisit"}]
pointerMap["visits/1/id"] = [{operationId: "getVisit", paramName: "visitId", value: "11", linkName: "GetVisit"}]
```

The existing `renderJsonWithLinks` already builds paths with numeric indices during JSON
tree traversal, so badges appear on each array element's value with no changes to the
rendering logic.

For deeper nesting (`$response.body#/visits[*]/veterinarian/id`), expansion recurses through
each visit object to resolve `veterinarian/id`.

Multiple `[*]` segments expand combinatorially — each array is iterated at its level.

### Visual presentation

Same badge style as existing body links — each linked value gets a `→ LinkName` badge
(Option A: per-item badges, consistent with top-level links).

### Click behavior

Navigate to the target operation and fill parameters. No auto-send — the user clicks Send
after reviewing.

## Implementation Approach

Extend existing hand-rolled code; no new libraries.

### Java (schema side)

- `OperationFragmentGenerator`: read `x-links` from the response extensions map alongside
  standard `links`.
- `parseResponseSource`: strip `[*]` from body expressions before matching against schema
  property paths.
- `responseLinksData`: serialize `x-links` into the `data-response-links` attribute alongside
  standard links.

### JavaScript (body side)

- `applyBodyLinks`: when parsing expressions, detect `[*]` segments. For each `[*]`, iterate
  the array at that position in the parsed JSON and recurse with the remaining path segments.
  Produce indexed pointer map entries (`visits/0/id`, `visits/1/id`, etc.).
- `renderJsonWithLinks`: no changes needed — already builds indexed paths.

## Demo App

Add `x-links` to `PetResource`'s `GET /pets/{id}` response via JAX-RS `@Extension`
annotation. The exact annotation syntax depends on SmallRye OpenAPI's support for complex
extension values — this will be determined during implementation. The extension must produce
this OpenAPI output:

```yaml
x-links:
  GetVisit:
    operationId: getVisit
    description: Get details of this visit
    parameters:
      visitId: $response.body#/visits[*]/id
```

This links each embedded visit to `GET /pets/{petId}/visits/{visitId}`.

Requires `GET /pets/{petId}/visits/{visitId}` to have an `operationId` (verify or add).

## Testing

- **Schema view test:** `x-links` render link rows below array item properties in the
  response schema (e.g., `→ GetVisit` below `id` inside `visits` items).
- **Body link test:** After sending a request, each array item's linked value gets a
  `→ LinkName` badge.
- **Click navigation test:** Clicking an array item's body link badge navigates to the target
  operation with the parameter filled.
- **Deeper nesting test:** Test fixture with two levels of `[*]` to verify multi-level array
  traversal.
- **Coexistence test:** `links` and `x-links` both render on the same response.
- **Test YAML fixture:** Extend `response-links.yaml` with `x-links` entries.

## Documentation

Update `README.md` Features section to document the `x-links` extension:

- What it is and why (standard links can't express array traversal)
- Expression syntax with `[*]` wildcard
- Same Link Object structure as standard `links`
- Header expression support (no array semantics)
- Brief YAML example
- Schema view and body view rendering
