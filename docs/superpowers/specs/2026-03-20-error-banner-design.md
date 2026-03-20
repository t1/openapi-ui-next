# Error Banner with Retry for htmx Requests

## Goal

When the backend is unreachable, show a banner and automatically retry htmx fragment requests until the backend is back. Data requests (Send button) are unaffected.

## Scope

Only htmx requests (fragment loads via `hx-get`). Send/fetch data requests keep their existing inline error behavior unchanged.

## Behavior

1. **htmx request fails** (network error or HTTP error) → show a red banner at the bottom of the page (e.g. "Backend not reachable — retrying...") and start retrying the same request every 1 second.
2. **Retry succeeds** → hide the banner and swap the content into the target element as if nothing happened.
3. **Multiple failures** — if several htmx requests fail while the banner is already showing, each gets its own retry loop. When all retries have resolved, the banner hides.

## Banner

- Positioned at the bottom of the viewport, fixed.
- Bulma `notification is-danger` styling.
- Text: "Backend not reachable — retrying..."
- Hidden by default, shown via a CSS class toggle.

## Implementation

- Listen for `htmx:sendError` (network failure) and `htmx:responseError` (HTTP error) events on `document.body`.
- On error: show the banner, start a `setInterval(1000)` that fetches the same URL. On successful response, clear the interval, perform `htmx.ajax()` or manually set `innerHTML` on the target, and hide the banner if no other retries are active.
- Track active retry count to know when to hide the banner.

## Out of Scope

- Send/data request error handling (unchanged).
- Offline detection on page load.
