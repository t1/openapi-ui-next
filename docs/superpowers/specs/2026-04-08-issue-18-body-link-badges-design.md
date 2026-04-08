# Issue #18: Body Link Badges

## Problem

Response body links currently wrap the parameter source value in a clickable `<a>` tag.
For example, if a `GetOwner` link uses `ownerId ← $response.body#/owner/id`, the value `7`
becomes clickable. This is unintuitive — users don't expect clicking a numeric ID to navigate
to a different resource. It also fails when multiple links reference the same field.

## Design

Replace the clickable value with a **non-clickable value + clickable badge**:

- The JSON value renders normally (syntax-highlighted, not clickable).
- After the value, a small badge shows the OpenAPI link name and acts as the click target.
- Clicking the badge navigates to the target operation and fills parameters (same behavior as today).
- Multiple links on the same field render as multiple badges, stacked horizontally.

### Badge content

The badge shows the OpenAPI Link Object `name` — e.g., `GetOwner ↗`. The `↗` icon (Font Awesome
external-link) is already used in the current implementation and stays.

### CSS

- Badge: small font size, border, border-radius, link-colored text, pointer cursor.
- The current `.body-link` class is repurposed for the badge (no longer wraps the value).
- The dotted underline and bold font weight on values are removed.

### Multiple links per field

When multiple link expressions point to the same JSON path, each link gets its own badge.
The `pointerMap` in `app.js` already stores arrays of entries per pointer — the current code
uses `entries[0]`; the new code iterates all entries.

## Demo App Changes

Currently only `GET /pets/{id}` has one `@Link` (`GetOwner`). Add links across all three
resources to exercise the feature broadly:

### PetResource (`GET /pets/{id}`)

- **owner** — `operationId: getOwner`, `id ← $response.body#/owner/id` (existing, rename to `owner`)
- **visits** — `operationId: listPetVisits`, `petId ← $response.body#/id`

### OwnerResource (`GET /owners/{id}`)

- **pets** — `operationId: listOwnerPets`, `ownerId ← $response.body#/id`
  (requires adding `operationId` to `GET /owners/{ownerId}/pets`)

### VisitResource (`GET /pets/{petId}/visits/{visitId}`)

- **pet** — `operationId: getPet`, `id ← $response.body#/petId`
  (requires adding `operationId` to `GET /pets/{petId}/visits/{visitId}`)

### Link naming convention

Demo app link names use **lowerCamelCase without the `Get`/`List` prefix** — e.g., `owner`
instead of `GetOwner`, `visits` instead of `ListPetVisits`. This reads more naturally as a
badge label. The core rendering code uses the link name as-is from the OpenAPI spec; the
naming is purely a demo app convention.

## Test Fixture Changes

Update `response-links.yaml` to cover:

- Single badge on a value (existing case, updated rendering)
- Multiple badges on the same value
- Badge click navigates and fills parameters

## Out of Scope

- Heuristic link placement (moving links to "relationship" fields) — rejected
- Links section below JSON — rejected
- Links on nested/embedded array items (#19) — separate issue
