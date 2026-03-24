# Response & Request Schema Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current pseudo-JSON response schema preview with proper schema documentation boxes for both request bodies and responses, using property trees with type badges, collapsible sections, status code tabs, and clear visual separation from actual response data.

**Architecture:** The `MethodFragmentGenerator` builds two new dashed-border schema boxes ("Body" and "Response") that contain property trees rendered as HTML. JavaScript in `app.js` handles toggle behavior, status code tab switching, Accept header, and auto-collapse on response. CSS uses Bulma variables for dark mode compatibility.

**Tech Stack:** Java (bulma-java HTML generation), vanilla JS, Bulma CSS variables, highlight.js (existing)

**Design spec:** `docs/superpowers/specs/2026-03-24-response-schema-design.md`

**Skills:** `tdder:tdd`, `tdder:java`, `tdder:maven`, `bulma-java`

---

## Chunk 1: Response Box with Schema Toggle

### Task 1: Create test spec with rich response schema

**Files:**
- Create: `core/src/test/resources/rich-response.yaml`

- [ ] **Step 1: Create test spec**

```yaml
openapi: 3.0.3
info:
  title: Test API
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
            type: integer
      responses:
        '200':
          description: A pet
          content:
            application/json:
              schema:
                type: object
                required: [id, name]
                properties:
                  id:
                    type: integer
                    example: 1
                  name:
                    type: string
                    example: Max
                  status:
                    type: string
                    enum: [available, adopted, pending]
            application/xml:
              schema:
                type: object
                properties:
                  id:
                    type: integer
        '404':
          description: Pet not found
          content:
            application/json:
              schema:
                type: object
                properties:
                  message:
                    type: string
                    example: Pet not found
```

- [ ] **Step 2: Stage the file**

```bash
git add core/src/test/resources/rich-response.yaml
```

### Task 2: Response box — schema toggle renders and collapses

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`

- [ ] **Step 1: Write failing test — Response box with Schema toggle appears**

Add to `BrowserTest.java`:

```java
@Nested class GivenAppWithRichResponse {
    @RegisterExtension static AppFixture app = context.launch("rich-response.yaml");

    @Test void shouldShowResponseBoxWithSchemaToggle() {
        app.expandFirstNode();
        app.clickTreeNode("pets/{petId}/index.html");
        app.waitForDetailContent("Get a pet");

        then(app.hasResponseBox()).isTrue();
        then(app.hasSchemaToggle("response")).isTrue();
    }
}
```

Add to `AppFixture.java`:

```java
boolean hasResponseBox() {
    return page.locator("#detail .response-box").count() > 0;
}

boolean hasSchemaToggle(String boxType) {
    return page.locator("#detail .schema-box[data-box='" + boxType + "'] .schema-toggle").count() > 0;
}
```

- [ ] **Step 2: Run test — verify it fails**

```bash
mvn test -pl core -Dtest='BrowserTest$GivenAppWithRichResponse#shouldShowResponseBoxWithSchemaToggle'
```

Expected: FAIL — `.response-box` not found.

- [ ] **Step 3: Implement Response box in MethodFragmentGenerator**

Replace the current response schema code (lines 126-153) in `MethodFragmentGenerator.java` with a
new `buildResponseBox()` method that generates:

```html
<div class="schema-box" data-box="response">
  <div class="schema-box-header">
    <div class="schema-box-title">
      <span class="schema-box-label">Response</span>
      <!-- Accept select if multiple content types -->
      <span class="schema-accept-label">Accept</span>
      <div class="select is-small"><select data-accept="true">...</select></div>
    </div>
    <button class="schema-toggle">Schema ▾</button>
  </div>
  <div class="schema-box-content">
    <!-- status code tabs -->
    <!-- property tree -->
  </div>
