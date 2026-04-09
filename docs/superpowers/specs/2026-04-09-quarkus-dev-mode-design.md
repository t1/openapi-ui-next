# Quarkus Dev-Mode Integration — Design Spec

Issue: #9

## Goal

When a developer changes JAX-RS code in Quarkus dev-mode, the OpenAPI UI in the browser
auto-refreshes to reflect the updated API — no manual steps required.

## Approach

Build a Quarkus extension that hooks into the augmentation pipeline. The extension consumes
the SmallRye OpenAPI model (via `OpenApiDocumentBuildItem`) and runs the existing
`OpenApiUiGenerator` to produce static HTML/CSS/JS. Quarkus's built-in dev-mode live-reload
handles browser refresh automatically.

Before building the extension, migrate the core generator from the Swagger model
(`io.swagger.v3.oas.models.*`) to the MicroProfile OpenAPI model
(`org.eclipse.microprofile.openapi.models.*`), so the extension can pass the model directly
without a serialize-then-parse round-trip.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Integration type | Quarkus extension (not standalone watcher) | Tightest integration, automatic dev-mode support |
| Spec source | SmallRye OpenAPI model via `OpenApiDocumentBuildItem` | No HTTP round-trip, no file I/O; direct model access |
| Browser refresh | Quarkus built-in live-reload | Zero extra code; augmentation-produced resources trigger refresh |
| Extension vs Maven plugin | Extension replaces plugin for Quarkus users | Single dependency instead of Maven plugin config |
| Model library | MicroProfile OpenAPI (SmallRye impl) replaces Swagger | Enables direct model pass-through; cleaner API (interfaces) |
| Quarkiverse | Build in this repo first, move to Quarkiverse later | Iterate fast, then apply for Quarkiverse when stable |

## Sub-Issues

### 1. Migrate core from Swagger model to MicroProfile OpenAPI model

Replace `io.swagger.v3:swagger-parser` with `io.smallrye:smallrye-open-api-core` as the
parser and model library throughout the core module.

**Parser change:** Replace `OpenAPIV3Parser.read(path, null, parseOptions)` with SmallRye's
`OpenApiParser.parse(url)` (or the `SmallRyeOpenAPI.Builder` API).

**Model type changes** (7 of 11 source files in the generator package):

| Swagger | MicroProfile |
|---------|-------------|
| `io.swagger.v3.oas.models.OpenAPI` | `org.eclipse.microprofile.openapi.models.OpenAPI` |
| `PathItem` / `PathItem.HttpMethod` | `PathItem` / `PathItem.HttpMethod` |
| `Operation` | `Operation` |
| `Schema` | `Schema` |
| `MediaType` | `MediaType` |
| `ApiResponse` / `ApiResponses` | `APIResponse` / `APIResponses` |
| `Parameter` | `Parameter` |
| `Link` | `Link` |

API methods are nearly identical. Key differences:
- Swagger `Paths` extends `LinkedHashMap`; MicroProfile `Paths` has `getPathItems()`.
- Swagger `new Link()`; MicroProfile `OASFactory.createLink()`.

**`$ref` resolution:** SmallRye's parser preserves `$ref` as `Reference` objects (no
`resolveFully` equivalent). The generator assumes fully-resolved schemas — no code checks
`getRef()` anywhere. A new `ReferenceResolver` utility must walk the parsed model in-place,
find every `Referenceable` where `getRef() != null`, look up the target in the appropriate
`components/*` map, and replace the reference with the resolved object. Must handle circular
references (the generator already has cycle detection via `IdentityHashMap`).

**Tests:** Test resource YAML files stay unchanged. Test code constructing model objects
switches to MicroProfile equivalents (`OASFactory.createSchema()`, etc.).

**Files affected:** `OpenApiUiGenerator.java`, `OperationFragmentGenerator.java`,
`PathFragmentGenerator.java`, `TagTreeGenerator.java`, `PathNode.java`, `Operation.java`,
`ApiPath.java`, plus `pom.xml` dependency changes and a new `ReferenceResolver.java`.

