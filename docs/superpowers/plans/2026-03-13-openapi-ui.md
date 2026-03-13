# OpenAPI UI Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a tool that generates static, keyboard-navigable HTML from OpenAPI specs for exploring and trying out APIs.

**Architecture:** Maven multi-module (core, maven-plugin, cli). Core parses OpenAPI specs with swagger-parser-v3 and generates static HTML files using bulma-java. HTMX handles fragment loading. Playwright tests verify browser behavior.

**Tech Stack:** Java, Maven, swagger-parser-v3, bulma-java, HTMX, Bulma CSS, Playwright, JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-13-openapi-ui-design.md`

**Skills:** @tdder:tdd, @tdder:java, @tdder:maven, @tdder:unfolding-architecture

**Note on bulma-java API:** This plan uses the bulma-java 1.0a17 API based on research. If any call does not compile, consult the bulma-java source/javadoc and adjust — the intent of each step is clear even if the exact method name differs.

**Note on base URL override:** The generated HTML stores the API base URL in a `<meta name="api-base-url" content="...">` tag. Tests can override this by manipulating the DOM before interacting. The JS reads this meta tag to construct request URLs.

---

## Chunk 1: Project Scaffolding + Minimal End-to-End

### Task 1: Create Maven Multi-Module Structure

**Files:**
- Create: `pom.xml` (parent)
- Create: `core/pom.xml`
- Create: `maven-plugin/pom.xml`
- Create: `cli/pom.xml`

- [ ] **Step 1: Create parent POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.github.t1</groupId>
    <artifactId>openapi-ui</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>core</module>
        <module>maven-plugin</module>
        <module>cli</module>
    </modules>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.junit</groupId>
                <artifactId>junit-bom</artifactId>
                <version>5.11.4</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: Create core POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.t1</groupId>
        <artifactId>openapi-ui</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>openapi-ui-core</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.t1</groupId>
            <artifactId>bulma-java</artifactId>
            <version>1.0a17</version>
        </dependency>
        <dependency>
            <groupId>io.swagger.parser.v3</groupId>
            <artifactId>swagger-parser-v3</artifactId>
            <version>2.1.39</version>
        </dependency>
        <dependency>
            <groupId>com.microsoft.playwright</groupId>
            <artifactId>playwright</artifactId>
            <version>1.51.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Create placeholder maven-plugin POM**

Use `jar` packaging for now. The `maven-plugin` packaging and plugin dependencies are added in Task 12 when the Mojo is implemented.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.t1</groupId>
        <artifactId>openapi-ui</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>openapi-ui-maven-plugin</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.t1</groupId>
            <artifactId>openapi-ui-core</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: Create placeholder cli POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.github.t1</groupId>
        <artifactId>openapi-ui</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>openapi-ui-cli</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.github.t1</groupId>
            <artifactId>openapi-ui-core</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 5: Verify build compiles**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add pom.xml core/pom.xml maven-plugin/pom.xml cli/pom.xml
git commit -m "scaffold multi-module maven project"
```

### Task 2: First End-to-End — Generate Index Page from Minimal Spec

The simplest possible slice: parse a spec with one GET endpoint, generate an `index.html` that lists it. Use @tdder:tdd for the red-green-refactor cycle.

**Files:**
- Create: `core/src/test/resources/one-get.yaml` (test fixture)
- Create: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`
- Create: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Create test fixture — minimal OpenAPI spec**

Create `core/src/test/resources/one-get.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Test API
  version: 1.0.0
servers:
  - url: https://api.example.com
paths:
  /pets:
    get:
      summary: List pets
      operationId: listPets
      responses:
        '200':
          description: A list of pets