</div>
```

Use dashed border via CSS class `.schema-box`.

- [ ] **Step 4: Add CSS for schema box**

Add to `app.css`:

```css
.schema-box {
    border: 1px dashed var(--bulma-border);
    border-radius: 6px;
    padding: 0.75rem;
    background: var(--bulma-scheme-main);
    margin-bottom: 0.75rem;
}
.schema-box-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.schema-box-title {
    display: flex;
    align-items: center;
    gap: 0.5rem;
}
.schema-box-label {
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--bulma-text-weak);
}
.schema-accept-label {
    font-size: 0.6875rem;
    color: var(--bulma-text-weak);
}
.schema-toggle {
    border: none;
    background: none;
    font-size: 0.6875rem;
    color: var(--bulma-text-weak);
    cursor: pointer;
    padding: 0.25rem;
}
.schema-box-content {
    margin-top: 0.5rem;
}
.schema-box.is-collapsed .schema-box-content {
    display: none;
}
```

- [ ] **Step 5: Add toggle JS in app.js**

```javascript
detail.addEventListener('click', function(e) {
    var toggle = e.target.closest('.schema-toggle');
    if (!toggle) return;
    var box = toggle.closest('.schema-box');
    box.classList.toggle('is-collapsed');
    toggle.textContent = box.classList.contains('is-collapsed') ? 'Schema ▸' : 'Schema ▾';
});
```

- [ ] **Step 6: Run test — verify it passes**

```bash
mvn test -pl core -Dtest='BrowserTest$GivenAppWithRichResponse#shouldShowResponseBoxWithSchemaToggle'
```

- [ ] **Step 7: Run full suite**

```bash
mvn test -pl core
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "add Response box with Schema toggle"
```

### Task 3: Property tree rendering

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Write failing test — property tree has name, type badge, example**

```java
@Test void shouldShowPropertyTreeWithTypeBadges() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");

    then(app.schemaPropertyNames("response")).contains("id", "name", "status");
    then(app.schemaPropertyType("response", "id")).isEqualTo("integer");
    then(app.schemaPropertyExample("response", "name")).contains("Max");
}
```

Add fixture methods:

```java
List<String> schemaPropertyNames(String boxType) {
    return page.locator(".schema-box[data-box='" + boxType + "'] .schema-prop-name")
            .allTextContents();
}

String schemaPropertyType(String boxType, String propName) {
    return page.locator(".schema-box[data-box='" + boxType + "'] .schema-prop[data-prop='"
            + propName + "'] .schema-prop-type").textContent();
}

String schemaPropertyExample(String boxType, String propName) {
    return page.locator(".schema-box[data-box='" + boxType + "'] .schema-prop[data-prop='"
            + propName + "'] .schema-prop-example").textContent();
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement property tree rendering**

Add a `buildPropertyTree(Schema)` method to `MethodFragmentGenerator` that generates for each property:

```html
<div class="schema-prop" data-prop="name">
  <span class="schema-prop-name">name</span>
  <span class="schema-prop-type">string</span>
  <span class="schema-prop-required">required</span>  <!-- if in required list -->
  <span class="schema-prop-example">e.g. "Max"</span>  <!-- if example exists -->
</div>
```

For enum properties, show values as example: `available | adopted | pending`

- [ ] **Step 4: Add CSS for property tree**

```css
.schema-prop {
    display: flex;
    align-items: center;
    gap: 0.375rem;
    font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
    font-size: 0.75rem;
    line-height: 1.8;
}
.schema-prop-name { color: var(--bulma-warning); font-weight: 500; }
.schema-prop-type {
    background: var(--bulma-scheme-main-bis);
    color: var(--bulma-text-weak);
    padding: 1px 6px;
    border-radius: 3px;
    font-size: 0.625rem;
}
.schema-prop-required {
    background: hsl(348, 86%, 93%);
    color: hsl(348, 86%, 43%);
    padding: 1px 5px;
    border-radius: 3px;
    font-size: 0.5625rem;
    font-weight: 600;
}
.schema-prop-example {
    color: var(--bulma-text-weak);
    font-style: italic;
    font-size: 0.6875rem;
}
```

- [ ] **Step 5: Run test — verify pass**
- [ ] **Step 6: Run full suite, review screenshots**
- [ ] **Step 7: Commit**

### Task 4: Status code tabs

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Write failing test — status code tabs render**

```java
@Test void shouldShowStatusCodeTabs() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");

    then(app.statusCodeTabs()).containsExactly("200", "404");
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement status code tabs**

In `MethodFragmentGenerator`, iterate all response status codes and render tabs. Each tab switches
which property tree is shown (via JS that toggles `data-status` panels).

- [ ] **Step 4: Write test — clicking tab switches schema**

```java
@Test void shouldSwitchSchemaOnStatusCodeTabClick() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");

    app.clickStatusCodeTab("404");

    then(app.schemaPropertyNames("response")).contains("message");
    then(app.schemaPropertyNames("response")).doesNotContain("id");
}
```

- [ ] **Step 5: Implement tab switching JS**
- [ ] **Step 6: Run tests — verify pass**
- [ ] **Step 7: Run full suite, review screenshots**
- [ ] **Step 8: Commit**

### Task 5: Move Accept select into Response box

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Write failing test — Accept select is inside Response box**

```java
@Test void shouldShowAcceptSelectInResponseBox() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");

    then(app.hasAcceptSelectInResponseBox()).isTrue();
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Move the Accept select generation into the Response box header**

Remove the separate `field("Response Type")` generation. The Accept select is now part of the
Response box title row, right after the "Response" label with an "Accept" prefix.

- [ ] **Step 4: Update `app.js` selector for Accept header**

Update the Accept header selector from `detail.querySelector('[data-accept] select')` to
`detail.querySelector('.schema-box[data-box="response"] [data-accept] select')`.

- [ ] **Step 5: Update `AppFixture` selectors**

Update `hasResponseTypeSelect()`, `responseTypeOptions()`, `selectResponseType()` to use the
new selectors within `.schema-box[data-box="response"]`.

