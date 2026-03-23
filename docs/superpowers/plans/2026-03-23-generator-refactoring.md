# OpenApiUiGenerator Refactoring Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose the 1134-line `OpenApiUiGenerator` into focused classes organized into `generator` and `components` packages.

**Architecture:** Extract by output type — each generated artifact gets its own generator class. Move JS/CSS to classpath resource files. The orchestrator keeps spec parsing, tree building, and file I/O.

**Tech Stack:** Java 21, JUnit 5, AssertJ, bulma-java, swagger-parser, Maven

---

## Chunk 1: Package Restructuring and Resource Files

### Task 1: Move components to `components` package

Move `Tree`, `Toggle`, and `SplitPane` to `com.github.t1.openapi.ui.components`.

**Files:**
- Move: `core/src/main/java/com/github/t1/openapi/ui/Tree.java` → `core/src/main/java/com/github/t1/openapi/ui/components/Tree.java`
- Move: `core/src/main/java/com/github/t1/openapi/ui/Toggle.java` → `core/src/main/java/com/github/t1/openapi/ui/components/Toggle.java`
- Move: `core/src/main/java/com/github/t1/openapi/ui/SplitPane.java` → `core/src/main/java/com/github/t1/openapi/ui/components/SplitPane.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java` → `core/src/test/java/com/github/t1/openapi/ui/components/TreeTest.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/ToggleTest.java` → `core/src/test/java/com/github/t1/openapi/ui/components/ToggleTest.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/SplitPaneTest.java` → `core/src/test/java/com/github/t1/openapi/ui/components/SplitPaneTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (update imports)

- [ ] **Step 1: Create component directories and move files**

```bash
mkdir -p core/src/main/java/com/github/t1/openapi/ui/components
mkdir -p core/src/test/java/com/github/t1/openapi/ui/components
git mv core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/main/java/com/github/t1/openapi/ui/components/
git mv core/src/main/java/com/github/t1/openapi/ui/Toggle.java core/src/main/java/com/github/t1/openapi/ui/components/
git mv core/src/main/java/com/github/t1/openapi/ui/SplitPane.java core/src/main/java/com/github/t1/openapi/ui/components/
git mv core/src/test/java/com/github/t1/openapi/ui/TreeTest.java core/src/test/java/com/github/t1/openapi/ui/components/
git mv core/src/test/java/com/github/t1/openapi/ui/ToggleTest.java core/src/test/java/com/github/t1/openapi/ui/components/
git mv core/src/test/java/com/github/t1/openapi/ui/SplitPaneTest.java core/src/test/java/com/github/t1/openapi/ui/components/
```

- [ ] **Step 2: Update package declarations in moved files**

In each moved file, change `package com.github.t1.openapi.ui;` to `package com.github.t1.openapi.ui.components;`.

- [ ] **Step 3: Make component APIs public**

`Tree`, `Toggle`, `SplitPane` and their public factory methods need `public` visibility since they're now in a different package from `OpenApiUiGenerator`. Check each class:
- `Tree.tree()`, `Tree.js()`, `Tree.css()`, `TreeContainer` interface — must be `public`
- `Toggle.toggle()`, `Toggle.js()`, `Toggle.css()` — must be `public`
- `SplitPane.splitPane()`, `SplitPane.js()`, `SplitPane.css()` — must be `public`

- [ ] **Step 4: Update imports in OpenApiUiGenerator.java**

Change the three static imports from:
```java
import static com.github.t1.openapi.ui.SplitPane.splitPane;
import static com.github.t1.openapi.ui.Toggle.toggle;
import static com.github.t1.openapi.ui.Tree.tree;
```
to:
```java
import static com.github.t1.openapi.ui.components.SplitPane.splitPane;
import static com.github.t1.openapi.ui.components.Toggle.toggle;
import static com.github.t1.openapi.ui.components.Tree.tree;
```

Also add regular imports for `Toggle`, `Tree`, `SplitPane` where their class names are used directly (e.g., `Toggle.js()`, `Tree.js()`, `SplitPane.js()`, `Tree.css()`, etc.).

- [ ] **Step 5: Update imports in test files**

Update `AppFixture.java` and `BrowserTest.java` if they import component classes. Update `TestContext.java` if needed.

- [ ] **Step 6: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add -A core/src/main/java/com/github/t1/openapi/ui/components/ core/src/test/java/com/github/t1/openapi/ui/components/ core/src/main/java/com/github/t1/openapi/ui/ core/src/test/java/com/github/t1/openapi/ui/
git commit -m "move Tree, Toggle, SplitPane to components package"
```