```

- [ ] **Step 2: Write failing test — generator produces index.html containing the path**

```java
package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiUiGeneratorTest {
    @TempDir Path outputDir;

    @Test
    void shouldGenerateIndexWithOnePath() throws Exception {
        var specPath = Path.of(getClass().getResource("/one-get.yaml").toURI());

        new OpenApiUiGenerator(specPath, outputDir).generate();

        var indexHtml = Files.readString(outputDir.resolve("index.html"));
        assertTrue(indexHtml.contains("/pets"));
        assertTrue(indexHtml.contains("List pets"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest`
Expected: FAIL — `OpenApiUiGenerator` does not exist

- [ ] **Step 4: Write minimal implementation**

Create `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`.

The generator should:
1. Parse the spec file using `new OpenAPIV3Parser().read(specFile.toString())` — returns an `io.swagger.v3.oas.models.OpenAPI` object
2. Create output directory via `Files.createDirectories(outputDir)`
3. Build an HTML page using bulma-java: `html(title).body(section().content(container().content(...)))`
4. Iterate `openApi.getPaths()` — each entry gives a path string and `PathItem`. Call `pathItem.readOperationsMap()` to get a map of `HttpMethod` → `Operation`
5. Render each operation as a list item showing the method, path, and summary
6. Write `page.render()` to `outputDir/index.html`

Key imports:
- `import static com.github.t1.htmljava.Html.html;`
- `import static com.github.t1.htmljava.HtmlBasics.*;` (provides `div()`, `ul()`, `li()`, `span()`, `p()`, `section()`, etc.)
- `import static com.github.t1.bulmajava.layout.Section.section;`
- `import static com.github.t1.bulmajava.layout.Container.container;`
- `import static com.github.t1.bulmajava.elements.Title.title;`
- `import io.swagger.v3.parser.OpenAPIV3Parser;`

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/
git commit -m "generate index.html with path listing from openapi spec"
```

## Chunk 2: Hierarchical Path Tree + HTMX Fragment Loading

### Task 3: Hierarchical Path Tree

Paths should be grouped by segments. E.g. `/pets` and `/pets/{petId}` both appear under a `pets` node.

**Files:**
- Create: `core/src/test/resources/nested-paths.yaml`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Create test fixture with nested paths**

Create `core/src/test/resources/nested-paths.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Pet Store
  version: 1.0.0
servers:
  - url: https://api.example.com
paths:
  /pets:
    get:
      summary: List pets
      operationId: listPets
      responses:
        '200':
          description: A list of pets
  /pets/{petId}:
    get:
      summary: Get a pet
      operationId: getPet
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: A pet
```

- [ ] **Step 2: Write failing test — paths are nested hierarchically**

```java
@Test
void shouldGroupPathsBySegments() throws Exception {
    var specPath = Path.of(getClass().getResource("/nested-paths.yaml").toURI());

    new OpenApiUiGenerator(specPath, outputDir).generate();

    var indexHtml = Files.readString(outputDir.resolve("index.html"));
    assertTrue(indexHtml.contains("pets"));
    assertTrue(indexHtml.contains("{petId}"));
    // Verify nested structure: {petId} should be inside a nested <ul> under pets
    // The outer list has "pets", the inner list has "{petId}"
    int petsListItem = indexHtml.indexOf("pets");
    int nestedUl = indexHtml.indexOf("<ul", petsListItem);
    int petIdItem = indexHtml.indexOf("{petId}", nestedUl);
    assertTrue(petIdItem > nestedUl, "{petId} should be in a nested list under pets");
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest#shouldGroupPathsBySegments`
Expected: FAIL — current implementation renders a flat list

- [ ] **Step 4: Implement hierarchical path tree**

Refactor the path rendering in `OpenApiUiGenerator`:

1. Build a tree data structure from the paths. Split each path by `/` into segments. Insert each path's segments into a tree where each node holds: segment name, child nodes (map), and optional list of operations.
2. For example, paths `/pets` and `/pets/{petId}` produce:
   - root → `pets` (has GET operation) → `{petId}` (has GET operation)
3. Render the tree as nested `<ul>` / `<li>` elements. Each node with children renders a nested `<ul>` inside its `<li>`.
4. Tree nodes start **collapsed** — child `<ul>` elements have `style="display:none"` or a CSS class like `is-hidden`. This is important for keyboard expand/collapse behavior later.

Whether to extract a separate `PathTreeNode` class: if the tree-building logic is more than ~15 lines, extract it. Otherwise keep it inline. Follow @tdder:unfolding-architecture.

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core`
Expected: PASS (both tests)

- [ ] **Step 6: Commit**

```bash
git add core/
git commit -m "render paths as hierarchical tree"
```

### Task 4: Generate HTMX Fragments + Load via HTMX

Each GET operation gets its own HTML fragment file. The tree nodes use `hx-get` to load fragments into a detail pane.

**Files:**
- Create: `core/src/main/resources/htmx.min.js` (download from unpkg)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Bundle HTMX**

Download htmx.min.js and place it at `core/src/main/resources/htmx.min.js`:

```bash
curl -o core/src/main/resources/htmx.min.js https://unpkg.com/htmx.org@2.0.4/dist/htmx.min.js
```

The generator should copy this file to the output directory:
```java
try (var htmx = getClass().getResourceAsStream("/htmx.min.js")) {
    Files.copy(htmx, outputDir.resolve("htmx.min.js"));
}
```

- [ ] **Step 2: Write failing test — fragment files are generated**

```java
@Test
void shouldGenerateFragmentFiles() throws Exception {
    var specPath = Path.of(getClass().getResource("/nested-paths.yaml").toURI());

    new OpenApiUiGenerator(specPath, outputDir).generate();

    assertTrue(Files.exists(outputDir.resolve("pets/GET.html")));
    assertTrue(Files.exists(outputDir.resolve("pets/{petId}/GET.html")));

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    assertTrue(fragment.contains("List pets"));
    assertTrue(fragment.contains("GET"));
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest#shouldGenerateFragmentFiles`
Expected: FAIL — fragment files don't exist

- [ ] **Step 4: Implement fragment generation**

For each operation in the parsed spec, generate a fragment HTML file. Fragments are **not** full HTML documents — they are partial content that HTMX swaps into the detail pane.

To generate fragments without the `<!DOCTYPE>`/`<html>`/`<head>` wrapper, build elements directly (not via `html()`) and call `.render()`:

```java
var fragment = div().content(
    title(method.name() + " " + path),
    p(operation.getSummary())
);
var fragmentPath = outputDir.resolve(pathToDir(path)).resolve(method.name() + ".html");
Files.createDirectories(fragmentPath.getParent());
Files.writeString(fragmentPath, fragment.render());
```

The `pathToDir()` helper strips the leading `/` from the path (e.g., `/pets/{petId}` → `pets/{petId}`).

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: PASS (all tests)

- [ ] **Step 6: Commit**

```bash
git add core/
git commit -m "generate HTMX fragment files per operation"
```

- [ ] **Step 7: Write failing test — index.html includes HTMX and hx-get attributes**

```java
@Test
void shouldIncludeHtmxAttributes() throws Exception {
    var specPath = Path.of(getClass().getResource("/one-get.yaml").toURI());

    new OpenApiUiGenerator(specPath, outputDir).generate();

    var indexHtml = Files.readString(outputDir.resolve("index.html"));
    assertTrue(indexHtml.contains("htmx.min.js"));
    assertTrue(indexHtml.contains("hx-get=\"pets/GET.html\""));
    assertTrue(indexHtml.contains("hx-target="));
}
```

- [ ] **Step 8: Implement HTMX integration in index.html**

1. Add `<script src="htmx.min.js"></script>` to the page via `html(title).script("htmx.min.js")`
2. Add a detail pane div: `div().id("detail")` as the second column
3. On each tree leaf node (operation), add HTMX attributes:
   - `hx-get="<path>/GET.html"` — relative path to the fragment
   - `hx-target="#detail"` — swap content into the detail pane
   - `hx-swap="innerHTML"` — replace detail pane content

Use bulma-java's `.attr("hx-get", "...")` method to add custom attributes.

- [ ] **Step 9: Run tests**

Run: `mvn test -pl core`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add core/
git commit -m "integrate HTMX for fragment loading"
```

### Task 5: Playwright Test — Fragment Loading Works in Browser

This is a verification/integration test for the end-to-end implemented in Tasks 2-4, not a TDD cycle. It confirms the generated HTML works correctly in a real browser.

**Files:**
- Create: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [ ] **Step 1: Write Playwright test — clicking tree node loads fragment**

```java
package com.github.t1.openapi.ui;

import com.microsoft.playwright.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class BrowserTest {
    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;
    HttpServer server;
    @TempDir Path outputDir;

    @BeforeAll static void setupAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll static void teardownAll() {
        browser.close();
        playwright.close();
    }

    @BeforeEach void setup() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach void teardown() {
        context.close();
        if (server != null) server.stop(0);
    }

    /** Starts a static file server for the output directory. Returns the base URL. */
    String serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            var uriPath = exchange.getRequestURI().getPath();
            if (uriPath.equals("/")) uriPath = "/index.html";
            var file = outputDir.resolve(uriPath.substring(1));
            if (Files.exists(file)) {
                var bytes = Files.readAllBytes(file);
                var contentType = uriPath.endsWith(".js") ? "application/javascript" : "text/html";
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    void generate(String fixture) throws Exception {
        var specPath = Path.of(getClass().getResource("/" + fixture).toURI());
        new OpenApiUiGenerator(specPath, outputDir).generate();
    }

    @Test
    void clickingTreeNodeLoadsFragment() throws Exception {
        generate("one-get.yaml");
        var baseUrl = serve();

        page.navigate(baseUrl);
        page.locator("[hx-get='pets/GET.html']").click();
        page.waitForSelector("#detail :text('List pets')");

        assertTrue(page.locator("#detail").textContent().contains("List pets"));
    }
}
```

- [ ] **Step 2: Run test**

Run: `mvn test -pl core -Dtest=BrowserTest`
Expected: PASS if Tasks 2-4 are complete. If FAIL, check that selectors match the actual DOM structure.

- [ ] **Step 3: Fix any issues and get test green**

- [ ] **Step 4: Commit**

```bash
git add core/
git commit -m "add Playwright test for HTMX fragment loading"
```

## Chunk 3: Keyboard Navigation

### Task 6: Arrow Key Navigation on Path Tree

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Write failing Playwright test — arrow keys move focus**

```java
@Test
void arrowKeysNavigateTree() throws Exception {
    generate("nested-paths.yaml");
    var baseUrl = serve();

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();

    // First item should be focused
    var focused = page.locator("[role='treeitem'][aria-selected='true']");
    assertTrue(focused.textContent().contains("pets"));

    // Arrow down moves to next visible item
    page.keyboard().press("ArrowDown");
    focused = page.locator("[role='treeitem'][aria-selected='true']");
    assertNotNull(focused.textContent());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=BrowserTest#arrowKeysNavigateTree`
Expected: FAIL — no `role="tree"` attributes exist, no keyboard JS

- [ ] **Step 3: Implement ARIA roles and keyboard navigation JS**

Two parts:

**Part A: ARIA roles in HTML generation.** Modify the tree rendering in `OpenApiUiGenerator`:
- Outer `<ul>` gets `role="tree"` and `tabindex="0"` (makes it focusable)
- Each `<li>` gets `role="treeitem"`
- Nested `<ul>` (children) gets `role="group"`
- First treeitem gets `aria-selected="true"`

Use `.attr("role", "tree")`, `.attr("tabindex", "0")`, etc. via bulma-java.

**Part B: Keyboard navigation JS.** Add inline JavaScript to `index.html` (via `html(title).javaScriptCode(...)` or by writing a separate `.js` file). The script:

```javascript
document.addEventListener('DOMContentLoaded', function() {
    var tree = document.querySelector('[role="tree"]');
    if (!tree) return;

    tree.addEventListener('keydown', function(e) {
        var items = Array.from(tree.querySelectorAll('[role="treeitem"]:not([style*="display: none"])'));
        // also include items whose parent is not hidden
        var current = tree.querySelector('[aria-selected="true"]');
        var idx = items.indexOf(current);

        switch (e.key) {
            case 'ArrowDown':
                e.preventDefault();
                if (idx < items.length - 1) selectItem(items[idx + 1]);
                break;
            case 'ArrowUp':
                e.preventDefault();
                if (idx > 0) selectItem(items[idx - 1]);
                break;
            case 'ArrowRight':
                e.preventDefault();
                // Expand: show the child [role="group"] ul
                var group = current.querySelector('[role="group"]');
                if (group) group.style.display = '';
                break;
            case 'ArrowLeft':
                e.preventDefault();
                // Collapse: hide the child [role="group"] ul
                var group = current.querySelector('[role="group"]');
                if (group && group.style.display !== 'none') {
                    group.style.display = 'none';
                } else {
                    // Move to parent treeitem
                    var parentGroup = current.closest('[role="group"]');
                    if (parentGroup) {
                        var parentItem = parentGroup.closest('[role="treeitem"]');
                        if (parentItem) selectItem(parentItem);
                    }
                }
                break;
            case 'Enter':
                e.preventDefault();
                // Trigger HTMX load by clicking the hx-get element
                var hxEl = current.querySelector('[hx-get]') || current;
                if (hxEl.getAttribute('hx-get')) htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail');
                break;
            case 'Tab':
                // Move focus to detail pane (default browser behavior with tabindex)
                break;
            case 'Escape':
                e.preventDefault();
                tree.focus();
                break;
        }
    });

    function selectItem(item) {
        tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) {
            el.removeAttribute('aria-selected');
        });
        item.setAttribute('aria-selected', 'true');
    }
});
```

Add CSS to highlight the selected item:
```css
[role="treeitem"][aria-selected="true"] > * { background-color: var(--bulma-primary-light, #ebfffc); }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=BrowserTest#arrowKeysNavigateTree`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/
git commit -m "add arrow key navigation on path tree"
```

- [ ] **Step 6: Write failing test — Enter loads fragment via keyboard**

```java
@Test
void enterKeyLoadsFragment() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();
    page.keyboard().press("Enter");
    page.waitForSelector("#detail :text('List pets')");

    assertTrue(page.locator("#detail").textContent().contains("List pets"));
}
```

- [ ] **Step 7: Run test, make it pass**

Run: `mvn test -pl core -Dtest=BrowserTest#enterKeyLoadsFragment`
Expected: PASS (Enter handler already implemented in Step 3)

- [ ] **Step 8: Commit**

```bash
git add core/
git commit -m "Enter key loads HTMX fragment"
```

- [ ] **Step 9: Write failing test — expand/collapse with arrow keys**

Tree nodes start collapsed (child `<ul role="group">` has `display:none`).

```java
@Test
void arrowRightExpandsAndLeftCollapsesNode() throws Exception {
    generate("nested-paths.yaml");
    var baseUrl = serve();

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();

    // Initially, child nodes should be collapsed (hidden)
    assertFalse(page.locator(":text('{petId}')").isVisible());

    // ArrowRight expands the "pets" node
    page.keyboard().press("ArrowRight");
    assertTrue(page.locator(":text('{petId}')").isVisible());

    // ArrowLeft collapses it
    page.keyboard().press("ArrowLeft");
    assertFalse(page.locator(":text('{petId}')").isVisible());
}
```

- [ ] **Step 10: Run test, make it pass**

Run: `mvn test -pl core -Dtest=BrowserTest#arrowRightExpandsAndLeftCollapsesNode`
Expected: PASS if tree starts collapsed and ArrowRight/ArrowLeft toggle display. If FAIL, ensure the generated HTML renders child `[role="group"]` with `style="display:none"` by default.

- [ ] **Step 11: Commit**

```bash
git add core/
git commit -m "arrow left/right expands and collapses tree nodes"
```

- [ ] **Step 12: Write failing test — Tab moves to detail, Escape returns**

```java
@Test
void tabAndEscapeMoveFocus() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();
    page.keyboard().press("Enter"); // load a fragment first
    page.waitForSelector("#detail :text('List pets')");

    page.keyboard().press("Tab");
    var focusInDetail = (Boolean) page.evaluate(
            "() => document.activeElement.closest('#detail') !== null");
    assertTrue(focusInDetail);

    page.keyboard().press("Escape");
    var treeHasFocus = (Boolean) page.evaluate(
            "() => document.activeElement.closest('[role=\"tree\"]') !== null"
            + " || document.activeElement === document.querySelector('[role=\"tree\"]')");
    assertTrue(treeHasFocus);
}
```

- [ ] **Step 13: Run test, make it pass**

The detail pane `<div id="detail">` needs `tabindex="0"` to be focusable. The Escape handler should call `document.querySelector('[role="tree"]').focus()`.

Run: `mvn test -pl core -Dtest=BrowserTest#tabAndEscapeMoveFocus`

- [ ] **Step 14: Commit**

```bash
git add core/
git commit -m "Tab/Escape move focus between tree and detail pane"
```

## Chunk 3b: Responsive Layout

### Task 7: Responsive Two-Panel Layout

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Write failing Playwright test — layout is side-by-side on desktop**

```java
@Test
void desktopLayoutIsSideBySide() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();

    page.setViewportSize(1280, 720);
    page.navigate(baseUrl);

    var treeBox = page.locator("[role='tree']").boundingBox();
    var detailBox = page.locator("#detail").boundingBox();
    assertTrue(treeBox.x < detailBox.x,
            "Tree should be left of detail pane on desktop");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=BrowserTest#desktopLayoutIsSideBySide`

- [ ] **Step 3: Implement two-column layout with Bulma columns**

Wrap the tree and detail pane in Bulma columns. Use `columns()` with the tree in a narrower column (e.g., `is-one-third`) and detail in the rest. Bulma columns with `is-desktop` modifier stack on screens narrower than 1024px:

```java
columns().attr("class", "columns is-desktop").content(
    column().attr("class", "column is-one-third").content(pathTree),
    column().content(div().id("detail").attr("tabindex", "0"))
)
```

The exact bulma-java API for column sizing may differ — check if there's a `ColumnSize` enum or use `.is(...)` modifiers.

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Write test — layout stacks on narrow viewport**

```java
@Test
void mobileLayoutIsStacked() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();

    page.setViewportSize(375, 667);
    page.navigate(baseUrl);

    var treeBox = page.locator("[role='tree']").boundingBox();
    var detailBox = page.locator("#detail").boundingBox();
    assertTrue(treeBox.y < detailBox.y,
            "Tree should be above detail pane on mobile");
}
```

- [ ] **Step 6: Run test — should pass if Bulma responsive columns are correctly applied**

If FAIL, verify the `is-desktop` class is on the columns container.

- [ ] **Step 7: Commit**

```bash
git add core/
git commit -m "responsive two-panel layout with Bulma columns"
```

## Chunk 4: Parameter Forms + URL Construction

### Task 8: Parameter Input Fields in Fragments

**Files:**
- Create: `core/src/test/resources/params.yaml` (spec with path and query params)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Create test fixture with parameters**

Create `core/src/test/resources/params.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Pet Store
  version: 1.0.0
servers:
  - url: https://api.example.com
paths:
  /pets/{petId}:
    get:
      summary: Get a pet
      operationId: getPet
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: string
        - name: fields
          in: query
          required: false
          schema:
            type: string
          description: Comma-separated list of fields
      responses:
        '200':
          description: A pet
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
                  name:
                    type: string
```

- [ ] **Step 2: Write failing test — fragment contains input fields for parameters**

```java
@Test
void shouldGenerateParameterInputs() throws Exception {
    var specPath = Path.of(getClass().getResource("/params.yaml").toURI());

    new OpenApiUiGenerator(specPath, outputDir).generate();

    var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
    // Path param
    assertTrue(fragment.contains("name=\"petId\""));
    // Query param
    assertTrue(fragment.contains("name=\"fields\""));
    // Help text for query param
    assertTrue(fragment.contains("Comma-separated list of fields"));
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest#shouldGenerateParameterInputs`

- [ ] **Step 4: Implement parameter rendering in fragments**

In the fragment generation code, iterate over `operation.getParameters()`. Each `io.swagger.v3.oas.models.parameters.Parameter` has:
- `getName()` — parameter name (e.g., "petId")
- `getIn()` — "path" or "query"
- `getRequired()` — boolean
- `getDescription()` — optional help text
- `getSchema()` — type info

Render each parameter as a Bulma form field:

```java
for (var param : operation.getParameters()) {
    var inputField = field(param.getName())
        .content(input(TEXT).attr("name", param.getName()));
    if (param.getDescription() != null) {
        inputField = inputField.help(param.getDescription());
    }
    // Add to fragment content
}
```

Group path parameters under a "Path Parameters" heading and query parameters under "Query Parameters".

- [ ] **Step 5: Run tests**

Run: `mvn test -pl core`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/
git commit -m "render parameter input fields in operation fragments"
```

- [ ] **Step 7: Write failing test — fragment shows response schema as JSON block**

```java
@Test
void shouldRenderResponseSchema() throws Exception {
    var specPath = Path.of(getClass().getResource("/params.yaml").toURI());

    new OpenApiUiGenerator(specPath, outputDir).generate();

    var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
    // Should contain the response schema properties
    assertTrue(fragment.contains("id"));
    assertTrue(fragment.contains("name"));
    assertTrue(fragment.contains("string"));
}
```

- [ ] **Step 8: Implement response schema rendering**

Read the `200` response's content schema from `operation.getResponses().get("200").getContent().get("application/json").getSchema()`. Render the schema as a prettified JSON block in a `<pre><code>` element. Use Jackson or manual formatting to convert the schema object to readable JSON.

- [ ] **Step 9: Run tests**

- [ ] **Step 10: Commit**

```bash
git add core/
git commit -m "render response schema as JSON block in fragments"
```

## Chunk 5: Mode Switch + Try/Copy

### Task 9: Global Mode Toggle (Try / httpie / curl)

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Write failing Playwright test — mode toggle exists and switches active mode**

```java
@Test
void modeToggleHasThreeOptionsAndSwitches() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();

    page.navigate(baseUrl);

    assertTrue(page.locator("button:text('Try')").isVisible());
    assertTrue(page.locator("button:text('httpie')").isVisible());
    assertTrue(page.locator("button:text('curl')").isVisible());

    // Click curl mode
    page.locator("button:text('curl')").click();
    // Verify it becomes active
    var mode = (String) page.evaluate(
            "() => document.querySelector('[data-mode]').getAttribute('data-mode')");
    assertEquals("curl", mode);
}
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement mode toggle in index.html**

Add a Bulma button group (`.buttons.has-addons`) at the top of the page with three buttons: Try (default/active), httpie, curl. Store the active mode in a `data-mode` attribute on a container div. JS click handler updates `data-mode` and toggles the `is-selected`/`is-primary` class on the active button.

Also add a `<meta name="api-base-url" content="...">` tag with the first server URL (or `/` if no servers defined) so JS can read it.

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git add core/
git commit -m "add global Try/httpie/curl mode toggle"
```

### Task 10: Copy as curl/httpie

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Write failing Playwright test — curl mode copies command to clipboard**

```java
@Test
void curlModeCopiesCommand() throws Exception {
    generate("params.yaml");

    context.close();
    context = browser.newContext(new Browser.NewContextOptions()
            .setPermissions(java.util.List.of("clipboard-read", "clipboard-write")));
    page = context.newPage();

    var baseUrl = serve();
    page.navigate(baseUrl);

    // Select curl mode
    page.locator("button:text('curl')").click();
    // Load the fragment
    page.locator("[hx-get='pets/{petId}/GET.html']").click();
    page.waitForSelector("#detail input[name='petId']");
    // Fill in petId
    page.locator("#detail input[name='petId']").fill("42");
    // Click the action button
    page.locator("#detail button:text('Send')").click();

    var clipboard = (String) page.evaluate("() => navigator.clipboard.readText()");
    assertTrue(clipboard.contains("curl"), "Expected curl command, got: " + clipboard);
    assertTrue(clipboard.contains("https://api.example.com/pets/42"),
            "Expected URL with petId=42, got: " + clipboard);
}
```

- [ ] **Step 2: Write failing Playwright test — httpie mode copies command to clipboard**

```java
@Test
void httpieModeCopiesCommand() throws Exception {
    generate("params.yaml");

    context.close();
    context = browser.newContext(new Browser.NewContextOptions()
            .setPermissions(java.util.List.of("clipboard-read", "clipboard-write")));
    page = context.newPage();

    var baseUrl = serve();
    page.navigate(baseUrl);

    // Select httpie mode
    page.locator("button:text('httpie')").click();
    // Load the fragment
    page.locator("[hx-get='pets/{petId}/GET.html']").click();
    page.waitForSelector("#detail input[name='petId']");
    page.locator("#detail input[name='petId']").fill("42");
    page.locator("#detail button:text('Send')").click();

    var clipboard = (String) page.evaluate("() => navigator.clipboard.readText()");
    assertTrue(clipboard.contains("http GET"), "Expected httpie command, got: " + clipboard);
    assertTrue(clipboard.contains("https://api.example.com/pets/42"),
            "Expected URL with petId=42, got: " + clipboard);
}
```

- [ ] **Step 3: Run tests to verify they fail**

- [ ] **Step 4: Implement URL construction and clipboard copy**

Add JS in the fragment (or in the main page's script) that:

1. On "Send" button click, reads the current mode from `document.querySelector('[data-mode]').getAttribute('data-mode')`
2. Reads parameter values from all `<input>` fields in the fragment
3. Reads the base URL from `document.querySelector('meta[name="api-base-url"]').content`
4. Constructs the full URL: substitute path params into the URL template, append query params
5. In `curl` mode: copy `curl https://api.example.com/pets/42` to clipboard via `navigator.clipboard.writeText()`
6. In `httpie` mode: copy `http GET https://api.example.com/pets/42` to clipboard
7. Show brief visual feedback (e.g., button text changes to "Copied!" for 1 second)

The fragment's "Send" button should have `data-path="/pets/{petId}"` and `data-method="GET"` attributes so the JS knows which path template and method to use.

- [ ] **Step 5: Run tests to verify they pass**

- [ ] **Step 6: Commit**

```bash
git add core/
git commit -m "copy as curl/httpie with parameter substitution"
```

### Task 11: Try Mode — Send Request and Show Response

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Add mock API endpoint capability to the test server**

Extend the `serve()` method (or add a new helper) to also register mock API endpoints. Add a helper method:

```java
void mockEndpoint(String path, String contentType, String body) {
    server.createContext(path, exchange -> {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        var bytes = body.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    });
}
```

The CORS header (`Access-Control-Allow-Origin: *`) is needed because the browser page is served from the same origin but the mock API conceptually represents an external API. In tests, both are on the same server, but the header ensures no CORS issues.

To make the tests work, the generated HTML's base URL (`<meta name="api-base-url">`) must point to the test server. After calling `generate()`, overwrite the meta tag by manipulating the generated `index.html`:

```java
void overrideBaseUrl(String baseUrl) throws Exception {
    var indexPath = outputDir.resolve("index.html");
    var html = Files.readString(indexPath);
    html = html.replace("https://api.example.com", baseUrl);
    Files.writeString(indexPath, html);
}
```

- [ ] **Step 2: Write failing Playwright test — Try mode sends request and shows prettified JSON**

```java
@Test
void tryModeSendsRequestAndShowsPrettifiedJson() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();
    mockEndpoint("/pets", "application/json", "{\"id\":\"1\",\"name\":\"Fido\"}");
    overrideBaseUrl(baseUrl);

    page.navigate(baseUrl);
    // Try mode is the default
    page.locator("[role='tree']").focus();
    page.keyboard().press("Enter");
    page.waitForSelector("#detail button:text('Send')");
    page.locator("#detail button:text('Send')").click();

    // Wait for response to appear
    page.waitForSelector("#detail pre");
    var responseText = page.locator("#detail pre").textContent();
    assertTrue(responseText.contains("\"name\""), "Expected JSON with name field");
    assertTrue(responseText.contains("Fido"), "Expected JSON with value Fido");
    // Verify it's prettified (has newlines/indentation)
    assertTrue(responseText.contains("\n"), "Expected prettified JSON with newlines");
}
```

- [ ] **Step 3: Run test to verify it fails**

- [ ] **Step 4: Implement Try mode**

In the "Send" button click handler, when mode is `try`:
1. Construct the URL (same as curl/httpie)
2. Call `fetch(url)` from JS
3. Read the `Content-Type` response header
4. If JSON: parse with `JSON.parse()`, format with `JSON.stringify(data, null, 2)`, display in `<pre><code>`
5. Otherwise: display raw text in `<pre>`
6. Show HTTP status code above the response body

- [ ] **Step 5: Run test to verify it passes**

- [ ] **Step 6: Write failing test — HTML response displayed as-is**

```java
@Test
void tryModeShowsHtmlResponseAsIs() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();
    mockEndpoint("/pets", "text/html", "<h1>Hello</h1><p>World</p>");
    overrideBaseUrl(baseUrl);

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();
    page.keyboard().press("Enter");
    page.waitForSelector("#detail button:text('Send')");
    page.locator("#detail button:text('Send')").click();

    page.waitForSelector("#detail pre");
    var responseText = page.locator("#detail pre").textContent();
    assertTrue(responseText.contains("<h1>Hello</h1>"), "HTML should be shown as raw text");
}
```

- [ ] **Step 7: Write failing test — XML response displayed as-is**

```java
@Test
void tryModeShowsXmlResponseAsIs() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();
    mockEndpoint("/pets", "application/xml", "<pets><pet><name>Fido</name></pet></pets>");
    overrideBaseUrl(baseUrl);

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();
    page.keyboard().press("Enter");
    page.waitForSelector("#detail button:text('Send')");
    page.locator("#detail button:text('Send')").click();

    page.waitForSelector("#detail pre");
    var responseText = page.locator("#detail pre").textContent();
    assertTrue(responseText.contains("<pets>"), "XML should be shown as raw text");
}
```

- [ ] **Step 8: Write failing test — YAML response displayed as-is**

```java
@Test
void tryModeShowsYamlResponseAsIs() throws Exception {
    generate("one-get.yaml");
    var baseUrl = serve();
    mockEndpoint("/pets", "application/yaml", "pets:\n  - name: Fido\n    id: 1");
    overrideBaseUrl(baseUrl);

    page.navigate(baseUrl);
    page.locator("[role='tree']").focus();
    page.keyboard().press("Enter");
    page.waitForSelector("#detail button:text('Send')");
    page.locator("#detail button:text('Send')").click();

    page.waitForSelector("#detail pre");
    var responseText = page.locator("#detail pre").textContent();
    assertTrue(responseText.contains("pets:"), "YAML should be shown as raw text");
    assertTrue(responseText.contains("Fido"), "YAML should contain data");
}
```

- [ ] **Step 9: Run all content-type tests, implement content-type detection**

The JS `fetch` handler checks `response.headers.get('Content-Type')`. If it contains `json`, prettify. Otherwise display as raw text. The HTML/XML/YAML tests should pass without special handling since they all fall into the "display as raw text" path.

- [ ] **Step 10: Run all tests**

Run: `mvn test -pl core`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add core/
git commit -m "Try mode sends requests and renders responses by content type"
```

## Chunk 6: Maven Plugin + CLI

### Task 12: Maven Plugin

**Files:**
- Modify: `maven-plugin/pom.xml` (change packaging, add plugin dependencies)
- Create: `maven-plugin/src/main/java/com/github/t1/openapi/ui/maven/GenerateMojo.java`
- Create: `maven-plugin/src/test/java/com/github/t1/openapi/ui/maven/GenerateMojoTest.java`

- [ ] **Step 1: Update maven-plugin POM**

Change packaging to `maven-plugin` and add required dependencies:

```xml
<packaging>maven-plugin</packaging>

<dependencies>
    <dependency>
        <groupId>com.github.t1</groupId>
        <artifactId>openapi-ui-core</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.apache.maven</groupId>
        <artifactId>maven-plugin-api</artifactId>
        <version>3.9.9</version>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.apache.maven.plugin-tools</groupId>
        <artifactId>maven-plugin-annotations</artifactId>
        <version>3.15.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

- [ ] **Step 2: Write failing test for the Mojo**

```java
package com.github.t1.openapi.ui.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateMojoTest {
    @TempDir Path tempDir;

    @Test
    void shouldGenerateOutput() throws Exception {
        var specFile = tempDir.resolve("spec.yaml");
        Files.writeString(specFile, """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /test:
                    get:
                      summary: Test endpoint
                      responses:
                        '200':
                          description: OK
                """);
        var outputDir = tempDir.resolve("output");

        var mojo = new GenerateMojo();
        mojo.specFile = specFile.toFile();
        mojo.outputDirectory = outputDir.toFile();
        mojo.execute();

        assertTrue(Files.exists(outputDir.resolve("index.html")));
        assertTrue(Files.exists(outputDir.resolve("test/GET.html")));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl maven-plugin -Dtest=GenerateMojoTest`

- [ ] **Step 4: Implement GenerateMojo**

```java
package com.github.t1.openapi.ui.maven;

import com.github.t1.openapi.ui.OpenApiUiGenerator;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GenerateMojo extends AbstractMojo {
    @Parameter(property = "openapi.specFile", required = true)
    File specFile;

    @Parameter(property = "openapi.outputDirectory",
               defaultValue = "${project.build.directory}/openapi-ui")
    File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            new OpenApiUiGenerator(specFile.toPath(), outputDirectory.toPath())
                    .generate();
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate OpenAPI UI", e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl maven-plugin -Dtest=GenerateMojoTest`

- [ ] **Step 6: Commit**

```bash
git add maven-plugin/
git commit -m "add Maven plugin wrapping core generator"
```

### Task 13: CLI with Really Executable Jar

**Files:**
- Modify: `cli/pom.xml`
- Create: `cli/src/main/java/com/github/t1/openapi/ui/cli/Main.java`
- Create: `cli/src/test/java/com/github/t1/openapi/ui/cli/MainTest.java`

- [ ] **Step 1: Update cli POM with shade plugin and antrun for executable jar**

The shade plugin must be declared **before** the antrun plugin so it runs first in the `package` phase (both bind to `package`, Maven runs them in declaration order).

```xml
<build>
    <plugins>
        <!-- shade FIRST: creates the fat jar -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.6.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.github.t1.openapi.ui.cli.Main</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
        <!-- antrun SECOND: prepends shell header to the shaded jar -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-antrun-plugin</artifactId>
            <version>3.1.0</version>
            <executions>
                <execution>
                    <id>make-executable</id>
                    <phase>package</phase>
                    <goals><goal>run</goal></goals>
                    <configuration>
                        <target>
                            <concat destfile="${project.build.directory}/openapi-ui" binary="true">
                                <header filtering="false">#!/bin/sh
exec java -jar "$0" "$@"
</header>
                                <fileset file="${project.build.directory}/${project.build.finalName}.jar"/>
                            </concat>
                            <chmod file="${project.build.directory}/openapi-ui" perm="755"/>
                        </target>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

- [ ] **Step 2: Write failing test for Main**

```java
package com.github.t1.openapi.ui.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path tempDir;

    @Test
    void shouldGenerateFromArgs() throws Exception {
        var specFile = tempDir.resolve("spec.yaml");
        Files.writeString(specFile, """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /test:
                    get:
                      summary: Test endpoint
                      responses:
                        '200':
                          description: OK
                """);
        var outputDir = tempDir.resolve("output");

        Main.main(new String[]{specFile.toString(), outputDir.toString()});

        assertTrue(Files.exists(outputDir.resolve("index.html")));
    }

    @Test
    void shouldPrintUsageWithNoArgs() {
        var ex = assertThrows(IllegalArgumentException.class, () ->
                Main.main(new String[]{}));
        assertTrue(ex.getMessage().contains("Usage"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl cli -Dtest=MainTest`

- [ ] **Step 4: Implement Main**

```java
package com.github.t1.openapi.ui.cli;

import com.github.t1.openapi.ui.OpenApiUiGenerator;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: openapi-ui <spec-file> <output-dir>");
        }
        new OpenApiUiGenerator(Path.of(args[0]), Path.of(args[1])).generate();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl cli -Dtest=MainTest`

- [ ] **Step 6: Commit**

```bash
git add cli/
git commit -m "add CLI with Main class"
```

- [ ] **Step 7: Build and verify the executable jar**

Run: `mvn package -pl cli -am -DskipTests && ./cli/target/openapi-ui`
Expected: prints usage error (`Usage: openapi-ui <spec-file> <output-dir>`)

- [ ] **Step 8: Commit any build config fixes**

```bash
git add cli/pom.xml
git commit -m "make CLI a really executable jar with shell header"
```

- [ ] **Step 9: Full build and test**

Run: `mvn clean verify`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 10: Commit any final fixes**

Only if needed. Stage specific files:

```bash
git add core/ maven-plugin/ cli/ pom.xml
git commit -m "complete MVP build"
```