- [ ] **Step 6: Run all tests — verify pass**
- [ ] **Step 7: Commit**

### Task 6: Auto-collapse schema on response

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [ ] **Step 1: Write failing test**

```java
@Test void shouldAutoCollapseSchemaWhenResponseArrives() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");
    then(app.isSchemaExpanded("response")).isTrue();

    app.mockEndpoint("/pets/1", "application/json", "{\"id\":1}");
    app.fillInput("petId", "1");
    app.clickSend();
    app.waitForResponse();

    then(app.isSchemaExpanded("response")).isFalse();
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement auto-collapse**

In `app.js`, after showing the response, collapse the response schema box:

```javascript
var responseBox = detail.querySelector('.schema-box[data-box="response"]');
if (responseBox && !responseBox.classList.contains('is-collapsed')) {
    responseBox.classList.add('is-collapsed');
    var toggle = responseBox.querySelector('.schema-toggle');
    if (toggle) toggle.textContent = 'Schema ▸';
}
```

- [ ] **Step 4: Run tests — verify pass**
- [ ] **Step 5: Run full suite**
- [ ] **Step 6: Commit**

## Chunk 2: Body Box with Request Schema

### Task 7: Body box — textarea inside, schema toggle

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Write failing test — Body box wraps textarea**

Use existing `request-body.yaml` test spec.

```java
// In existing GivenAppWithRequestBody class:
@Test void shouldShowBodyBoxWithSchemaToggle() {
    app.clickTreeNode("pets/index.html");
    app.waitForDetailContent("Add a pet");

    then(app.hasBodyBox()).isTrue();
    then(app.hasSchemaToggle("body")).isTrue();
}
```

Add fixture:
```java
boolean hasBodyBox() {
    return page.locator("#detail .schema-box[data-box='body']").count() > 0;
}
```

- [ ] **Step 2: Run test — verify fail**
- [ ] **Step 3: Implement Body box**

Wrap the existing textarea in a `.schema-box[data-box="body"]` with title "Body" and Schema toggle.
Move the example select into the box header if it exists.

- [ ] **Step 4: Run test — verify pass**
- [ ] **Step 5: Run full suite**
- [ ] **Step 6: Commit**

### Task 8: Request schema tree beside textarea

**Files:**
- Create: `core/src/test/resources/request-body-schema.yaml` (with request body schema + required fields + examples)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Create test spec with request body schema**
- [ ] **Step 2: Write failing test — schema tree appears beside textarea**

```java
@Test void shouldShowRequestSchemaTreeBesideTextarea() {
    app.clickTreeNode("pets/index.html");
    app.waitForDetailContent("Add a pet");

    then(app.schemaPropertyNames("body")).contains("name", "status");
    then(app.schemaPropertyType("body", "name")).isEqualTo("string");
}
```

- [ ] **Step 3: Run test — verify fail**
- [ ] **Step 4: Implement side-by-side layout**

When Schema toggle is expanded, render textarea (left) and property tree (right) in a flex container
with a subtle border separator:

```css
.schema-box-body {
    display: flex;
    gap: 0.75rem;
}
.schema-box-body > :first-child {
    flex: 1;
    min-width: 0;
}
.schema-box-tree {
    flex: 0 0 auto;
    border-left: 1px solid var(--bulma-border);
    padding-left: 0.75rem;
}
```

- [ ] **Step 5: Run tests — verify pass**
- [ ] **Step 6: Run full suite, review screenshots (light + dark)**
- [ ] **Step 7: Commit**

## Chunk 3: Single-type endpoints & cleanup

### Task 9: No Response box for endpoints without documented responses

- [ ] **Step 1: Write test — existing one-get.yaml has no Response box**

```java
@Test void shouldNotShowResponseBoxWithoutDocumentedResponses() {
    app.focusTree();
    app.pressKey("Enter");
    app.waitForDetailContent("List pets");

    then(app.hasResponseBox()).isFalse();
}
```

- [ ] **Step 2: Verify it passes (or fix generation logic)**
- [ ] **Step 3: Commit**

### Task 10: Remove old response schema preview

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`

- [ ] **Step 1: Remove the old `pre > code` schema preview code** (lines that generate `fragment.content(element("pre").content(code(...)))`)
- [ ] **Step 2: Run full suite — verify nothing breaks**
- [ ] **Step 3: Commit**

### Task 11: Demo app & final verification

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java` (add richer response annotations)

- [ ] **Step 1: Enrich demo endpoint annotations** with `@APIResponse` for 404, examples, etc.
- [ ] **Step 2: Build demo, verify generated UI**
- [ ] **Step 3: Review screenshots in both light and dark mode**
- [ ] **Step 4: Squash related commits**
- [ ] **Step 5: Final commit**

```bash
git commit -m "add request and response schema documentation boxes"
```
