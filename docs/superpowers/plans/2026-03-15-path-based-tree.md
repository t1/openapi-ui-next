# Path-Based Tree with Method Tabs Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the tree to show paths only (with method tag addons) and move method selection to HTMX tabs in the detail pane, eliminating vertical layout jumps from summary text.

**Architecture:** Tree items become path-centric (no per-method items). Selecting a path loads a new path-level `index.html` fragment containing a tab bar and pre-rendered first method content. Tab switching loads individual method fragments via HTMX. Keyboard navigation spans three focus levels: tree → tabs → fields.

**Tech Stack:** Java 21, bulma-java, swagger-parser, Playwright (browser tests), Quarkus (demo), JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-15-path-based-tree-design.md`

**Test patterns in this codebase:**
- **File-based tests** go in `OpenApiUiGeneratorTest`: use `@TempDir Path outputDir`, call `generate("/spec.yaml")`, read output with `Files.readString()`. Use `then(...)` from AssertJ BDD.
- **Browser tests** go in `BrowserTest`: use `@RegisterExtension static TestContext context = new TestContext()` at class level, then `@RegisterExtension static AppFixture app = context.launch("spec.yaml")` per nested class. Use `app.page()`, `app.focusTree()`, `app.pressKey()`, `app.detailText()`, etc.

---

## Chunk 1: Tree Structure — Remove Per-Method Items, Add Method Tag Addons

### Task 1: Create a multi-method test spec

**Files:**
- Create: `core/src/test/resources/multi-method.yaml`

- [ ] **Step 1: Create the test spec**

```yaml
openapi: 3.0.3
info:
  title: Multi Method API
  version: "1.0"
paths:
  /pets:
    get:
      summary: List pets
      description: Returns all pets from the system.
      tags:
        - pets
      responses:
        "200":
          description: OK
    post:
      summary: Create a pet
      description: Adds a new pet to the store.
      tags:
        - pets
      responses:
        "201":
          description: Created
  /pets/{petId}:
    get:
      summary: Get pet by ID
      description: Returns a single pet.
      deprecated: true
      externalDocs:
        url: https://example.com/docs/pets
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: integer
      responses:
        "200":
          description: OK
    delete:
      summary: Delete a pet
      description: Permanently removes a pet.
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: integer
      responses:
        "204":
          description: Deleted
```

- [ ] **Step 2: Stage and commit**

```bash
git add core/src/test/resources/multi-method.yaml
git commit -m "add multi-method test spec"
```

### Task 2: Change tree to show paths with method tag addons

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `addNodes()` (both overloads), `addNodeOperations()`, `addLeafOperations()`, `operationLabel()`, `APP_CSS`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`

- [ ] **Step 1: Write failing test — tree items show path with method tag addons, no summary**

In `OpenApiUiGeneratorTest.java`, add a file-based test:

```java
@Test void shouldRenderMethodTagAddons() throws Exception {
    generate("/multi-method.yaml");

    var indexHtml = Files.readString(outputDir.resolve("index.html"));
    // Method addons in tree, not operation labels
    then(indexHtml).contains("class=\"method-addon");
    then(indexHtml).doesNotContain("class=\"tree-op-summary\"");
    then(indexHtml).doesNotContain("class=\"tree-op-label\"");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderMethodTagAddons'`
Expected: FAIL — current tree renders per-method labels with `tree-op-label` and `tree-op-summary` classes

- [ ] **Step 3: Implement — replace per-method tree items with method tag addons**

In `OpenApiUiGenerator.java`:

1. Replace `operationLabel()` with a new `methodAddon()` method:

```java
private static Element methodAddon(HttpMethod method) {
    return span(method.name()).classes("method-addon", "method-" + method.name().toLowerCase());
}
```

2. Change `addNodes(Tree tree, PathNode node, String pathPrefix)` to build path items with inline method addons and `hx-get` pointing to the path's `index.html`:

```java
private void addNodes(Tree tree, PathNode node, String pathPrefix) {
    for (var entry : node.children.entrySet()) {
        var segment = entry.getKey();
        var child = entry.getValue();
        var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
        var label = span().content(span(segment).classes(segmentClass(segment)));
        for (var method : child.operations.keySet()) {
            label.content(methodAddon(method));
        }
        if (!child.children.isEmpty()) {
            tree.node(label, sub -> addNodes(sub, child, fullPath));
        } else {
            tree.item(label, item -> {
                if (!child.operations.isEmpty()) {
                    item.attr("hx-get", fullPath + "/index.html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML");
                }
            });
        }
    }
}
```

