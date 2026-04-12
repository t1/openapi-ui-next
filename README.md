# OpenAPI UI Next

[![Java CI](https://github.com/t1/openapi-ui-next/actions/workflows/maven.yml/badge.svg)](https://github.com/t1/openapi-ui-next/actions/workflows/maven.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.t1/openapi-ui)](https://central.sonatype.com/artifact/com.github.t1/openapi-ui)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Generates static, keyboard-navigable HTML UIs from OpenAPI specifications; via CLI or Maven plugin.

![OpenAPI UI Next: light](docs/screenshots/hero.png#gh-light-mode-only)
![OpenAPI UI Next: dark](docs/screenshots/hero-dark.png#gh-dark-mode-only)

## Why?

When Swagger came out, I loved it! Esp. the "Try it out" was a real game changer. But APIs keep getting bigger and
more complex, yet Swagger didn't grow alongside UX-wise. I absolutely need to be able to quickly navigate APIs with the
keyboard and have not only a nice-looking, but a clear and poweruser-oriented view on the API.

In contrast, Swagger tends to show everything at once: `curl` commands you didn't ask for, response schemas that look
just like actual responses, and deeply nested sections that bury the information you need, while schema objects are
cluttered at the end. I often find myself copying ids from here to there and back. _OpenAPI UI Next_ keeps the UI clean
by showing details on demand: schema documentation, response types, code snippets, etc. it's there when you want them,
but hidden when you don't.

With big schemas, performance is also an issue: The OpenAPI UI tools I know (Swagger UI, Redoc (even the generated
variant), Rapidoc) are slow by design: they are JavaScript-heavy SPAs that parse the spec at runtime in the browser.
This means slow initial loads on large specs. _OpenAPI UI Next_ takes a different approach: it generates plain HTML +
CSS at build time. The result is a set of static files that can be served from any web server or embedded in any
backend, so they load instantly. The dynamic UX is provided mainly by HTMX, e.g. loading method fragments on click.

> This is also my playground for learning how to vibe-code at the Harness Engineering level.

## Features

**Navigation & Layout**

- Path-based or tag-based tree views with method badges
- Resizable split pane with persistent width
- Method tabs for switching operations on the same path
- Keyboard navigation: cursor keys for spacial navigation in addition to Tab/Shift+Tab
- Pin values, so you don't have to repeat them everywhere, quickly with Ctrl+P (Alt on Linux and Windows);
  kept in local storage, so it survives reloads
- URL hash navigation: bookmarkable deep links (`#pets/GET`, `#[billing]invoices/GET`)
- Responsive layout (desktop: side-by-side; mobile: stacked)
- Automatic dark mode support (uses system setting)

<details>
<summary>Tag-based tree view</summary>

![Tag view: light](docs/screenshots/tag-view.png#gh-light-mode-only)
![Tag view: dark](docs/screenshots/tag-view-dark.png#gh-dark-mode-only)
</details>

**API Documentation**

- Operation summary, description, deprecated badge, tags, external docs
- Parameter inputs with type (path/query/header) and required badges
- Request body editor with JSON skeleton and schema documentation
- Response schema with status code tabs, property types, required markers, and examples
- Collapsible nested object and array properties in schema views
- `x-links` extension for array item links: when a response embeds an array of sub-resources
  (e.g., a pet's visits), `x-links` lets you declare per-item links using `[*]` as an array
  wildcard in the parameter expression (e.g., `$response.body#/visits[*]/id`). These render
  as clickable badges on each array element in the response body, and as link rows in the
  schema view: same visual treatment as standard OpenAPI Links.
- Accept header select for multi-content-type endpoints
- Boolean parameters rendered as checkboxes (two-state: omit or send `true`; explicitly
  sending `false` is not supported -- a three-state control would add UX complexity for a
  rare need, since most boolean query params are opt-in flags with a `false` default)

<details>
<summary>Parameter inputs</summary>

![Parameters filled: light](docs/screenshots/params-filled.png#gh-light-mode-only)
![Parameters filled: dark](docs/screenshots/params-filled-dark.png#gh-dark-mode-only)
</details>

<details>
<summary>Response schema tabs</summary>

![Response schema tabs: light](docs/screenshots/response-schema-tabs.png#gh-light-mode-only)
![Response schema tabs: dark](docs/screenshots/response-schema-tabs-dark.png#gh-dark-mode-only)
</details>

<details>
<summary>Nested schema expanded</summary>

![Nested schema expanded: light](docs/screenshots/nested-schema-expanded.png#gh-light-mode-only)
![Nested schema expanded: dark](docs/screenshots/nested-schema-expanded-dark.png#gh-dark-mode-only)
</details>

<details>
<summary>x-links for array items</summary>

Standard OpenAPI Links can't express "for each item in an array, link to its detail operation"
because JSON Pointer has no wildcard syntax. The `x-links` extension uses the same Link Object
structure but supports `[*]` in body expressions:

```yaml
responses:
  200:
    links:
      GetOwner:
        operationId: getOwner
        parameters:
          ownerId: $response.body#/owner/id
    x-links:
      GetVisit:
        operationId: getVisit
        parameters:
          visitId: $response.body#/visits[*]/id
```

After "Try it out", each visit's `id` in the response body gets a clickable `→ GetVisit` badge
that navigates to the target operation and fills in the parameter. Multiple `[*]` segments are
supported for nested arrays (e.g., `$response.body#/visits[*]/treatments[*]/id`).
</details>

**Request Headers**

- Global headers panel: set headers that apply to all requests; pin them when you want
- Per-operation custom headers: add arbitrary, pinnable headers per endpoint

**Try It Out**

- Three modes: Try (fetch), httpie, curl; switch at any time with Ctrl+1/2/3 (Alt on Linux and Windows)
- Response rendering with syntax highlighting (JSON, HTML, XML, YAML)
- Collapsible response headers display with spec documentation: documented headers show descriptions,
  deprecated indicators, and required-but-missing warnings; undocumented headers are hidden behind
  a "Show all" button when documented headers exist; auto-expands on first send when documented headers are present

<details>
<summary>Try mode with JSON response</summary>

![Try mode: light](docs/screenshots/try-mode-json-response.png#gh-light-mode-only)
![Try mode: dark](docs/screenshots/try-mode-json-response-dark.png#gh-dark-mode-only)
</details>

## Usage

### CLI

```bash
jbang openapi-ui@t1/openapi-ui-next <spec-file> <output-dir>
```

Or install it as a command:

```bash
jbang app install openapi-ui@t1/openapi-ui-next
openapi-ui <spec-file> <output-dir>
```

Or build and run it locally:

```bash
mvn -pl cli package
./cli/target/openapi-ui <spec-file> <output-dir>
```

Use `--verbose` for debug logging and full stack traces on errors.

To preview the result locally:

```bash
python3 -m http.server 8000 -d <output-dir>
```

Then open http://localhost:8000.

### Maven Plugin

```xml
<plugin>
  <groupId>com.github.t1</groupId>
  <artifactId>openapi-ui-maven-plugin</artifactId>
  <version>${project.version}</version>
  <executions>
    <execution>
      <phase>process-classes</phase>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <specFile>${project.build.directory}/generated/openapi.yaml</specFile>
        <outputDirectory>${project.build.directory}/classes/META-INF/resources/openapi-ui</outputDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Demo App

A Quarkus petstore app that uses the Maven plugin to generate and serve the UI:

```bash
mvn package -pl demo -am
java -jar demo/target/quarkus-app/quarkus-run.jar
```

Then open http://localhost:8080/openapi-ui/index.html.

## Modules

| Module                    | Description                                                           |
|---------------------------|-----------------------------------------------------------------------|
| `core`                    | Generator library: parses specs and produces HTML + CSS + JS          |
| `cli`                     | Command-line tool: executable fat jar with shell header               |
| `maven-plugin`            | Maven plugin: integrates generation into build pipelines              |
| `quarkus-extension`       | Quarkus extension: dev-mode integration with auto-refresh             |
| `demo`                    | Quarkus petstore app: exercises the plugin end-to-end                 |

## Tech Stack

**Build-time** (generator + tests):

- Java 21+, Maven
- [swagger-parser](https://github.com/swagger-api/swagger-parser) for OpenAPI parsing
- [bulma-java](https://github.com/t1/bulma-java) for HTML generation
- [Playwright](https://playwright.dev/) for browser integration tests

**Runtime** (generated output, served as static files):

- [Bulma](https://bulma.io/) CSS framework
- [HTMX](https://htmx.org/) for dynamic fragment loading
- [highlight.js](https://highlightjs.org/) for response syntax highlighting
- [Font Awesome](https://fontawesome.com/) for icons

## Example Output (Petstore)

```
output/
├── index.html          # Layout + keyboard navigation
├── path-tree.html      # Path-based tree view
├── tag-tree.html       # Tag-based tree view
├── openapi-ui.css
├── vendor/             # Third-party libraries (from webjars)
│   ├── bulma.min.css
│   ├── htmx.min.js
│   ├── highlight.min.js
│   ├── css/
│   │   ├── fontawesome.min.css  # Font Awesome core
│   │   └── solid.min.css       # Font Awesome solid icons
│   └── webfonts/
│       └── fa-solid-900.woff2
├── owners/
│   ├── index.html       # Path fragment: tab bar + first method
│   ├── GET.html         # Fragment for GET /owners
│   └── {id}/
│       ├── index.html
│       ├── GET.html     # Fragment for GET /owners/{id}
│       └── pets/
│           ├── index.html
│           └── GET.html # Fragment for GET /owners/{ownerId}/pets
└── pets/
    ├── index.html       # Path fragment: tab bar + first method
    ├── GET.html         # Fragment for GET /pets
    ├── POST.html        # Fragment for POST /pets
    └── {id}/
        ├── index.html
        ├── GET.html     # Fragment for GET /pets/{id}
        ├── DELETE.html  # Fragment for DELETE /pets/{id}
        └── visits/
            ├── index.html
            ├── GET.html     # Fragment for GET /pets/{petId}/visits
            └── {visitId}/
                ├── index.html
                └── GET.html # Fragment for GET /pets/{petId}/visits/{visitId}
```

## Release Notes

We use [semantic versioning](https://semver.org), so 1.0.x versions are pure bugfix releases
(expept for 1.0.3, which was a mistake ;-).

### 1.0 - 2026-04-04

Initial MVP release

### 1.1 - 2026-04-10

- **Response links**: schema view shows link annotations with click-to-navigate;
  response bodies get named badges that fill parameters in the target operation;
  `x-links` extension for array items; keyboard-focusable
  ([#4](https://github.com/t1/openapi-ui-next/issues/4))
- **httpie defaults**: `GET` default method, `http://localhost` default base URL
  ([#5](https://github.com/t1/openapi-ui-next/issues/5))
- **Quarkus extension**: dev-mode integration: change JAX-RS code, browser auto-refreshes UI
  ([#26](https://github.com/t1/openapi-ui-next/issues/26))
- **Bug fixes**:
  - request body toggle without schema ([#6](https://github.com/t1/openapi-ui-next/issues/6))
  - textarea auto-grow ([#8](https://github.com/t1/openapi-ui-next/issues/8))
  - keyboard navigation ([#21](https://github.com/t1/openapi-ui-next/issues/21), [#23](https://github.com/t1/openapi-ui-next/issues/23))

### 1.2 - not yet released

- Style custom headers like documented parameters: label-above layout with badge and auto-resizing name input
- Security schemes infrastructure: resolve effective security requirements per operation ([#34](https://github.com/t1/openapi-ui-next/issues/34))
- apiKey security: header and query parameter input fields with pin support and fetch integration ([#35](https://github.com/t1/openapi-ui-next/issues/35))
- http bearer security: Authorization header input with pin support and auto-prefix Bearer in fetch ([#36](https://github.com/t1/openapi-ui-next/issues/36))
- Info-only security schemes: documentation rows for browser-handled auth (http basic, oauth2, openIdConnect, mutualTLS, cookie apiKey) ([#37](https://github.com/t1/openapi-ui-next/issues/37))
- Global server selector: collapsible panel with radio buttons for each server URL and description; persists selection in localStorage; foundation for multi-server support ([#39](https://github.com/t1/openapi-ui-next/issues/39))
- Custom server URLs: add arbitrary base URLs via "+ Add custom URL" button with editable text inputs, delete functionality, and localStorage persistence ([#41](https://github.com/t1/openapi-ui-next/issues/41))

## Contributing

Even just using the project and leaving a star helps a lot -- it shows there's interest
and keeps the momentum going. Bug reports and feature ideas are equally welcome.

### Prerequisites

- Java 21+
- Maven 3.9+

### Build

```bash
mvn verify
```

Browser tests run in Chromium by default. To test with WebKit (Safari) or Firefox:

```bash
mvn test -pl core -Dplaywright.browser=webkit
mvn test -pl core -Dplaywright.browser=firefox
```

## License

[Apache License 2.0](LICENSE)
