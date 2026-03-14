# OpenAPI UI

Generates static, keyboard-navigable HTML UIs from OpenAPI specifications.

## Features

- Parses OpenAPI 3.0/3.1 specs into interactive static HTML
- Hierarchical, keyboard-navigable path tree
- Three interaction modes: Try (fetch), curl, httpie
- Responsive layout (desktop: side-by-side; mobile: stacked)
- Parameter inputs (path and query)
- Response rendering with content-type awareness (JSON prettification, HTML/XML/YAML)

## Modules

| Module | Description |
|--------|-------------|
| `core` | Generator library — parses specs and produces HTML + CSS + JS |
| `cli` | Command-line tool — executable fat jar with shell header |
| `maven-plugin` | Maven plugin — integrates generation into build pipelines |
| `demo` | Quarkus petstore app — exercises the plugin end-to-end |

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
└── pets/
    ├── GET.html         # Fragment for GET /pets
    └── {id}/
        └── GET.html     # Fragment for GET /pets/{id}
```
