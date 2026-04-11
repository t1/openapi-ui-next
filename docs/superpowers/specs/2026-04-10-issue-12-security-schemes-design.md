# Security Scheme Support — Design Spec (#12)

## Goal

Display OpenAPI security scheme requirements on operations and provide credential input
for schemes that need user-provided values (apiKey, Bearer). Browser-handled auth
(Basic, cookies, OIDC) is shown as documentation only.

## Background

OpenAPI specs define security at two levels:

- **Global**: top-level `security` field applies to all operations by default
- **Per-operation**: an operation's own `security` field overrides the global default;
  `security: []` means explicitly public (no auth required)

Security schemes are defined in `components/securitySchemes` with a type (apiKey, http,
oauth2, openIdConnect, mutualTLS) and type-specific configuration.

Requirements use OR/AND logic:
- The `security` array is a list of alternatives (OR)
- Each element is an object mapping scheme names to scopes (AND within one element)

Example:
```yaml
security:
  - BearerAuth: []           # Alternative 1: Bearer alone
  - ApiKeyAuth: []            # Alternative 2: ApiKey AND OAuth2
    OAuth2: [read, write]
```

## Deployment Context

Currently the UI is always served from the same origin as the API (relative fetch URLs).
In this context:

- **HTTP Basic**: browser handles natively (401 → browser dialog → auto-attached header)
- **Cookie/session auth (OIDC, form login)**: browser sends cookies automatically
- **mutualTLS**: handled at TLS level
- **apiKey in header/query, Bearer token**: NOT automatic — user must provide values

When multi-server support lands (#13), cross-origin scenarios will need all auth to be
explicit. A note has been added to #13 about this dependency.

## Security Scheme Treatments

| Scheme | UI | Fetch |
|--------|----|-------|
| apiKey `in: header` | Input field, pinnable | Sent as request header |
| apiKey `in: query` | Input field, pinnable | Appended to fetch URL |
| apiKey `in: cookie` | Info-only row | None (browser handles cookies) |
| http `bearer` | Input field, pinnable | `Authorization: Bearer <value>` |
| http `basic` | Info-only row | None (browser handles natively) |
| oauth2 | Info-only row (flows, URLs, scopes) | None (browser session) |
| openIdConnect | Info-only row (discovery URL) | None (browser session) |
| mutualTLS | Info-only row | None (TLS-level) |

"Info-only" means: an auth-badged row in the parameter list showing scheme details as
text, but no input field.

## UI Design

### Placement

Auth parameters appear **inline with regular parameters** in the operation form, below
the spec-defined parameters. They use the same Bulma `.field` pattern (label + control)
with a visually distinct badge.

### Auth Badge

Instead of the regular `in` badges (`path`, `query`, `header`), auth params use a badge
showing the scheme type: e.g. `🔒 bearer`, `🔒 apiKey`, `🔒 oauth2`. The badge uses a
distinct color (warning/amber) to differentiate from regular parameter badges.

### Functional Auth Params (apiKey header/query, Bearer)

- Standard text input field
- Thumbtack pin icon (existing persist mechanism)
- Placeholder hints the expected format (e.g. "Bearer <token>", "your-api-key")
- `data-param-in` attribute: `auth-header` or `auth-query`

### Info-Only Auth Params (Basic, OAuth2, OIDC, mutualTLS, cookie apiKey)

- Same auth-badged row, but instead of an input field, descriptive text:
  - **http basic**: "Basic authentication (handled by browser)"
  - **oauth2**: flow type, authorization URL, token URL, scopes
  - **openIdConnect**: discovery URL
  - **mutualTLS**: "Mutual TLS (client certificate required)"
  - **cookie apiKey**: cookie name, "handled by browser"

### OR/AND Grouping

- **OR alternatives** use tabs, same pattern as response status code tabs
- **AND requirements** within a tab are listed together (multiple `.field` elements)
- Tab labels show scheme names: e.g. "Bearer", "ApiKey + OAuth2"
- **Single requirement** (no OR): no tabs, auth fields rendered directly
- Tab switching is static HTML (generated at build time, same as response tabs)

### No Security

Operations with `security: []` or no security defined (and no global security) show
no auth section.

## Fetch Integration

When "Try it out" sends a request:

1. Collect auth param values from the active security tab (if tabs exist)
2. For `auth-header` params: add to fetch headers
3. For `auth-query` params: append to URL as query parameters
4. Bearer values: auto-prefix with `Bearer ` if the value doesn't already start with it
5. Empty auth values are not sent

## Pin/Persist

Auth params support the existing pin mechanism:

- Storage key: `openapi-ui-param:auth-header:{METHOD}:{path}:{name}`
  (or `auth-query` for query apiKeys)
- Pinned values persist in localStorage across reloads
- Pin toggle via thumbtack icon or Ctrl+P keyboard shortcut

## Demo App

Add security scheme annotations to the Quarkus petstore demo:

- Define a Bearer security scheme and an apiKey scheme in `@SecuritySchemes`
- Apply Bearer globally to most endpoints
- Apply apiKey to specific operations (e.g. POST /pets)
- Make some operations explicitly public (`security: []`, e.g. GET /pets)

This exercises: scheme resolution, auth param rendering, OR/AND tabs, pin/persist,
and fetch integration.

## Sub-Issues

Decompose into sub-issues for incremental delivery:

1. **Infrastructure** — resolve effective security requirements per operation (global
   fallback, per-operation override, `security: []` opt-out); render auth section
   with badge; OR/AND tab grouping
2. **apiKey (header + query)** — input field, pin/persist, fetch integration (header
   and query parameter)
3. **http bearer** — input field, pin/persist, fetch with `Bearer` prefix
4. **Info-only schemes** — http basic, oauth2, openIdConnect, mutualTLS, cookie apiKey;
   documentation-only rows with scheme details
5. **Demo app** — add security scheme annotations, exercise all scheme types E2E

## Out of Scope

- Cookie-based apiKey input (can't set HttpOnly cookies from JS)
- OAuth2 authorization flow in the UI (users obtain tokens externally)
- Cross-origin auth behavior (deferred to #13)
- OpenID Connect discovery document fetching