### Task 2: Move `OpenApiUiGenerator` to `generator` package

**Files:**
- Move: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` → `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java` → `core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java` → `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/AppFixture.java` → `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`
- Move: `core/src/test/java/com/github/t1/openapi/ui/TestContext.java` → `core/src/test/java/com/github/t1/openapi/ui/generator/TestContext.java`
- Modify: `cli/src/main/java/com/github/t1/openapi/ui/cli/Main.java` (update import)
- Modify: `maven-plugin/src/main/java/com/github/t1/openapi/ui/maven/GenerateMojo.java` (update import)

- [ ] **Step 1: Create directory and move files**

```bash
mkdir -p core/src/main/java/com/github/t1/openapi/ui/generator
mkdir -p core/src/test/java/com/github/t1/openapi/ui/generator
git mv core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/main/java/com/github/t1/openapi/ui/generator/
git mv core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java core/src/test/java/com/github/t1/openapi/ui/generator/
git mv core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java core/src/test/java/com/github/t1/openapi/ui/generator/
git mv core/src/test/java/com/github/t1/openapi/ui/AppFixture.java core/src/test/java/com/github/t1/openapi/ui/generator/
git mv core/src/test/java/com/github/t1/openapi/ui/TestContext.java core/src/test/java/com/github/t1/openapi/ui/generator/
```

- [ ] **Step 2: Update package declarations in all moved files**

Change `package com.github.t1.openapi.ui;` to `package com.github.t1.openapi.ui.generator;` in all moved files.

- [ ] **Step 3: Update imports in OpenApiUiGenerator.java**

The component imports were already updated in Task 1. Verify no other in-package references break.

- [ ] **Step 4: Update consumer imports**

In `cli/src/main/java/com/github/t1/openapi/ui/cli/Main.java`, change:
```java
import com.github.t1.openapi.ui.OpenApiUiGenerator;
```
to:
```java
import com.github.t1.openapi.ui.generator.OpenApiUiGenerator;
```

In `maven-plugin/src/main/java/com/github/t1/openapi/ui/maven/GenerateMojo.java`, same change.

- [ ] **Step 5: Run full build**

Run: `mvn test` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All modules compile and tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/ core/src/test/java/com/github/t1/openapi/ui/generator/ cli/src/main/java/com/github/t1/openapi/ui/cli/Main.java maven-plugin/src/main/java/com/github/t1/openapi/ui/maven/GenerateMojo.java
git commit -m "move OpenApiUiGenerator to generator package"
```

### Task 3: Move APP_CSS to resource file

**Files:**
- Create: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Create app.css resource file**

Copy the content of the `APP_CSS` string constant (lines 547-731, the content inside the `"""` delimiters) to `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`.

- [ ] **Step 2: Add resource loading method and replace constant**

In `OpenApiUiGenerator.java`, remove the `APP_CSS` constant and add a method:

```java
private static String loadResource(String name) {
    try (var stream = OpenApiUiGenerator.class.getResourceAsStream(name)) {
        if (stream == null) throw new IllegalStateException("resource not found: " + name);
        return new String(stream.readAllBytes());
    } catch (IOException e) {
        throw new RuntimeException("could not load resource: " + name, e);
    }
}
```