### 2. Build the Quarkus extension

New `quarkus-extension/` module with deployment and runtime sub-modules.

**Module structure:**
```
quarkus-extension/
├── pom.xml                  (parent, packaging: pom)
├── deployment/
│   ├── pom.xml
│   └── src/main/java/.../deployment/
│       └── OpenApiUiProcessor.java
└── runtime/
    ├── pom.xml
    └── src/main/resources/META-INF/
        └── quarkus-extension.yaml
```

**Deployment dependencies:**
- `io.quarkus:quarkus-core-deployment`
- `io.quarkus:quarkus-smallrye-openapi-spi` (for `OpenApiDocumentBuildItem`)
- `com.github.t1:openapi-ui-core`

**Runtime dependencies:**
- `io.quarkus:quarkus-core`
- Runs `quarkus-extension-maven-plugin:extension-descriptor`

**BuildStep** (`OpenApiUiProcessor.java`):
- Consumes `List<OpenApiDocumentBuildItem>` (filters to default document).
- Extracts `OpenAPI` model from `doc.getSmallRyeOpenAPI().model()`.
- Calls `OpenApiUiGenerator` with the model directly (new constructor accepting
  `OpenAPI` instead of file path).
- Generator produces files as `Map<String, byte[]>` (new `generateToMemory()` method).
- Produces `GeneratedResourceBuildItem` for each file at
  `META-INF/resources/openapi-ui/<path>`.
- Produces `NativeImageResourceBuildItem` for GraalVM native image support.

**Generator API addition** (in core):
- New constructor: `OpenApiUiGenerator(OpenAPI model, ...)` — skips parsing.
- New method: `Map<String, byte[]> generateToMemory()` — returns file contents instead of
  writing to disk. The existing `generate()` method (file-based) stays for CLI/Maven plugin.

**Dev-mode flow:**
1. Developer changes JAX-RS class → Quarkus detects change
2. Re-augmentation runs → SmallRye re-scans → fresh `OpenApiDocumentBuildItem`
3. Our BuildStep re-runs → regenerates UI as `GeneratedResourceBuildItem`
4. Quarkus detects resource changes → triggers browser live-reload
5. Browser refreshes automatically

**No runtime code needed.** The runtime module contains only the extension descriptor.

### 3. Update demo app

**Changes to `demo/pom.xml`:**
- **Add** `com.github.t1:openapi-ui-quarkus` runtime dependency.
- **Remove** `openapi-ui-maven-plugin` execution.
- **Remove** `smallrye-open-api-maven-plugin` execution (the `quarkus-smallrye-openapi`
  runtime dependency handles spec generation at augmentation time).

**What stays:** `quarkus-smallrye-openapi` runtime dependency, JAX-RS resources,
`application.properties`, `index.html` landing page.

**Verification:**
- `mvn quarkus:dev -pl demo` — change a resource, UI auto-refreshes.
- `mvn package -pl demo -am` — production build includes UI in fat jar.
- `http://localhost:8080/openapi-ui/index.html` works as before.

### 4. Move extension to Quarkiverse

Once the extension is stable, apply for Quarkiverse onboarding:
- Create an Extension Proposal issue in `quarkusio/quarkus`.
- Create a new repo `github.com/quarkiverse/quarkus-openapi-ui` containing only the
  deployment + runtime modules.
- Change coordinates to `io.quarkiverse.openapiui:quarkus-openapi-ui`.
- Parent POM becomes `io.quarkiverse:quarkiverse-parent`.
- Adopt Quarkiverse release workflow (`.github/project.yml` version-bump PRs).
- Add Antora docs in `docs/modules/ROOT/pages/`.
- Register in extension catalog and ecosystem CI.
- The core library stays at `com.github.t1:openapi-ui-core` on Maven Central;
  the Quarkiverse extension depends on it.

## Out of Scope

- Custom SSE/WebSocket for partial HTMX updates (Quarkus live-reload is sufficient).
- Standalone file-watcher mode for non-Quarkus frameworks.
- Incremental generation (full regeneration is fast enough).
