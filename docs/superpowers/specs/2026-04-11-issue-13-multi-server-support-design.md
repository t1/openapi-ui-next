# Multi-Server Support (#13)

Support all three OpenAPI server levels (global, per-path, per-operation) with template
variables, user-defined presets, custom URLs, persistence, and cross-origin security awareness.

## Server Selector UI

### Header Display

The resolved server URL is shown inline in the header bar, between the page title and the
mode toggle. It is always a clickable toggle (▶/▼) that expands/collapses the server panel.
Even with a single non-template server, users can expand to add custom URLs.

### Server Panel (collapsible)

The panel appears below the header when expanded. Its sections, top to bottom:

1. **Global servers** — a radio button for each entry in the spec's top-level `servers` array.
   - **Non-template servers:** a single radio button labeled with the URL.
   - **Template servers** (URLs with `{variable}` placeholders): the template URL is shown,
     with presets listed indented below it, each as a radio button labeled with the resolved
     URL. If all variables have defaults, a preset with the default values is pre-generated
     automatically. A "+ Add preset" button per template opens an inline form with input
     fields for each variable (dropdowns for enum-constrained variables). Saving creates a
     new radio entry labeled with the resolved URL. User-created presets can be deleted;
     the default preset cannot.
   - Multiple template servers are supported — each has its own presets.
   - If the spec defines no `servers` field, a single entry shows the resolved origin URL
     (e.g. `http://localhost:8080`), resolved client-side in JS since it depends on where
     the page is served from.

2. **Server override slot** (`#server-override`) — normally empty and hidden. Populated via
   HTMX out-of-band swap when an operation with per-path or per-operation servers loads.
   When populated:
   - A warning indicator is shown: "The operation selected below uses different servers".
   - All global servers, their presets, and custom URLs above are visually disabled
     and functionally ignored — the override server is used for requests.
   - The override content contains radio buttons for the operation's own server list.
   When the user navigates to an operation without overrides, the slot is cleared
   (empty OOB swap) and global servers re-enable.

3. **"+ Add custom URL"** — adds a new radio option with an editable URL text field.
   Consistent with the existing "add custom header" pattern. Custom URLs can be deleted.

### Per-Operation Server Overrides

OpenAPI allows servers at three levels with inheritance: global → per-path → per-operation.
The generator resolves this inheritance at build time and embeds the effective server list
in each operation fragment.

Every operation fragment (e.g. `GET.html`) includes a `<div id="server-override"
hx-swap-oob="innerHTML">` element:
- Operations with their own servers (per-path or per-operation): the element contains
  the override UI (warning badge + radio buttons for the operation's servers).
- Operations without overrides: the element is empty, clearing any previous override.

HTMX handles the DOM swap automatically via OOB processing. JS listens on
`htmx:oobAfterSwap` to disable/enable the global server radios based on whether the
override slot has content.

## HTML Structure

- **`index.html`** — the server panel is generated inline, including the global server
  radio buttons, preset sections, the override slot (`#server-override` — initially empty),
  and the "+ Add custom URL" button. Expand/collapse is pure CSS/JS — no HTMX load needed.
- **Operation fragments** (`GET.html`, `POST.html`, etc.) — each includes an OOB swap
  element for `#server-override`, either populated (override) or empty (no override).

## Persistence

All user state is stored in localStorage and survives page reloads:
- **Selected server** — which radio button is active (key: `openapi-ui-server`)
- **Variable presets** — user-created variable combinations per template server
- **Custom URLs** — user-added custom URL entries

## Security Scheme Integration

When the selected server is **cross-origin** (different origin from `window.location.origin`),
security inputs (API key headers, Bearer tokens, query parameters) are automatically shown
on operations that require them — even for security schemes that would normally be handled
by the browser for same-origin requests (Basic auth, cookies).

When switching to a **same-origin** server, those extra inputs are hidden again, since the
browser can handle authentication natively.

The origin comparison happens client-side in JS whenever the active server changes.

## Data Flow

### Build time (Java generator)

- Read `servers` from all three spec levels (global, per-path, per-operation).
- Resolve per-path/per-operation inheritance.
- Generate the server panel inline in `index.html` with the global server list, template
  variables (defaults, enums), and panel structure.
- For each operation fragment, generate the OOB swap element: populated if the operation
  has its own servers, empty otherwise.

### Runtime

- On toggle expand/collapse: pure CSS/JS show/hide.
- On operation fragment load: HTMX OOB-swaps the `#server-override` slot automatically
  (no JS needed for the swap itself).
- JS handles: preset creation, custom URL addition, persistence (localStorage read/write),
  resolving template variables, updating the header display, disabling/enabling global
  radios on `htmx:oobAfterSwap`, and cross-origin security input visibility.
- The active server's resolved URL is set as the base URL for try/httpie/curl requests
  (currently stored in `data-base-url` on the mode toggle element).