3. Change `addNodes(Tree.Node node, PathNode pathNode, String pathPrefix)` similarly:

```java
private void addNodes(Tree.Node treeNode, PathNode pathNode, String pathPrefix) {
    for (var entry : pathNode.children.entrySet()) {
        var segment = entry.getKey();
        var child = entry.getValue();
        var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
        var label = span().content(span(segment).classes(segmentClass(segment)));
        for (var method : child.operations.keySet()) {
            label.content(methodAddon(method));
        }
        if (!child.children.isEmpty()) {
            treeNode.node(label, sub -> addNodes(sub, child, fullPath));
        } else {
            treeNode.item(label, item -> {
                if (!child.operations.isEmpty()) {
                    item.attr("hx-get", fullPath + "/index.html")
                            .attr("hx-target", "#detail")
                            .attr("hx-swap", "innerHTML");
                }
            });
        }
    }
}
```

4. For nodes (paths with children) that also have operations, add `hx-get` to the node. This requires adding an `hx-get` attribute to the node's content. Use the `Tree.Node.content()` method or adjust the `Tree` API to support `hx-get` on node items. If the Tree API does not support attributes on node `<li>` elements, the `hx-get` can go on a wrapper span inside the node label:

```java
if (!child.operations.isEmpty()) {
    label.attr("hx-get", fullPath + "/index.html")
            .attr("hx-target", "#detail")
            .attr("hx-swap", "innerHTML");
}
```

5. Remove `addNodeOperations()`, `addLeafOperations()`, and `operationLabel()` — they are no longer used.

6. Add CSS for method addons in `APP_CSS`:

```css
.method-addon {
    font-size: 0.65rem;
    padding: 1px 5px;
    border-radius: 3px;
    color: white;
    font-weight: 600;
    margin-left: 3px;
    opacity: 0.85;
    vertical-align: middle;
}
.method-get { background: var(--bulma-success); }
.method-post { background: var(--bulma-link); }
.method-put { background: var(--bulma-warning); color: var(--bulma-text-strong); }
.method-delete { background: var(--bulma-danger); }
.method-patch { background: var(--bulma-primary); }
```

7. Remove the old CSS rules from `APP_CSS`: `.tree-op-label { ... }`, `.tree-op-summary { ... }`, and `[aria-selected="true"] > .tree-op-label > .tree-op-summary { ... }`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderMethodTagAddons'`
Expected: PASS

- [ ] **Step 5: Fix broken existing tests**

Run: `mvn test -pl core`

These existing tests will break and need updating:

1. `shouldIncludeHtmxAttributes` — change expected `hx-get` from `"pets/GET.html"` to `"pets/index.html"`:
```java
then(indexHtml).contains("hx-get=\"pets/index.html\"");
```

2. `shouldHideSummaryOnNonSelectedItems` — this test validates the old summary-hiding CSS. Delete this test entirely (the summary-hiding mechanism is removed by design).

3. `shouldWrapSummaryInSpan` — this test validates the old summary span. Delete this test entirely.

4. In `BrowserTest`, tests that use `app.clickTreeNode("pets/GET.html")` — the `hx-get` value changes to `"pets/index.html"`. Update `AppFixture.clickTreeNode()` calls or the method itself if it uses `hx-get` as a selector.

5. `hasMethodBadge` — the tree now uses `.method-addon` elements instead of `.tag` elements for methods. Update to look for `.method-addon` or check the detail pane's method badge instead.

- [ ] **Step 6: Run all tests to verify they pass**

Run: `mvn test -pl core`
Expected: All PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git commit -m "tree shows paths with method tag addons instead of per-method items"
```

---

## Chunk 2: Path Fragment Generation (HTMX Tabs)

### Task 3: Generate path-level index.html fragments with tab bar

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `generateFragments()`, add `generatePathFragment()`, extract `buildMethodFragmentContent()`
- Test: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`

- [ ] **Step 1: Write failing test — path fragment with tabs exists**

In `OpenApiUiGeneratorTest.java`:

