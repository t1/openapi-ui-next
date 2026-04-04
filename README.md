# OpenAPI UI Next

[![Java CI](https://github.com/t1/openapi-ui-next/actions/workflows/maven.yml/badge.svg)](https://github.com/t1/openapi-ui-next/actions/workflows/maven.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Generates static, keyboard-navigable HTML UIs from OpenAPI specifications; via CLI or Maven plugin.

> This is also my playground for learning how to vibe-code at the Harness Engineering level.

![OpenAPI UI Next — light](docs/screenshots/hero.png#gh-light-mode-only)
![OpenAPI UI Next — dark](docs/screenshots/hero-dark.png#gh-dark-mode-only)

## Why?

When Swagger came out, I loved it! Esp. the "Try it out" was a real game changer. But APIs keep getting bigger and
more complex, yet Swagger didn't grow alongside UX-wise. I absolutely want to be able to quickly navigate APIs with the
keyboard and have not only a nice-looking, but a clear and usage oriented view on the API.

In contrast, Swagger tends to show everything at once: `curl` commands you didn't ask for, response schemas that look
just like actual responses, and deeply nested sections that bury the information you need. And schema objects cluttered
at the end. _OpenAPI UI Next_ keeps the UI clean by showing details on demand: schema documentation, response types,
and code snippets are there when you want them, hidden when you don't.

The OpenAPI UI tools I know (Swagger UI, Redoc (even the generated variant), Rapidoc) are slow by design:
they are JavaScript-heavy SPAs that parse the spec at runtime in the browser. This means slow initial loads on large
specs. _OpenAPI UI Next_ takes a different approach: it generates plain HTML + CSS at build time. The result is a set of
static files that can be served from any web server or embedded in any backend, so they load instantly. The dynamic UX
is provided mainly by HTMX, e.g. loading method fragments on click.

## Features

**Navigation & Layout**
- Path-based or tag-based tree views with method badges
- Resizable split pane with persistent width
- Method tabs for switching operations on the same path
- Keyboard navigation: cursor keys for spacial navigation in addition to Tab/Shift+Tab
- Pin values, so you don't have to repeat them everywhere, quickly with Ctrl+P (Alt on Linux and Windows)
- URL hash navigation: bookmarkable deep links (`#pets/GET`, `#[billing]invoices/GET`), browser back/forward
- Responsive layout (desktop: side-by-side; mobile: stacked)
- Automatic dark mode support (uses system setting)

<details>
<summary>Tag-based tree view</summary>

![Tag view — light](docs/screenshots/tag-view.png#gh-light-mode-only)
![Tag view — dark](docs/screenshots/tag-view-dark.png#gh-dark-mode-only)
</details>

**API Documentation**
- Operation summary, description, deprecated badge, tags, external docs
- Parameter inputs with type (path/query/header) and required badges, with optional localStorage persistence
- Request body editor with JSON skeleton and schema documentation
- Response schema with status code tabs, property types, required markers, and examples
- Collapsible nested object and array properties in schema views
- Accept header select for multi-content-type endpoints
- Boolean parameters rendered as checkboxes (two-state: omit or send `true`; explicitly
  sending `false` is not supported -- a three-state control would add UX complexity for a
  rare need, since most boolean query params are opt-in flags with a `false` default)

<details>
<summary>Parameter inputs</summary>

![Parameters filled — light](docs/screenshots/params-filled.png#gh-light-mode-only)
![Parameters filled — dark](docs/screenshots/params-filled-dark.png#gh-dark-mode-only)
</details>

<details>
<summary>Response schema tabs</summary>

![Response schema tabs — light](docs/screenshots/response-schema-tabs.png#gh-light-mode-only)
![Response schema tabs — dark](docs/screenshots/response-schema-tabs-dark.png#gh-dark-mode-only)
</details>

<details>
<summary>Nested schema expanded</summary>

![Nested schema expanded — light](docs/screenshots/nested-schema-expanded.png#gh-light-mode-only)
![Nested schema expanded — dark](docs/screenshots/nested-schema-expanded-dark.png#gh-dark-mode-only)
</details>

**Request Headers**
- Global headers panel: set headers that apply to all requests, with optional localStorage persistence
- Per-operation custom headers: add arbitrary headers per endpoint, with optional persistence
- Header merge priority: per-operation custom > spec-defined > global

**Try It Out**
- Three modes: Try (fetch), httpie, curl; switch at any time with Ctrl+1/2/3 (Alt on Linux and Windows)
- Response rendering with syntax highlighting (JSON, HTML, XML, YAML)
- Collapsible response headers display with spec documentation: documented headers show descriptions,
  deprecated indicators, and required-but-missing warnings; undocumented headers are hidden behind
  a "Show all" button when documented headers exist; auto-expands on first send when documented headers are present

<details>
<summary>Try mode with JSON response</summary>

![Try mode — light](docs/screenshots/try-mode-json-response.png#gh-light-mode-only)
![Try mode — dark](docs/screenshots/try-mode-json-response-dark.png#gh-dark-mode-only)
</details>

## Usage

### CLI

```bash
mvn -pl cli package
./cli/target/openapi-ui <spec-file> <output-dir>
```

### Maven Plugin

```xml
<plugin>
    <groupId>com.github.t1</groupId>
    <artifactId>openapi-ui-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <configuration>
        <specFile>src/main/resources/openapi.yaml</specFile>
    </configuration>
    <executions>
        <execution>
            <phase>generate-resources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
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

| Module         | Description                                                  |
|----------------|--------------------------------------------------------------|
| `core`         | Generator library: parses specs and produces HTML + CSS + JS |
| `cli`          | Command-line tool: executable fat jar with shell header      |
| `maven-plugin` | Maven plugin: integrates generation into build pipelines     |
| `demo`         | Quarkus petstore app: exercises the plugin end-to-end        |

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
│   │   └── all.min.css       # Font Awesome
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
