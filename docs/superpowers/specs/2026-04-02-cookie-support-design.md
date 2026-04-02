# Cookie Support

## Summary

Add support for OpenAPI cookie parameters (`in: cookie`) in Try-it-out mode, and show an
inline note on documented `Set-Cookie` response headers explaining the browser limitation.

## Cookie Parameters (Sending)

Cookie parameters are already rendered in the UI with a "cookie" badge and
`data-param-in="cookie"` attribute. The only missing piece is the request submission logic.

### Changes

**app.js** (parameter routing, ~line 1030-1047): Add a `cookie` case that collects all
`in: cookie` parameters and combines them into a single `Cookie` header
(`name1=value1; name2=value2`). Currently cookie params fall through to the query string
default, which is incorrect.

**app.js** (httpie/curl copy modes): Include cookie params in the generated command. For curl,
use `-b "name1=value1; name2=value2"`. For httpie, use `Cookie:name1=value1\;name2=value2`.

### No changes needed

- `MethodFragmentGenerator.java` already renders cookie params with the correct badge and
  `data-param-in="cookie"` attribute.
- Cookie params behave like other params: text input, checkbox for booleans, persistence
  toggle, etc.

## Response Set-Cookie Headers

The Fetch API strips `Set-Cookie` from `response.headers` (forbidden response header name).
The actual value is never accessible to JS. However, if the OpenAPI spec documents Set-Cookie
as a response header, the existing response header infrastructure already shows it with its
description and schema.

### Changes

**app.js** (response header population, ~line 636-706): When a documented response header is
`set-cookie` (case-insensitive) and its value would show as "---" (absent from fetch response),
show the note "browser sends these automatically, not visible to JS" instead.

### No changes needed

- `MethodFragmentGenerator.java` already renders documented response headers including
  Set-Cookie via the existing infrastructure.
- The collapsible response headers section, auto-expand logic, and documented/undocumented
  separation all work as-is.

## Demo App

- Add a cookie parameter to one of the demo endpoints to exercise the feature E2E.
- Add a documented `Set-Cookie` response header to a demo endpoint so the inline note is
  visible.