```java
@Test void shouldGeneratePathFragmentWithTabs() throws Exception {
    generate("/multi-method.yaml");

    var pathFragment = Files.readString(outputDir.resolve("pets/index.html"));
    then(pathFragment).contains("class=\"tabs\"");
    then(pathFragment).contains("hx-get=\"pets/GET.html\"");
    then(pathFragment).contains("hx-get=\"pets/POST.html\"");
    then(pathFragment).contains("id=\"method-content\"");
    // First method content pre-rendered
    then(pathFragment).contains("List pets");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldGeneratePathFragmentWithTabs'`
Expected: FAIL — no `index.html` generated for paths

- [ ] **Step 3: Implement — extract buildMethodFragmentContent and add path fragment generation**

In `OpenApiUiGenerator.java`:

1. Extract the method fragment building logic from `generateFragments()` into a reusable method. The current code inside the `for (var opEntry : ...)` loop (lines ~196-261) becomes:

```java
private Element buildMethodFragmentContent(HttpMethod method, Operation operation, String fullPath) {
    var summary = operation.getSummary() != null ? operation.getSummary() : "";
    var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
    var fragment = div().content(
            div().classes("is-flex", "is-align-items-center", "mb-5").style("gap:0.75rem").content(
                    headingBadge,
                    element("h2").classes("title", "is-4", "mb-0", "endpoint-path")
                            .content("/" + fullPath)),
            p(summary).classes("op-summary")
    );
    if (operation.getParameters() != null) {
        for (var param : operation.getParameters()) {
            var inputField = field(param.getName())
                    .content(input(TEXT).attr("name", param.getName()));
            if (param.getDescription() != null) {
                inputField.help(param.getDescription());
            }
            fragment.content(inputField);
        }
    }
    if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
        var content = operation.getRequestBody().getContent();
        var jsonContent = content.get("application/json");
        if (jsonContent == null) jsonContent = content.get("*/*");
        if (jsonContent != null && jsonContent.getSchema() != null) {
            var skeleton = generateJsonSkeleton(jsonContent.getSchema());
            fragment.content(
                    field("Request Body (application/json)").content(
                            element("textarea")
                                    .attr("data-request-body", "true")
                                    .classes("textarea", "is-family-code")
                                    .attr("rows", "6")
                                    .content(skeleton)));
        }
    }
    if (operation.getResponses() != null) {
        var response200 = operation.getResponses().get("200");
        if (response200 != null && response200.getContent() != null) {
            var jsonMedia = response200.getContent().get("application/json");
            if (jsonMedia != null && jsonMedia.getSchema() != null) {
                @SuppressWarnings("unchecked")
                var properties = (Map<String, Schema<?>>) jsonMedia.getSchema().getProperties();
                if (properties != null) {
                    var sb = new StringBuilder();
                    sb.append("{\n");
                    var first = true;
                    for (var propEntry : properties.entrySet()) {
                        if (!first) sb.append(",\n");
                        first = false;
                        sb.append("  \"").append(propEntry.getKey()).append("\": ")
                                .append(propEntry.getValue().getType());
                    }
                    sb.append("\n}");
                    fragment.content(element("pre").content(code(sb.toString())));
                }
            }
        }
    }
    fragment.content(button("Send").is(PRIMARY)
            .attr("data-path", "/" + fullPath)
            .attr("data-method", method.name()));
    return fragment;
}
```

2. Simplify `generateFragments()` to use the extracted method:

```java
private void generateFragments(PathNode node, String pathPrefix) throws IOException {
    for (var entry : node.children.entrySet()) {
        var segment = entry.getKey();
        var child = entry.getValue();
        var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;

        // Generate individual method fragments
        for (var opEntry : child.operations.entrySet()) {
            var fragment = buildMethodFragmentContent(opEntry.getKey(), opEntry.getValue(), fullPath);
            var fragmentDir = outputDir.resolve(fullPath);
            Files.createDirectories(fragmentDir);
            Files.writeString(fragmentDir.resolve(opEntry.getKey().name() + ".html"), fragment.render());
        }

        // Generate path-level index.html with tab bar
        if (!child.operations.isEmpty()) {
            generatePathFragment(child, fullPath);
        }

        generateFragments(child, fullPath);
    }
}
```

3. Add `generatePathFragment()`:

