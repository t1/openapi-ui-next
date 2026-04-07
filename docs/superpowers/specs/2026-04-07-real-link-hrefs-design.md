# Real `<a href>` Links with Query Parameters

**Issue:** Follow-up to #4 (response links)
**Date:** 2026-04-07

## Problem

The current body-link and schema-link implementations use custom JS click handlers with
`data-*` attributes and `window._operationIdMap` lookups. Body links additionally use regex-based
HTML manipulation (`applyBodyLinks()`) to wrap values in the highlighted JSON response. This
regex approach matches by the last segment of the JSON pointer key name, which breaks when
duplicate keys appear at different nesting levels (e.g., `pet.id` vs `owner.id`).

Schema links use `<span>` elements with `data-operation-id` — not real links, so they can't be
right-clicked, bookmarked, or opened in new tabs.

## Solution

Replace both mechanisms with real `<a href>` links. Extend the hash format to support query
parameters for pre-filling form fields.

## Hash Format

Current: `#path/METHOD` or `#[Tag]path/METHOD`
New: `#path/METHOD?param1=value1&param2=value2`

Query parameters are optional. When present, `navigateFromHash()` parses them and fills the
corresponding form input fields after the htmx swap completes.

## Body Links

**Delete** `applyBodyLinks()` and its regex-based HTML manipulation entirely.

Replace with a new function that walks the parsed JSON response and the link metadata together.
For each value at a JSON pointer path that matches a link parameter, wrap it in a real `<a>`:

```html
<a class="body-link" href="#owners/{ownerId}/GET?ownerId=7">7</a>
```

The href is computed by looking up the `operationId` in `_operationIdMap` to get the target
path and method, then appending matched parameters as a query string.

Because wrapping happens during structured JSON traversal (not regex against flattened HTML),
the full path context is available — there is no ambiguity between duplicate keys at different
nesting levels.

## Schema Links

Replace `<span data-operation-id="..." class="schema-link-name">` with
`<a href="#path/METHOD" class="schema-link-name">`. Generated server-side in
`OperationFragmentGenerator.responseLinks()`.

Similarly for `schema-link-operation` elements.

## navigateFromHash()

Extend to parse query parameters from the hash:

1. Split hash on `?` — left side is the route (existing logic), right side is query string.
2. Parse query string into key-value pairs.
3. After htmx swap completes, fill matching form `input[name="..."]` fields with the values.
4. Position in the afterSwap sequence: same as current `_pendingParamFill` (after pinned value
   restoration and fieldCache restoration).

## Pinned Values Interaction

Query parameter values override pinned values for display, same as the current `_pendingParamFill`
behavior. The stored pin in localStorage is unchanged — navigating to the operation normally
later restores the pinned value. The pin icon stays pressed.

Further pin management improvements (multiple pin sets, override visualization) are tracked
in issue #17.

## What Gets Deleted

- `applyBodyLinks()` regex-based HTML manipulation (app.js) — replaced by structured JSON renderer
- Body-link click handler (app.js) — navigation works via real `<a href>`
- `window._pendingParamFill` and its consumption code (app.js) — replaced by `_pendingParams`
- Schema-link click handler (app.js) — navigation works via real `<a href>`
- `data-operation-id` attributes on schema link elements — replaced by `href`
- `embedResponseLinksDataOnForm` test

Note: `data-response-links` attribute and `responseLinksData()` stay — the JS still reads
link metadata from the form attribute to know which response values to wrap.

## What Gets Added/Changed

- `navigateFromHash()`: parse `?key=value` from hash, fill form fields after swap
- New body-link rendering function: walk parsed JSON + link metadata, produce `<a href>` tags
- Schema links: Java code generates `<a href>` instead of `<span data-operation-id>`
- `_operationIdMap` stays — needed at render time to resolve operationId to path/method

## Bookmark Support

Comes for free. Users can share or bookmark URLs like
`#pets/{petId}/GET?petId=42` and the fields will be pre-filled on load.
