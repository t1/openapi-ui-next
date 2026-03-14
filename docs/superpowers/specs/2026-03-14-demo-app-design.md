# Demo Quarkus App Design

## Purpose

A minimal Quarkus petstore app that serves as a living demo for the OpenAPI UI generator.
It starts small and grows alongside core features.

## Module Structure

New `demo` module added to the parent pom:

```
demo/
├── pom.xml
└── src/main/java/com/github/t1/openapi/ui/demo/
    ├── Pet.java              # Record: id, name, status
    └── PetResource.java      # JAX-RS: GET /pets, GET /pets/{id}
```

## Tech Stack

- Quarkus (latest 3.x) with `quarkus-rest` and `quarkus-smallrye-openapi`
- Java 21 (matching core and maven-plugin modules)
- In-memory storage (seed data baked in)
- No database, no persistence layer

## Build Pipeline

1. **`process-classes`**: `smallrye-open-api-maven-plugin` scans compiled JAX-RS classes
   and generates `target/generated/openapi.yaml`
2. **`prepare-package`**: `openapi-ui-maven-plugin` reads the spec, writes HTML to
   `target/classes/META-INF/resources/openapi-ui/`
3. **`package`**: `quarkus-maven-plugin` packages the jar including the generated UI

Using the standalone `smallrye-open-api-maven-plugin` (not Quarkus's built-in schema generation)
because Quarkus generates the schema during `quarkus:build` at `package` phase — too late
for our plugin to consume at `prepare-package`.

The `quarkus-smallrye-openapi` extension is still included for the runtime `/q/openapi` endpoint.

The UI is served at `/openapi-ui/index.html` alongside the live API.

### Plugin Configuration

```xml
<!-- 1. Generate OpenAPI spec from JAX-RS classes -->
<plugin>
    <groupId>io.smallrye</groupId>
    <artifactId>smallrye-open-api-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>generate-schema</goal></goals>
            <configuration>
                <outputDirectory>target/generated</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- 2. Generate UI from the spec -->
<plugin>
    <groupId>com.github.t1</groupId>
    <artifactId>openapi-ui-maven-plugin</artifactId>
    <version>${project.version}</version>
    <executions>
        <execution>
            <phase>prepare-package</phase>
            <goals><goal>generate</goal></goals>
            <configuration>
                <specFile>target/generated/openapi.yaml</specFile>
                <outputDirectory>target/classes/META-INF/resources/openapi-ui</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Application Code

**`Pet.java`** — simple record:
```java
public record Pet(long id, String name, String status) {}
```

**`PetResource.java`** — JAX-RS resource with seed data:
- `GET /pets` — list all pets
- `GET /pets/{id}` — get pet by id (404 if not found)
- In-memory `List<Pet>` with two seed entries

## Growth Strategy

Start with GET-only endpoints. Add POST, PUT, DELETE as core features
(request bodies, status codes, etc.) are implemented.