```java
private void generatePathFragment(PathNode child, String fullPath) throws IOException {
    var tabList = element("ul");
    var first = true;
    Element firstMethodContent = null;

    for (var opEntry : child.operations.entrySet()) {
        var method = opEntry.getKey();
        var li = element("li");
        if (first) li.classes("is-active");
        li.content(element("a").content(method.name())
                .attr("hx-get", fullPath + "/" + method.name() + ".html")
                .attr("hx-target", "#method-content")
                .attr("hx-swap", "innerHTML"));
        tabList.content(li);

        if (first) {
            firstMethodContent = buildMethodFragmentContent(method, opEntry.getValue(), fullPath);
            first = false;
        }
    }

    var fragment = div().content(
            div().classes("tabs").content(tabList),
            div().id("method-content").content(firstMethodContent));

    var fragmentDir = outputDir.resolve(fullPath);
    Files.createDirectories(fragmentDir);
    Files.writeString(fragmentDir.resolve("index.html"), fragment.render());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldGeneratePathFragmentWithTabs'`
Expected: PASS

- [ ] **Step 5: Write test — method fragments still generated alongside index.html**

```java
@Test void shouldStillGenerateMethodFragments() throws Exception {
    generate("/multi-method.yaml");

    then(outputDir.resolve("pets/GET.html")).exists();
    then(outputDir.resolve("pets/POST.html")).exists();
    then(outputDir.resolve("pets/{petId}/GET.html")).exists();
    then(outputDir.resolve("pets/{petId}/DELETE.html")).exists();
    then(outputDir.resolve("pets/index.html")).exists();
    then(outputDir.resolve("pets/{petId}/index.html")).exists();
}
```

- [ ] **Step 6: Run test to verify it passes**

Expected: PASS (should already work from step 3)

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "generate path-level index.html with HTMX tab bar"
```

### Task 4: Add tab switching JS and update auto-load

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `APP_JS`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [ ] **Step 1: Write failing browser test — clicking tab switches content**

In `BrowserTest.java`, add:

```java
@Nested class GivenMultiMethodApp {
    @RegisterExtension static AppFixture app = context.launch("multi-method.yaml");

    @Test void shouldSwitchMethodTabOnClick() {
        // First tab (GET) should be active by default
        app.waitForDetailContent("List pets");
        then(app.detailText()).contains("List pets");

        // Click POST tab
        app.page().click(".tabs li:nth-child(2) a");
        app.waitForDetailContent("Create a pet");

        then(app.detailText()).contains("Create a pet");
    }
}
```

Note: `waitForDetailContent()` may need to be added to `AppFixture` if it doesn't exist. Check existing code — the existing test `enterKeyLoadsFragment` uses `app.waitForDetailContent("List pets")`, so this method exists.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenMultiMethodApp#shouldSwitchMethodTabOnClick'`
Expected: FAIL — no tab switching JS; the `is-active` class doesn't move on click

- [ ] **Step 3: Implement tab switching JS and update auto-load**

In `APP_JS`:

1. Add tab switching via HTMX event:

```javascript
// Tab switching — toggle is-active when HTMX swaps method content
document.body.addEventListener('htmx:beforeSwap', function(e) {
    if (e.detail.target.id === 'method-content') {
        var tabLink = e.detail.elt;
        var tabs = tabLink.closest('.tabs');
        if (tabs) {
            tabs.querySelectorAll('li').forEach(function(li) { li.classList.remove('is-active'); });
            tabLink.closest('li').classList.add('is-active');
        }
    }
});
```

2. Update the auto-load logic. The current code loads the first `[hx-get]` element's target. With the new structure, tree items' `hx-get` points to `index.html` (path fragments), so the existing auto-load should still work:

```javascript
// Auto-load the first path fragment
var firstHxEl = document.querySelector('[role="treeitem"] [hx-get]');
if (firstHxEl) htmx.ajax('GET', firstHxEl.getAttribute('hx-get'), '#detail');
```

3. Update the Send button handler: the `detail.querySelector(...)` calls for inputs, textareas, and `pre.response` still work because `querySelector` searches all descendants, so `#method-content` being nested inside `#detail` is transparent.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenMultiMethodApp#shouldSwitchMethodTabOnClick'`
Expected: PASS

- [ ] **Step 5: Run all tests**