Replace the reference to `APP_CSS` in `generate()` (line 145) with `loadResource("app.css")`.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.css core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git commit -m "move APP_CSS to resource file"
```

### Task 4: Move APP_JS to resource file

**Files:**
- Create: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Create app.js resource file**

Copy the content of the `APP_JS` string constant (lines 734-1118, the content inside the `"""` delimiters) to `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`.

- [ ] **Step 2: Replace constant with resource loading**

Remove the `APP_JS` constant. Replace the reference in `generate()` (line 140) with `loadResource("app.js")`.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git commit -m "move APP_JS to resource file"
```

## Chunk 2: Class Extractions

### Task 5: Introduce `OperationContext` record

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationContext.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Create the record**

```java
package com.github.t1.openapi.ui.generator;

import io.swagger.v3.oas.models.PathItem.HttpMethod;

record OperationContext(HttpMethod method, io.swagger.v3.oas.models.Operation operation, String fullPath) {}
```

- [ ] **Step 2: Update `buildMethodFragmentContent` signature**

Change from:
```java
private static Element buildMethodFragmentContent(HttpMethod method, io.swagger.v3.oas.models.Operation operation, String fullPath) {
```
to:
```java
private static Element buildMethodFragmentContent(OperationContext ctx) {
```

Replace all references to `method`, `operation`, and `fullPath` inside the method body with `ctx.method()`, `ctx.operation()`, `ctx.fullPath()`.

- [ ] **Step 3: Update callers**

In `generateFragments()` (line 289), change:
```java
var fragment = buildMethodFragmentContent(opEntry.getKey(), opEntry.getValue(), fullPath);
```
to:
```java
var fragment = buildMethodFragmentContent(new OperationContext(opEntry.getKey(), opEntry.getValue(), fullPath));
```

In `generatePathFragment()` (line 316), change:
```java
firstMethodContent = buildMethodFragmentContent(method, opEntry.getValue(), fullPath);
```
to:
```java
firstMethodContent = buildMethodFragmentContent(new OperationContext(method, opEntry.getValue(), fullPath));
```

- [ ] **Step 4: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/
git commit -m "introduce OperationContext record"
```

### Task 6: Make shared utilities package-private

`methodColor` is used by `buildMethodFragmentContent`, `addNodes` (path tree), `buildTagTree`, and `generatePathFragment`. It needs to be accessible from all future generator classes within the package. `hxLoad` is only used within `OpenApiUiGenerator` (for the view toggle), so it stays `private` — no cross-class access needed.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Change `methodColor` visibility to package-private**

Change `methodColor` from `private static` to package-private `static`:
```java
static Color methodColor(PathItem.HttpMethod method) {
```

This keeps it in `OpenApiUiGenerator` for now but makes it accessible to other classes in the `generator` package.

- [ ] **Step 2: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git commit -m "make methodColor package-private for cross-class access"
```

### Task 7: Extract `MethodFragmentGenerator`

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Create `MethodFragmentGenerator` class**

Create the class with all methods that belong to method fragment content generation. Move these methods from `OpenApiUiGenerator`:
- `buildMethodFragmentContent(OperationContext)` → renamed to `buildContent(OperationContext)`
- `generateJsonSkeleton(Schema<?>)`
- `sampleValue(Schema<?>)`
- `mediaTypeExample(MediaType)`
- `formatSampleValue(String, Object)`
- `formatBasedSample(String)`
The class has no constructor parameters. All methods are static (or the class is instantiated as a stateless utility — follow existing patterns).

Note: `splitSegments()` stays in `OpenApiUiGenerator` — it is used by `generate()` for PathNode tree building and the defaultToTags heuristic, not by `buildMethodFragmentContent`.

Reference `OpenApiUiGenerator.methodColor()` via static import or qualified name since it stays in the orchestrator.

```java
package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
// ... other imports

import static com.github.t1.openapi.ui.generator.OpenApiUiGenerator.methodColor;

class MethodFragmentGenerator {
    static Element buildContent(OperationContext ctx) {
        // ... body of buildMethodFragmentContent, using ctx.method(), ctx.operation(), ctx.fullPath()
    }
    // ... other extracted methods
}
```

- [ ] **Step 2: Update `OpenApiUiGenerator` to delegate**

In `generateFragments()`, replace:
```java
var fragment = buildMethodFragmentContent(new OperationContext(...));
```
with:
```java
var fragment = MethodFragmentGenerator.buildContent(new OperationContext(...));
```

In `generatePathFragment()`, same replacement.

Remove the moved methods from `OpenApiUiGenerator`.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/
git commit -m "extract MethodFragmentGenerator"
```

### Task 8: Extract `PathFragmentGenerator`

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/generator/PathFragmentGenerator.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Create `PathFragmentGenerator` class**

Move `generatePathFragment` content (the tab-bar building logic) into a new class. The method currently writes files directly — refactor so it returns the renderable element instead, and the caller in `generateFragments()` handles file writing.

```java
package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Element;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.htmljava.HtmlBasics.element;

class PathFragmentGenerator {
    static Element buildContent(String fullPath, java.util.Map<HttpMethod, io.swagger.v3.oas.models.Operation> operations) {
        var tabList = element("ul");
        var first = true;
        Element firstMethodContent = null;
        for (var opEntry : operations.entrySet()) {
            var method = opEntry.getKey();
            var li = element("li");
            if (first) li.classes("is-active");
            li.content(element("a").content(method.name())
                    .attr("tabindex", "0")
                    .attr("hx-get", fullPath + "/" + method.name() + ".html")
                    .attr("hx-target", "#method-content")
                    .attr("hx-swap", "innerHTML"));
            tabList.content(li);
            if (first) {
                firstMethodContent = MethodFragmentGenerator.buildContent(
                        new OperationContext(method, opEntry.getValue(), fullPath));
                first = false;
            }
        }
        return div().content(
                div().classes("tabs").content(tabList),
                div().id("method-content").content(firstMethodContent));
    }
}
```

- [ ] **Step 2: Update `generateFragments()` in `OpenApiUiGenerator`**

Replace the call to `generatePathFragment(child, fullPath)` with:
```java
var pathFragment = PathFragmentGenerator.buildContent(fullPath, child.operations);
var fragmentDir = outputDir.resolve(fullPath);
Files.createDirectories(fragmentDir);
Files.writeString(fragmentDir.resolve("index.html"), pathFragment.render());
```

Remove `generatePathFragment()` method from `OpenApiUiGenerator`.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/
git commit -m "extract PathFragmentGenerator"
```

### Task 9: Extract `TagTreeGenerator`

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/generator/TagTreeGenerator.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Make `PathNode` package-private**

Change `PathNode` from `private static class` to package-private:
```java
static class PathNode {
```

Also make its fields accessible (they're already package-private since they have no modifier).

- [ ] **Step 2: Create `TagTreeGenerator` class**

Move `buildTagTree()`, `collectTaggedOperations()`, and the `TaggedOperation` record:

```java
package com.github.t1.openapi.ui.generator;

import com.github.t1.htmljava.Renderable;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.github.t1.bulmajava.elements.Tag.tag;
import static com.github.t1.htmljava.HtmlBasics.element;
import static com.github.t1.htmljava.HtmlBasics.span;
import static com.github.t1.openapi.ui.components.Tree.tree;
import static com.github.t1.openapi.ui.generator.OpenApiUiGenerator.methodColor;

class TagTreeGenerator {
    // Move TaggedOperation record, buildTagTree, and collectTaggedOperations here
    // Adjust method signatures: buildTagTree takes (OpenAPI openApi, PathNode root)
}
```

- [ ] **Step 3: Update `OpenApiUiGenerator` to delegate**

Replace:
```java
var tagTree = buildTagTree(openApi, root);
```
with:
```java
var tagTree = TagTreeGenerator.buildTagTree(openApi, root);
```

Remove `buildTagTree()`, `collectTaggedOperations()`, and `TaggedOperation` from `OpenApiUiGenerator`.

- [ ] **Step 4: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/
git commit -m "extract TagTreeGenerator"
```

## Chunk 3: Method Decomposition and Cleanup

### Task 10: Decompose `generate()` into named steps

After Tasks 3-9, `generate()` is already shorter (no inline JS/CSS, fragment building delegated). Decompose the remaining body into named private methods.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`

- [ ] **Step 1: Extract `parseSpec()`**

Extract lines 68-71 (logging + parsing) into:
```java
private OpenAPI parseSpec() {
    log.info("Parsing {}", specFile);
    var parseOptions = new ParseOptions();
    parseOptions.setResolveFully(true);
    return new OpenAPIV3Parser().read(specFile.toString(), null, parseOptions);
}
```

- [ ] **Step 2: Extract `buildPageLayout()`**

Extract the page assembly logic (from `var pageTitle = ...` through `var page = html(...)`) into:
```java
private Renderable buildPageLayout(OpenAPI openApi, Renderable pathTree, Renderable tagTree, boolean defaultToTags) {
    // ... page assembly
}
```

- [ ] **Step 3: Extract `writeOutput()`**

Extract the file-writing logic into:
```java
private void writeOutput(Renderable page, PathNode root, Renderable tagTree, Renderable pathTree) throws IOException {
    Files.createDirectories(outputDir);
    Files.writeString(outputDir.resolve("index.html"), page.render());
    Files.writeString(outputDir.resolve("openapi-ui.css"), Toggle.css() + Tree.css() + SplitPane.css() + loadResource("app.css"));
    generateFragments(root, "");
    Files.writeString(outputDir.resolve("tag-tree.html"), tagTree.render());
    Files.writeString(outputDir.resolve("path-tree.html"), pathTree.render());
    copyWebJarResource("bulma", "css/bulma.min.css", "bulma.min.css");
    copyWebJarResource("htmx.org", "dist/htmx.min.js", "htmx.min.js");
    log.info("Done. Output written to {}", outputDir);
}
```

- [ ] **Step 4: Verify `generate()` is now a short orchestrator**

`generate()` should now read roughly:
```java
public void generate() throws IOException {
    var openApi = parseSpec();
    // build PathNode tree (inline, ~6 lines)
    // log path/operation counts
    var pathTree = buildTree(root);
    var tagTree = TagTreeGenerator.buildTagTree(openApi, root);
    // compute defaultToTags (inline, ~5 lines)
    var page = buildPageLayout(openApi, pathTree, tagTree, defaultToTags);
    writeOutput(page, root, tagTree, pathTree);
}
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl core` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git commit -m "decompose generate() into named steps"
```

### Task 11: Fix test formatting (#19)

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java`

- [ ] **Step 1: Fix indentation at line 112**

The `shouldUseParamClassForPathParameters` test at line 112 has broken indentation (no leading spaces). Fix to match surrounding tests (4-space indent).

- [ ] **Step 2: Add missing blank lines between test methods**

Scan for test methods that lack a blank line before `@Test`. Add blank lines to maintain consistent spacing.

- [ ] **Step 3: Run tests**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java
git commit -m "fix test formatting"
```

### Task 12: Squash commits and final verification

- [ ] **Step 1: Run full build across all modules**

Run: `mvn test` with `dangerouslyDisableSandbox: true` and `timeout: 60000`
Expected: All modules compile and all tests pass.

- [ ] **Step 2: Review screenshots**

Check `core/target/screenshots/` to visually confirm no UI regressions.

- [ ] **Step 3: Squash into single commit**

Squash all task commits into one:
```bash
git rebase -i HEAD~N
```
where N is the number of task commits. Use the commit message:
```
refactor OpenApiUiGenerator: extract classes, packages, and resource files
```

- [ ] **Step 4: Verify clean state**

```bash
git status
git log --oneline -5
```
