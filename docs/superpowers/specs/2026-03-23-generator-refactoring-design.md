# OpenApiUiGenerator Refactoring Design

Addresses all remaining clean code review findings (#7, #8, #9, #10, #12, #14, #19) from
`docs/clean-code-review-remaining.md`. The core problem: `OpenApiUiGenerator` is 1134 lines
and handles 8+ responsibilities.

## Approach: Extract by Output Type

Each generated artifact type gets its own class. The generator becomes a thin orchestrator.
All fragment generators return renderable elements — the orchestrator handles all file I/O.

## Package Restructuring

Rename and split the single package `com.github.t1.openapi.ui` into:

- **`com.github.t1.openapi.ui.generator`** — `OpenApiUiGenerator`, `MethodFragmentGenerator`,
  `PathFragmentGenerator`, `TagTreeGenerator`, `OperationContext`, `PathNode`
- **`com.github.t1.openapi.ui.components`** — `Tree`, `Toggle`, `SplitPane`

This changes import paths for consumers (demo app, CLI, maven-plugin).

## New Types

### `OperationContext` Record (#14)

Bundles the three parameters that always travel together:

```java
record OperationContext(HttpMethod method, Operation operation, String fullPath) {}
```

### `MethodFragmentGenerator` (#8, #12)

Extracts `buildMethodFragmentContent` and all helpers (~250 lines).

No constructor parameters needed — the spec is parsed with `resolveFully(true)`, so all `$ref`
references are already resolved before generation runs.

**Methods moving in:**
- `buildContent(OperationContext)` — main public method (was `buildMethodFragmentContent`)
- `generateJsonSkeleton()`, `sampleValue()`, `mediaTypeExample()`, `formatSampleValue()`,
  `formatBasedSample()` — JSON skeleton helpers
- `splitSegments()` — path segment splitting for header rendering

### `PathFragmentGenerator` (#12)

Extracts path index page generation (~40 lines).

**Methods moving in:**
- `buildContent(String segment, Map<HttpMethod, Operation> operations)` — tab-bar layout

### `TagTreeGenerator` (#12)

Extracts tag-based tree view generation (~65 lines).

**Methods moving in:**
- `buildTagTree(PathNode root, OpenAPI openAPI)` — builds tag-grouped tree
- `collectTaggedOperations()` — recursive tag operation gathering

### Shared Utilities

Two methods are used across multiple generators:

- **`methodColor(HttpMethod)`** — used by `MethodFragmentGenerator`, `PathFragmentGenerator`,
  and `TagTreeGenerator`. Becomes a package-private static utility method, either on
  `OperationContext` or a small `MethodColors` utility class.
- **`hxLoad()`** — used by `PathFragmentGenerator` and the main page layout in
  `OpenApiUiGenerator`. Stays in `OpenApiUiGenerator` as a package-private static method,
  callable by `PathFragmentGenerator` within the same package.

## Resource Files (#9, #10)

Move `APP_JS` (~385 lines) and `APP_CSS` (~185 lines) from inline string constants to classpath
resource files:

- `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`

Loaded via `getResourceAsStream()`. The generated HTML still inlines the content in `<style>`
and `<script>` tags — only the source changes.

Tree, Toggle, and SplitPane keep their existing inline constant pattern.

## `generate()` Decomposition (#7)

After the extractions above, decompose the remaining `generate()` into private methods:

- `parseSpec()` — reads the spec file, returns `OpenAPI`
- `buildPageLayout(...)` — assembles the main `index.html`
- `writeOutput(...)` — writes all files, copies webjar resources

**`generateFragments()`** and **`buildTree()`** stay in `OpenApiUiGenerator` as part of the
orchestrator. `generateFragments()` walks the `PathNode` tree recursively, delegates content
building to `MethodFragmentGenerator` and `PathFragmentGenerator`, and returns renderables.
`buildTree()` builds the path-based tree view (the tag tree is handled by `TagTreeGenerator`).

`generate()` becomes a short orchestrator calling these steps.

## Test Formatting Fix (#19)

Fix broken indentation at line 112 and missing blank lines in `OpenApiUiGeneratorTest`.
No structural changes to the test file — tests stay in the single existing test class.

## Expected Result

`OpenApiUiGenerator` drops from ~1134 lines to ~250-300 lines. Each extracted class has a single
responsibility and is independently understandable.

| Component | Responsibility | Approx. lines |
|-----------|---------------|---------------|
| `OpenApiUiGenerator` | Orchestrate: parse, delegate, write | ~250-300 |
| `MethodFragmentGenerator` | Build operation detail views | ~250 |
| `PathFragmentGenerator` | Build path tab-bar pages | ~40 |
| `TagTreeGenerator` | Build tag-grouped tree | ~65 |
| `OperationContext` | Bundle operation params | ~3 |
| `app.js` | Application JavaScript | ~385 |
| `app.css` | Application CSS | ~185 |