Run: `mvn test -pl core`
Fix any remaining broken tests.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "add HTMX tab switching for method tabs"
```

---

## Chunk 3: Enriched Method Fragments

### Task 5: Add description, deprecated, tags, externalDocs to method fragments

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `buildMethodFragmentContent()`, `APP_CSS`
- Test: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

All new tests in this task go in `OpenApiUiGeneratorTest` (file-based, since we're validating generated HTML content) unless they require browser interaction.

- [ ] **Step 1: Write failing test — description is rendered**

```java
@Test void shouldRenderDescription() throws Exception {
    generate("/multi-method.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    then(fragment).contains("Returns all pets from the system.");
    then(fragment).contains("op-description");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderDescription'`
Expected: FAIL

- [ ] **Step 3: Implement — add description to buildMethodFragmentContent**

In `buildMethodFragmentContent()`, after the summary paragraph:

```java
if (operation.getDescription() != null) {
    fragment.content(p(operation.getDescription()).classes("op-description"));
}
```

Add CSS to `APP_CSS`:

```css
#detail .op-description {
    color: var(--bulma-text);
    margin-bottom: 1rem;
    line-height: 1.6;
}
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Write failing test — deprecated badge**

```java
@Test void shouldRenderDeprecatedBadge() throws Exception {
    generate("/multi-method.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
    then(fragment).contains("DEPRECATED");
    then(fragment).contains("deprecated-badge");
}
```

- [ ] **Step 6: Run test to verify it fails, implement, verify it passes**

In `buildMethodFragmentContent()`, after the description:

```java
if (Boolean.TRUE.equals(operation.getDeprecated())) {
    fragment.content(span("DEPRECATED").classes("tag", "is-warning", "deprecated-badge"));
}
```

Add CSS:

```css
.deprecated-badge {
    font-weight: 600;
}
```

- [ ] **Step 7: Write failing test — tags**

```java
@Test void shouldRenderTags() throws Exception {
    generate("/multi-method.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    then(fragment).contains("op-tag");
    then(fragment).contains("pets");
}
```

- [ ] **Step 8: Run test to verify it fails, implement, verify it passes**

In `buildMethodFragmentContent()`:

```java
if (operation.getTags() != null && !operation.getTags().isEmpty()) {
    var tagsRow = div().classes("tags");
    for (var t : operation.getTags()) {
        tagsRow.content(span(t).classes("tag", "op-tag"));
    }
    fragment.content(tagsRow);
}
```

- [ ] **Step 9: Write failing test — external docs**

```java
@Test void shouldRenderExternalDocs() throws Exception {
    generate("/multi-method.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/{petId}/GET.html"));
    then(fragment).contains("external-docs");
    then(fragment).contains("https://example.com/docs/pets");
}
```

- [ ] **Step 10: Run test to verify it fails, implement, verify it passes**

In `buildMethodFragmentContent()`:

```java
if (operation.getExternalDocs() != null) {
    fragment.content(div().classes("external-docs").content(
            element("a").attr("href", operation.getExternalDocs().getUrl())
                    .attr("target", "_blank")
                    .content("External docs →")));
}
```

Add CSS:

```css
#detail .external-docs a {
    color: var(--bulma-link);
    text-decoration: underline;
}
```

- [ ] **Step 11: Run all tests**

Run: `mvn test -pl core`
Expected: All PASS

- [ ] **Step 12: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "add description, deprecated, tags, externalDocs to method fragments"
```

---

## Chunk 4: Keyboard Navigation — Three Focus Levels

### Task 6: Update tree keyboard nav — ArrowRight enters tabs, not first child

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java` — JS section
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [ ] **Step 1: Write failing test — ArrowRight on expanded node enters tabs**

In `BrowserTest.GivenMultiMethodApp`:

```java
@Test void shouldEnterTabsOnArrowRight() {
    app.focusTree();
    // "pets" is expanded by default, ArrowRight should enter tabs (not first child)
    app.pressKey("ArrowRight");
    // First tab should have focus
    then(app.page().evaluate("document.activeElement.closest('.tabs') !== null")).isEqualTo(true);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenMultiMethodApp#shouldEnterTabsOnArrowRight'`
Expected: FAIL — current ArrowRight moves to first child

- [ ] **Step 3: Implement — update Tree.java JS ArrowRight/ArrowLeft handling**

In `Tree.java`, update the `ArrowRight` case in the JS:

```javascript
case 'ArrowRight':
    e.preventDefault();
    if (current.getAttribute('aria-expanded') === 'false') {
        toggleNode(current, true);
    } else {
        // Expanded node or leaf → enter tabs
        var activeTabLink = document.querySelector('.tabs .is-active a');
        if (activeTabLink) activeTabLink.focus();
    }
    break;
```

The `ArrowLeft` handler stays as-is (collapse expanded node, or move to parent).

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "ArrowRight on expanded tree node enters method tabs"
```

### Task 7: Tab keyboard navigation with current-tab tracking

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `APP_JS`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [ ] **Step 1: Write failing test — ArrowLeft/Right switches tabs**

```java
@Test void shouldSwitchTabsWithArrowKeys() {
    app.focusTree();
    app.pressKey("ArrowRight"); // enter tabs on first tab (GET)
    app.pressKey("ArrowRight"); // switch to POST tab

    then(app.page().querySelector(".tabs li:nth-child(2)").getAttribute("class")).contains("is-active");
    app.waitForDetailContent("Create a pet");
    then(app.detailText()).contains("Create a pet");

    app.pressKey("ArrowLeft"); // back to GET
    app.waitForDetailContent("List pets");
    then(app.page().querySelector(".tabs li:first-child").getAttribute("class")).contains("is-active");
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL

- [ ] **Step 3: Implement tab keyboard navigation**

Add to `APP_JS`:

```javascript
// Tab keyboard navigation
document.body.addEventListener('keydown', function(e) {
    var activeTab = document.querySelector('.tabs .is-active a');
    if (!activeTab || !activeTab.matches(':focus')) return;

    if (e.key === 'ArrowRight') {
        e.preventDefault();
        var nextLi = activeTab.closest('li').nextElementSibling;
        if (nextLi) {
            nextLi.querySelector('a').click();
            nextLi.querySelector('a').focus();
        }
        // else: last tab → boundary (bump added in Task 9)
    } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        var prevLi = activeTab.closest('li').previousElementSibling;
        if (prevLi) {
            prevLi.querySelector('a').click();
            prevLi.querySelector('a').focus();
        } else {
            // First tab → back to tree
            var tree = document.querySelector('[role="tree"]');
            if (tree) tree.focus();
        }
    } else if (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        var firstInput = document.querySelector('#method-content input, #method-content textarea, #method-content button[data-path]');
        if (firstInput) firstInput.focus();
    } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        // Boundary — bump added in Task 9
    } else if (e.key === 'Escape') {
        e.preventDefault();
        var tree = document.querySelector('[role="tree"]');
        if (tree) tree.focus();
    }
});
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Write failing test — ArrowLeft on first tab returns to tree**

```java
@Test void shouldReturnToTreeOnArrowLeftFromFirstTab() {
    app.focusTree();
    app.pressKey("ArrowRight"); // enter tabs
    app.pressKey("ArrowLeft"); // first tab → back to tree
    then(app.page().evaluate("document.activeElement === document.querySelector('[role=\"tree\"]')")).isEqualTo(true);
}
```

- [ ] **Step 6: Run test to verify it passes** (should already work from step 3)

Expected: PASS

- [ ] **Step 7: Write failing test — Enter/Tab from tree enters current (last selected) tab**

Per the spec: ArrowRight always enters first tab, but Enter/Tab enters the current (last selected) tab.

```java
@Test void shouldEnterCurrentTabOnEnter() {
    app.focusTree();
    app.pressKey("ArrowRight"); // enter tabs, first tab (GET)
    app.pressKey("ArrowRight"); // switch to POST
    app.pressKey("Escape"); // back to tree
    app.pressKey("Enter"); // should re-enter on POST (current tab), not GET
    then(app.page().querySelector(".tabs li.is-active a").textContent()).isEqualTo("POST");
    then(app.page().evaluate("document.activeElement.closest('.tabs') !== null")).isEqualTo(true);
}
```

- [ ] **Step 8: Run test to verify it fails**

Expected: FAIL — Enter currently triggers `htmx.ajax()` via the existing Tree.js handler, reloading the path fragment and resetting to the first tab

- [ ] **Step 9: Implement — Enter/Tab enters current tab without reloading**

In `Tree.java` JS, update the `Enter` handler. Currently it does `htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail')`. This reloads the path fragment, resetting tabs. Instead, if the detail pane already shows the correct path's content, Enter should just focus the current active tab:

```javascript
case 'Enter':
    e.preventDefault();
    var activeTabLink = document.querySelector('.tabs .is-active a');
    if (activeTabLink) {
        activeTabLink.focus();
    } else {
        // No tabs loaded yet — load the path fragment
        var hxEl = current.querySelector('[hx-get]') || current;
        if (hxEl.getAttribute('hx-get')) htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail');
    }
    break;
```

Also update the `selectItem` function in `Tree.java` JS. When a new tree item is selected (via ArrowUp/Down), it should load the path fragment:

```javascript
function selectItem(item) {
    tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) {
        el.removeAttribute('aria-selected');
    });
    item.setAttribute('aria-selected', 'true');
    // Load path fragment when selecting a new item
    var hxEl = item.querySelector('[hx-get]') || item;
    if (hxEl.getAttribute('hx-get')) htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail');
}
```

- [ ] **Step 10: Run test to verify it passes**

Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "add keyboard navigation for method tabs with current-tab tracking"
```

### Task 8: Field navigation and Enter-to-Send

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `APP_JS`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [ ] **Step 1: Write failing test — ArrowDown from tabs enters first field**

In `BrowserTest.GivenMultiMethodApp`:

```java
@Test void shouldNavigateFromTabsToFields() {
    // Navigate to {petId} which has a parameter field
    app.focusTree();
    app.pressKey("ArrowDown"); // select {petId}
    app.waitForDetailContent("Get pet by ID");
    app.pressKey("ArrowRight"); // enter tabs
    app.pressKey("ArrowDown"); // enter fields
    then(app.page().evaluate("document.activeElement.tagName")).isEqualTo("INPUT");
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL

- [ ] **Step 3: Implement field navigation**

Add to `APP_JS`:

```javascript
// Field navigation within method content
document.body.addEventListener('keydown', function(e) {
    var mc = document.getElementById('method-content');
    if (!mc) return;
    var focusables = Array.from(mc.querySelectorAll('input, textarea, button[data-path]'));
    var idx = focusables.indexOf(document.activeElement);
    if (idx < 0) return;

    if (e.key === 'ArrowDown') {
        e.preventDefault();
        if (idx < focusables.length - 1) focusables[idx + 1].focus();
        // else: last field → boundary (bump added in Task 9)
    } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        if (idx > 0) {
            focusables[idx - 1].focus();
        } else {
            // First field → back to tabs
            var activeTab = document.querySelector('.tabs .is-active a');
            if (activeTab) activeTab.focus();
        }
    } else if (e.key === 'Enter' && document.activeElement.tagName !== 'TEXTAREA') {
        e.preventDefault();
        var sendBtn = mc.querySelector('button[data-path]');
        if (sendBtn) sendBtn.click();
    } else if (e.key === 'Escape') {
        e.preventDefault();
        var tree = document.querySelector('[role="tree"]');
        if (tree) tree.focus();
    }
});
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Write test — Enter in field triggers Send**

```java
@Test void shouldTriggerSendOnEnterInField() {
    app.focusTree();
    app.pressKey("ArrowDown"); // {petId}
    app.waitForDetailContent("Get pet by ID");
    app.pressKey("ArrowRight"); // tabs
    app.pressKey("ArrowDown"); // first field
    app.fillInput("petId", "42");
    app.mockEndpoint("GET", "/pets/42", 200, "application/json", "{\"id\":42}");
    app.pressKey("Enter");
    app.waitForDetailContent("42");
    then(app.responseText()).contains("42");
}
```

- [ ] **Step 6: Run test to verify it passes** (should work from step 3)

Expected: PASS

- [ ] **Step 7: Write test — ArrowUp from first field goes back to tabs**

```java
@Test void shouldReturnToTabsOnArrowUpFromFirstField() {
    app.focusTree();
    app.pressKey("ArrowDown"); // {petId}
    app.waitForDetailContent("Get pet by ID");
    app.pressKey("ArrowRight"); // tabs
    app.pressKey("ArrowDown"); // first field
    app.pressKey("ArrowUp"); // back to tabs
    then(app.page().evaluate("document.activeElement.closest('.tabs') !== null")).isEqualTo(true);
}
```

- [ ] **Step 8: Run test to verify it passes** (should work from step 3)

Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "add field keyboard navigation with Enter-to-Send"
```

---

## Chunk 5: Boundary Feedback, Polish, and Demo

### Task 9: Boundary bump animation

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` — `APP_CSS` and `APP_JS`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java` — JS (tree boundary bumps)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [ ] **Step 1: Write failing test — bump class applied at tree boundary**

```java
@Test void shouldBumpOnTreeBoundary() {
    app.focusTree();
    // Already on first item, press ArrowUp → should bump
    app.pressKey("ArrowUp");
    var selected = app.page().querySelector("[aria-selected='true']");
    then(selected.getAttribute("class")).contains("bump");
}
```

- [ ] **Step 2: Run test to verify it fails**

Expected: FAIL

- [ ] **Step 3: Implement boundary bumps**

Add to `APP_CSS`:

```css
@keyframes bump-vertical {
    0%, 100% { transform: translateY(0); }
    25% { transform: translateY(-2px); }
    75% { transform: translateY(2px); }
}
@keyframes bump-horizontal {
    0%, 100% { transform: translateX(0); }
    25% { transform: translateX(-2px); }
    75% { transform: translateX(2px); }
}
.bump-v { animation: bump-vertical 0.2s ease; }
.bump-h { animation: bump-horizontal 0.2s ease; }
```

Add a `bump()` function in `Tree.java` JS (accessible to both tree and APP_JS):

```javascript
function bump(el, dir) {
    var cls = dir === 'h' ? 'bump-h' : 'bump-v';
    el.classList.remove(cls);
    void el.offsetWidth;
    el.classList.add(cls);
    setTimeout(function() { el.classList.remove(cls); }, 250);
}
```

Apply in Tree.java JS at ArrowUp/ArrowDown boundaries (when at first/last visible item and the key does nothing).

Apply in APP_JS tab navigation: ArrowRight on last tab, ArrowUp on tabs.

Apply in APP_JS field navigation: ArrowDown on last field.

Since the bump function needs to be shared between Tree.js and APP_JS, either:
- Define it in Tree.js (loaded first) so APP_JS can call it, or
- Define it as a global function in APP_JS.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Run all tests**

Run: `mvn test -pl core`
Expected: All PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "add boundary bump animation"
```

### Task 10: Review screenshots and final polish

- [ ] **Step 1: Run all tests**

Run: `mvn test -pl core`
Expected: All PASS

- [ ] **Step 2: Review screenshots**

Review generated screenshots in `core/target/screenshots/` using the `frontend-design` plugin. Check:
- Tree renders cleanly with method tag addons (no wrapping, no layout jumps)
- Tab bar looks correct with proper colors
- Method fragment content shows all enriched fields
- Keyboard navigation works visually

- [ ] **Step 3: Fix any design issues found in review**

- [ ] **Step 4: Commit if changes were made**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "screenshot review polish"
```

### Task 11: Update demo app to showcase new features

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerResource.java`

- [ ] **Step 1: Add OpenAPI annotations to demo endpoints**

Add `@Operation(description = "...")`, `@Tag`, `@Deprecated` annotations to demo resource methods so the generated UI showcases the new enriched method fragments. For example:

```java
@GET
@Operation(summary = "List pets", description = "Returns all pets from the system. Supports filtering by status.")
public List<Pet> list(@QueryParam("status") String status) { ... }
```

Mark one endpoint as deprecated to show the badge.

- [ ] **Step 2: Build and verify demo**

```bash
mvn package -pl demo -am
java -jar demo/target/quarkus-app/quarkus-run.jar
```

Open http://localhost:8080/openapi-ui/index.html and verify visually.

- [ ] **Step 3: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerResource.java
git commit -m "add OpenAPI annotations to demo for enriched method fragments"
```

### Task 12: Update documentation

**Files:**
- Modify: `README.md` — update "Generated Output" section

- [ ] **Step 1: Update README**

Update the "Generated Output" tree in `README.md` to show the new `index.html` files per path directory:

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
│       └── GET.html     # Fragment for GET /owners/{id}
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

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "update README with new generated output structure"
```

### Task 13: Squash commits

- [ ] **Step 1: Squash all commits from this plan into one**

Interactive rebase from the commit before the first plan commit. Squash into a single commit with message: `restructure tree to path-based with method tabs`
