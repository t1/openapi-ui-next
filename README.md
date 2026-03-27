# OpenAPI UI Next

Generates static, keyboard-navigable HTML UIs from OpenAPI specifications.

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

- Parses OpenAPI 3.0/3.1 specs into interactive static HTML
- Hierarchical, keyboard-navigable path tree with method tag addons
- Method tabs in detail pane: switching between operations on the same path
- Enriched method fragments: description, deprecated badge, tags, external docs
- Three interaction modes: Try (fetch), curl, httpie
- Three-level keyboard navigation: tree → method tabs → content fields
- Boundary bump animation at navigation limits
- Responsive layout (desktop: side-by-side; mobile: stacked)
- Parameter inputs (path and query)
- Response rendering with content-type awareness (JSON prettification, HTML/XML/YAML)

## Modules

| Module         | Description                                                  |
|----------------|--------------------------------------------------------------|
| `core`         | Generator library: parses specs and produces HTML + CSS + JS |
| `cli`          | Command-line tool: executable fat jar with shell header      |
| `maven-plugin` | Maven plugin: integrates generation into build pipelines     |
| `demo`         | Quarkus petstore app: exercises the plugin end-to-end        |

## Build

```bash
mvn verify
```

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

## Tech Stack

- Java 21+, Maven
- [swagger-parser](https://github.com/swagger-api/swagger-parser) for OpenAPI parsing
- [bulma-java](https://github.com/t1/bulma-java) for HTML generation
- [Bulma](https://bulma.io/) CSS framework (via webjars)
- [HTMX](https://htmx.org/) for dynamic fragment loading (via webjars)
- [Playwright](https://playwright.dev/) for browser integration tests

## Generated Output

```
output/
├── index.html          # Path tree + layout + keyboard navigation
├── openapi-ui.css
├── bulma.min.css
├── htmx.min.js
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
