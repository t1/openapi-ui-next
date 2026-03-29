# Request Headers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add support for spec-defined header parameters, per-operation custom headers, and global custom headers — all with optional localStorage persistence.

**Architecture:** Three layers of headers merge at request time (global < spec-defined < per-op custom). Server-side Java generators produce HTML skeletons; client-side `app.js` handles persistence, dynamic rows, and header injection into fetch/curl/httpie.

**Tech Stack:** Java (bulma-java HTML generation), JavaScript (vanilla, no framework), CSS (Bulma + custom), OpenAPI 3.x (swagger-parser)

---

### Task 1: Add `data-param-in` attribute to parameter inputs

Tag every parameter input/select/checkbox with its OpenAPI `in` value so `app.js` can distinguish header params from query params.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java:84-111`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java`
- Create: `core/src/test/resources/header-params.yaml`

- [x] **Step 1: Create test spec with header parameters**

Create `core/src/test/resources/header-params.yaml`:

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
      parameters:
        - name: limit
          in: query
          schema:
            type: integer
          description: Max items to return
        - name: X-Request-ID
          in: header
          required: true
          schema:
            type: string
          description: Unique request correlation identifier
        - name: X-Api-Version
          in: header
          schema:
            type: string
            enum: [2024-01, 2024-06]
          description: API version to use
      responses:
        '200':
          description: A list of pets
```

- [x] **Step 2: Write failing test for data-param-in attribute**

Add test in `OpenApiUiGeneratorTest.java`:

```java
@Test void shouldRenderDataParamInAttribute() throws Exception {
    generate("/header-params.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    then(fragment).contains("data-param-in=\"query\"");
    then(fragment).contains("data-param-in=\"header\"");
}
```

- [x] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderDataParamInAttribute'`
Expected: FAIL — no `data-param-in` attribute rendered yet.

- [x] **Step 4: Add data-param-in attribute to parameter rendering**

In `MethodFragmentGenerator.java`, inside the parameter loop (around line 92), add `data-param-in` to each input element. The attribute must go on the actual input/select/checkbox element, not the field wrapper.

Change the parameter rendering block (lines 85-111) so each input variant gets the attribute:

```java
if (operation.getParameters() != null) {
    for (var param : operation.getParameters()) {
        var schema = param.getSchema();
        var enumValues = (schema != null) ? schema.getEnum() : null;
        var required = Boolean.TRUE.equals(param.getRequired());
        var badges = tagsAddon().content(tag(param.getIn())).classes("is-inline-flex", "ml-2");
        if (required) badges.content(tag("required").is(DANGER));
        var inputField = field().label(span(param.getName()), badges);
        if (enumValues != null && !enumValues.isEmpty()) {
            var sel = select(param.getName()).option("", "(any)");
            sel.attr("data-param-in", param.getIn());
            if (required) sel.attr("required", "");
            for (var value : enumValues) {
                sel.option(value.toString(), value.toString());
            }
            inputField.content(sel);
        } else if ("boolean".equals(schema != null ? schema.getType() : null)) {
            inputField.content(checkbox().name(param.getName()).attr("data-param-in", param.getIn()));
        } else {
            var inp = input(TEXT).attr("name", param.getName());
            inp.attr("data-param-in", param.getIn());
            if (required) inp.attr("required", "");
            inputField.content(inp);
        }
        if (param.getDescription() != null) {
            inputField.help(param.getDescription());
        }
        sendForm.content(inputField);
    }
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderDataParamInAttribute'`
Expected: PASS

- [x] **Step 6: Write test for header parameter badge**

```java
@Test void shouldRenderHeaderParameterBadge() throws Exception {
    generate("/header-params.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    then(fragment).contains(">header<");
}
```

- [x] **Step 7: Run test to verify it passes** (badge already works)

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderHeaderParameterBadge'`
Expected: PASS — the badge rendering uses `param.getIn()` which already works for header params.

- [x] **Step 8: Write test for header enum as select**

```java
@Test void shouldRenderHeaderEnumParameterAsSelect() throws Exception {
    generate("/header-params.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    then(fragment).contains("name=\"X-Api-Version\"");
    then(fragment).contains("data-param-in=\"header\"");
    then(fragment).contains("2024-01");
    then(fragment).contains("2024-06");
}
```

- [x] **Step 9: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderHeaderEnumParameterAsSelect'`
Expected: PASS

- [x] **Step 10: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 11: Commit**

```bash
git add core/src/test/resources/header-params.yaml \
       core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java \
       core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java
git commit -m "add data-param-in attribute to parameter inputs"
```

---

### Task 2: Send header params as HTTP headers in app.js

Modify the form submission handler so that inputs with `data-param-in="header"` are sent as HTTP headers instead of query parameters.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:478-510`

- [x] **Step 1: Write a BrowserTest for header param submission**

This test needs a spec with a header param and a backend that echoes headers. Since the existing `AppFixture` launches a static file server, we need to verify the generated curl/httpie commands include the header — this is testable without a live backend.

Add to `BrowserTest.java`, in a new nested class:

```java
@ResourceLock("header-params") @Nested class GivenAppWithHeaderParams {
    @RegisterExtension static AppFixture app = launch("header-params.yaml");

    @Test void shouldIncludeHeaderParamInCurlCommand() {
        app.switchMode("curl");
        app.fillInput("X-Request-ID", "test-123");
        app.clickSend();

        var clipboard = app.getClipboardText();
        then(clipboard).contains("-H 'X-Request-ID: test-123'");
    }
}
```

Check `AppFixture` for the `switchMode`, `fillInput`, `clickSend`, and `getClipboardText` methods. If they don't exist, implement them as thin Playwright wrappers.

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldIncludeHeaderParamInCurlCommand'`
Expected: FAIL — header params are currently appended as query params.

- [x] **Step 3: Modify app.js to separate header params from query params**

In `app.js`, replace the input collection block (lines 478-494) with logic that checks `data-param-in`:

```javascript
// Collect input values
var inputs = sendForm.querySelectorAll('input[name], select[name]');
var resolvedPath = pathTemplate;
var queryParams = [];
var requestHeaders = {};
inputs.forEach(function(inp) {
    var name = inp.getAttribute('name');
    var paramIn = inp.getAttribute('data-param-in') || 'query';
    var isCheckbox = inp.type === 'checkbox';
    var val = isCheckbox ? (inp.checked ? 'true' : '') : inp.value;
    if (paramIn === 'path' || pathTemplate.includes('{' + name + '}')) {
        resolvedPath = resolvedPath.replace('{' + name + '}', encodeURIComponent(val));
    } else if (paramIn === 'header') {
        if (val) requestHeaders[name] = val;
    } else if (val) {
        queryParams.push(name + '=' + encodeURIComponent(val));
    }
});
```

- [x] **Step 4: Include headers in curl command generation**

Replace the curl block (lines 499-504):

```javascript
if (mode === 'curl') {
    var headerFlags = Object.keys(requestHeaders).map(function(h) {
        return "-H '" + h + ": " + requestHeaders[h] + "'";
    }).join(' ');
    var cmd = 'curl -X ' + method;
    if (headerFlags) cmd += ' ' + headerFlags;
    if (bodyValue) cmd += " -H 'Content-Type: application/json' -d '" + bodyValue + "'";
    cmd += ' ' + url;
    navigator.clipboard.writeText(cmd);
    showCopied(sendBtn);
}
```

- [x] **Step 5: Include headers in httpie command generation**

Replace the httpie block (lines 505-510):

```javascript
else if (mode === 'httpie') {
    var headerArgs = Object.keys(requestHeaders).map(function(h) {
        return h + ':' + requestHeaders[h];
    }).join(' ');
    var cmd = bodyValue
            ? "echo '" + bodyValue + "' | http " + method + ' ' + url + " Content-Type:application/json"
            : 'http ' + method + ' ' + url;
    if (headerArgs) cmd += ' ' + headerArgs;
    navigator.clipboard.writeText(cmd);
    showCopied(sendBtn);
}
```

- [x] **Step 6: Include headers in fetch request**

In the `try` mode block, merge `requestHeaders` into `fetchOptions.headers` (after the existing Content-Type/Accept logic, around line 522):

```javascript
Object.keys(requestHeaders).forEach(function(h) {
    fetchOptions.headers[h] = requestHeaders[h];
});
```

- [x] **Step 7: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldIncludeHeaderParamInCurlCommand'`
Expected: PASS

- [x] **Step 8: Write httpie header test**

```java
@Test void shouldIncludeHeaderParamInHttpieCommand() {
    app.switchMode("httpie");
    app.fillInput("X-Request-ID", "test-456");
    app.clickSend();

    var clipboard = app.getClipboardText();
    then(clipboard).contains("X-Request-ID:test-456");
}
```

- [x] **Step 9: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldIncludeHeaderParamInHttpieCommand'`
Expected: PASS

- [x] **Step 10: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 11: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
       core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "send header params as HTTP headers instead of query params"
```

---

### Task 3: Add per-operation custom header area (server-side skeleton)

Generate an "Add custom header" button and container div below the spec-defined parameters in each method fragment.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java`

- [x] **Step 1: Write failing test**

```java
@Test void shouldRenderCustomHeaderArea() throws Exception {
    generate("/header-params.yaml");

    var fragment = Files.readString(outputDir.resolve("pets/GET.html"));
    then(fragment).contains("custom-headers");
    then(fragment).contains("Add custom header");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderCustomHeaderArea'`
Expected: FAIL

- [x] **Step 3: Add custom header skeleton to MethodFragmentGenerator**

In `MethodFragmentGenerator.java`, after the parameter loop (after line 111) and before the request body block, add:

```java
var customHeaders = div().classes("custom-headers")
        .content(element("button").attr("type", "button").classes("custom-header-add")
                .content("+ Add custom header"));
sendForm.content(customHeaders);
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderCustomHeaderArea'`
Expected: PASS

- [x] **Step 5: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java \
       core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java
git commit -m "add per-operation custom header area skeleton"
```

---

### Task 4: Add custom header row management in app.js

Wire the "Add custom header" button to dynamically add/remove name-value-persist rows.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [x] **Step 1: Write failing BrowserTest**

```java
@Test void shouldAddCustomHeaderRow() {
    app.clickButton("+ Add custom header");

    then(app.customHeaderRowCount()).isEqualTo(1);
}
```

Add helper methods to `AppFixture` as needed (`clickButton`, `customHeaderRowCount`).

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldAddCustomHeaderRow'`
Expected: FAIL

- [x] **Step 3: Add click handler in app.js**

In `app.js`, inside the `detail.addEventListener('click', ...)` block, add handling for the custom header add button:

```javascript
var addBtn = e.target.closest('.custom-header-add');
if (addBtn) {
    var container = addBtn.closest('.custom-headers');
    var row = document.createElement('div');
    row.className = 'custom-header-row';
    row.innerHTML =
        '<input type="text" class="input is-small custom-header-name" placeholder="Header name">' +
        '<input type="text" class="input is-small custom-header-value" placeholder="Value">' +
        '<label class="checkbox is-size-7 custom-header-persist"><input type="checkbox" class="custom-header-persist-check"> persist</label>' +
        '<button type="button" class="delete is-small custom-header-remove"></button>';
    container.insertBefore(row, addBtn);
    row.querySelector('.custom-header-name').focus();
    return;
}
var removeBtn = e.target.closest('.custom-header-remove');
if (removeBtn) {
    var row = removeBtn.closest('.custom-header-row');
    row.remove();
    return;
}
```

- [x] **Step 4: Add CSS for custom header rows**

Append to `app.css`:

```css
/* custom header rows */
.custom-headers {
    margin-bottom: 1rem;
}
.custom-header-row {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    margin-bottom: 0.5rem;
}
.custom-header-name {
    flex: 1;
}
.custom-header-value {
    flex: 2;
}
.custom-header-persist {
    white-space: nowrap;
    font-size: 0.6875rem !important;
    color: var(--bulma-text-weak);
    display: flex;
    align-items: center;
    gap: 0.25rem;
}
.custom-header-add {
    border: 1px dashed var(--bulma-border);
    border-radius: 4px;
    background: none;
    padding: 0.375rem 0.75rem;
    color: var(--bulma-text-weak);
    cursor: pointer;
    width: 100%;
    font-size: 0.8125rem;
}
.custom-header-add:hover {
    border-color: var(--bulma-link);
    color: var(--bulma-link);
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldAddCustomHeaderRow'`
Expected: PASS

- [x] **Step 6: Write test for including custom headers in curl**

```java
@Test void shouldIncludeCustomHeaderInCurlCommand() {
    app.switchMode("curl");
    app.clickButton("+ Add custom header");
    app.fillCustomHeader(0, "X-Debug", "true");
    app.clickSend();

    var clipboard = app.getClipboardText();
    then(clipboard).contains("-H 'X-Debug: true'");
}
```

- [x] **Step 7: Collect custom headers in form submission**

In the form submit handler in `app.js`, after the existing `inputs.forEach` loop, add collection of custom headers:

```javascript
sendForm.querySelectorAll('.custom-header-row').forEach(function(row) {
    var name = row.querySelector('.custom-header-name').value.trim();
    var value = row.querySelector('.custom-header-value').value;
    if (name) requestHeaders[name] = value;
});
```

- [x] **Step 8: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldIncludeCustomHeaderInCurlCommand'`
Expected: PASS

- [x] **Step 9: Write test for removing custom header row**

```java
@Test void shouldRemoveCustomHeaderRow() {
    app.clickButton("+ Add custom header");
    then(app.customHeaderRowCount()).isEqualTo(1);

    app.removeCustomHeader(0);
    then(app.customHeaderRowCount()).isEqualTo(0);
}
```

- [x] **Step 10: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldRemoveCustomHeaderRow'`
Expected: PASS

- [x] **Step 11: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 12: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
       core/src/main/resources/com/github/t1/openapi/ui/generator/app.css \
       core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java \
       core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java
git commit -m "add per-operation custom header row management"
```

---

### Task 5: Add global headers panel (server-side skeleton)

Generate a collapsible "Global Headers" panel in `OpenApiUiGenerator` in the detail header area.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java:115-118`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java`

- [x] **Step 1: Write failing test**

```java
@Test void shouldRenderGlobalHeadersPanel() throws Exception {
    generate("/one-get.yaml");

    var indexHtml = Files.readString(outputDir.resolve("index.html"));
    then(indexHtml).contains("global-headers");
    then(indexHtml).contains("Global Headers");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderGlobalHeadersPanel'`
Expected: FAIL

- [x] **Step 3: Add global headers panel to page layout**

In `OpenApiUiGenerator.java`, in the `buildPageLayout` method, add the global headers panel after the `detailHeader` div (around line 118). Insert before the `splitLayout`:

```java
var globalHeaders = div().id("global-headers").classes("global-headers", "is-collapsed")
        .content(
                element("button").attr("type", "button").classes("global-headers-toggle")
                        .content(span("Global Headers"), span("0").classes("global-headers-count")),
                div().classes("global-headers-body")
                        .content(element("button").attr("type", "button").classes("custom-header-add")
                                .content("+ Add global header"))
        );
```

Then add it to the body layout. Change:
```java
var body = section().content(container().content(
        detailHeader,
        splitLayout
), errorBanner);
```
To:
```java
var body = section().content(container().content(
        detailHeader,
        globalHeaders,
        splitLayout
), errorBanner);
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldRenderGlobalHeadersPanel'`
Expected: PASS

- [x] **Step 5: Add CSS for global headers panel**

Append to `app.css`:

```css
/* global headers panel */
.global-headers {
    margin-bottom: 1rem;
    border: 1px solid var(--bulma-border);
    border-radius: 6px;
}
.global-headers-toggle {
    width: 100%;
    text-align: left;
    padding: 0.5rem 0.75rem;
    background: var(--bulma-scheme-main-bis);
    border: none;
    border-radius: 6px;
    cursor: pointer;
    font-size: 0.8125rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
}
.global-headers-toggle:focus {
    outline: 2px solid var(--bulma-link);
    outline-offset: 2px;
    border-radius: 6px;
}
.global-headers-count {
    background: var(--bulma-scheme-main-ter);
    padding: 0 0.5rem;
    border-radius: 10px;
    font-size: 0.6875rem;
}
.global-headers-body {
    padding: 0.75rem;
    border-top: 1px solid var(--bulma-border);
}
.global-headers.is-collapsed .global-headers-body {
    display: none;
}
/* persist checkbox on spec-defined header params */
.header-persist {
    margin-left: auto;
    font-size: 0.6875rem !important;
    color: var(--bulma-text-weak);
    display: flex;
    align-items: center;
    gap: 0.25rem;
    white-space: nowrap;
}
```

- [x] **Step 6: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 7: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java \
       core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java \
       core/src/main/resources/com/github/t1/openapi/ui/generator/app.css
git commit -m "add global headers panel skeleton"
```

---

### Task 6: Wire global headers panel interactivity in app.js

Add toggle expand/collapse, add/remove rows, count badge update, and include global headers in requests.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [x] **Step 1: Write failing BrowserTest for toggle**

In a new nested class for `one-get.yaml` (or reuse the existing one):

```java
@Test void shouldToggleGlobalHeadersPanel() {
    then(app.isGlobalHeadersCollapsed()).isTrue();
    app.clickGlobalHeadersToggle();
    then(app.isGlobalHeadersCollapsed()).isFalse();
    app.clickGlobalHeadersToggle();
    then(app.isGlobalHeadersCollapsed()).isTrue();
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldToggleGlobalHeadersPanel'`
Expected: FAIL

- [x] **Step 3: Add toggle handler in app.js**

Near the top of the DOMContentLoaded handler (after modeContainer setup), add:

```javascript
// Global headers panel toggle
var globalHeadersPanel = document.getElementById('global-headers');
if (globalHeadersPanel) {
    globalHeadersPanel.querySelector('.global-headers-toggle').addEventListener('click', function() {
        globalHeadersPanel.classList.toggle('is-collapsed');
    });
}
```

Also add click handling for the "Add global header" button and remove button. Reuse the same `.custom-header-add` and `.custom-header-remove` class pattern, but scope it:

```javascript
if (globalHeadersPanel) {
    globalHeadersPanel.addEventListener('click', function(e) {
        var addBtn = e.target.closest('.custom-header-add');
        if (addBtn) {
            var body = globalHeadersPanel.querySelector('.global-headers-body');
            var row = document.createElement('div');
            row.className = 'custom-header-row';
            row.innerHTML =
                '<input type="text" class="input is-small custom-header-name" placeholder="Header name">' +
                '<input type="text" class="input is-small custom-header-value" placeholder="Value">' +
                '<label class="checkbox is-size-7 custom-header-persist"><input type="checkbox" class="custom-header-persist-check"> persist</label>' +
                '<button type="button" class="delete is-small custom-header-remove"></button>';
            body.insertBefore(row, addBtn);
            row.querySelector('.custom-header-name').focus();
            updateGlobalHeaderCount();
            return;
        }
        var removeBtn = e.target.closest('.custom-header-remove');
        if (removeBtn) {
            removeBtn.closest('.custom-header-row').remove();
            updateGlobalHeaderCount();
            return;
        }
    });
}

function updateGlobalHeaderCount() {
    if (!globalHeadersPanel) return;
    var count = globalHeadersPanel.querySelectorAll('.custom-header-row').length;
    globalHeadersPanel.querySelector('.global-headers-count').textContent = count;
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldToggleGlobalHeadersPanel'`
Expected: PASS

- [x] **Step 5: Write test for global headers in curl command**

```java
@Test void shouldIncludeGlobalHeaderInCurlCommand() {
    app.clickGlobalHeadersToggle();
    app.clickButton("+ Add global header");
    app.fillGlobalHeader(0, "Authorization", "Bearer token123");
    app.switchMode("curl");
    app.clickSend();

    var clipboard = app.getClipboardText();
    then(clipboard).contains("-H 'Authorization: Bearer token123'");
}
```

- [x] **Step 6: Collect global headers in form submission**

In the form submit handler, before the existing `inputs.forEach`, collect global headers into a separate object and merge them (lowest priority):

```javascript
// Collect global headers (lowest priority)
var globalHeaders = {};
if (globalHeadersPanel) {
    globalHeadersPanel.querySelectorAll('.custom-header-row').forEach(function(row) {
        var name = row.querySelector('.custom-header-name').value.trim();
        var value = row.querySelector('.custom-header-value').value;
        if (name) globalHeaders[name] = value;
    });
}
```

Then after all three sources are collected, merge with priority:

```javascript
// Merge: global < spec-defined < per-op custom (highest priority wins)
var mergedHeaders = {};
Object.keys(globalHeaders).forEach(function(h) { mergedHeaders[h] = globalHeaders[h]; });
Object.keys(requestHeaders).forEach(function(h) { mergedHeaders[h] = requestHeaders[h]; });
```

Use `mergedHeaders` (instead of `requestHeaders`) when building curl/httpie/fetch commands.

- [x] **Step 7: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldIncludeGlobalHeaderInCurlCommand'`
Expected: PASS

- [x] **Step 8: Write merge priority test**

```java
@Test void perOperationHeaderShouldOverrideGlobalHeader() {
    app.clickGlobalHeadersToggle();
    app.clickButton("+ Add global header");
    app.fillGlobalHeader(0, "X-Test", "global-value");
    // navigate to header-params spec method that has custom header area
    // (this test may belong in GivenAppWithHeaderParams instead)
}
```

Note: The merge priority test is better placed in `GivenAppWithHeaderParams` since it needs the custom header area. Move it there:

```java
@Test void perOperationHeaderShouldOverrideGlobalHeader() {
    app.clickGlobalHeadersToggle();
    app.clickButton("+ Add global header");
    app.fillGlobalHeader(0, "X-Debug", "global");
    app.switchMode("curl");
    app.clickButton("+ Add custom header");
    app.fillCustomHeader(0, "X-Debug", "per-op");
    app.clickSend();

    var clipboard = app.getClipboardText();
    then(clipboard).contains("-H 'X-Debug: per-op'");
    then(clipboard).doesNotContain("global");
}
```

- [x] **Step 9: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#perOperationHeaderShouldOverrideGlobalHeader'`
Expected: PASS

- [x] **Step 10: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 11: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
       core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java \
       core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java
git commit -m "wire global headers panel interactivity and merge into requests"
```

---

### Task 7: Add localStorage persistence for all header types

Implement the persist checkbox logic: checking it saves to localStorage, unchecking removes the entry, page load restores persisted values.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [x] **Step 1: Write failing BrowserTest for global header persistence**

```java
@Test void shouldPersistGlobalHeader() {
    app.clickGlobalHeadersToggle();
    app.clickButton("+ Add global header");
    app.fillGlobalHeader(0, "Authorization", "Bearer secret");
    app.checkGlobalHeaderPersist(0);

    app.navigateHome();

    app.clickGlobalHeadersToggle();
    then(app.globalHeaderName(0)).isEqualTo("Authorization");
    then(app.globalHeaderValue(0)).isEqualTo("Bearer secret");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldPersistGlobalHeader'`
Expected: FAIL

- [x] **Step 3: Add persist logic for global headers**

In `app.js`, add event handlers for the persist checkbox in the global headers panel:

```javascript
if (globalHeadersPanel) {
    globalHeadersPanel.addEventListener('change', function(e) {
        var checkbox = e.target.closest('.custom-header-persist-check');
        if (!checkbox) return;
        var row = checkbox.closest('.custom-header-row');
        var name = row.querySelector('.custom-header-name').value.trim();
        var value = row.querySelector('.custom-header-value').value;
        if (checkbox.checked && name) {
            localStorage.setItem('openapi-ui-global-header:' + name, value);
        } else if (name) {
            localStorage.removeItem('openapi-ui-global-header:' + name);
        }
    });
    // Also update localStorage when name/value changes (if persist is checked)
    globalHeadersPanel.addEventListener('input', function(e) {
        var inp = e.target.closest('.custom-header-name, .custom-header-value');
        if (!inp) return;
        var row = inp.closest('.custom-header-row');
        var checkbox = row.querySelector('.custom-header-persist-check');
        if (!checkbox || !checkbox.checked) return;
        var name = row.querySelector('.custom-header-name').value.trim();
        var value = row.querySelector('.custom-header-value').value;
        if (name) localStorage.setItem('openapi-ui-global-header:' + name, value);
    });
}
```

Add restore-on-load logic (inside the DOMContentLoaded handler, after global headers panel setup):

```javascript
// Restore persisted global headers
if (globalHeadersPanel) {
    for (var i = 0; i < localStorage.length; i++) {
        var key = localStorage.key(i);
        if (!key.startsWith('openapi-ui-global-header:')) continue;
        var name = key.substring('openapi-ui-global-header:'.length);
        var value = localStorage.getItem(key);
        var body = globalHeadersPanel.querySelector('.global-headers-body');
        var addBtn = body.querySelector('.custom-header-add');
        var row = document.createElement('div');
        row.className = 'custom-header-row';
        row.innerHTML =
            '<input type="text" class="input is-small custom-header-name" placeholder="Header name" value="' + name.replace(/"/g, '&quot;') + '">' +
            '<input type="text" class="input is-small custom-header-value" placeholder="Value" value="' + value.replace(/"/g, '&quot;') + '">' +
            '<label class="checkbox is-size-7 custom-header-persist"><input type="checkbox" class="custom-header-persist-check" checked> persist</label>' +
            '<button type="button" class="delete is-small custom-header-remove"></button>';
        body.insertBefore(row, addBtn);
    }
    updateGlobalHeaderCount();
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldPersistGlobalHeader'`
Expected: PASS

- [x] **Step 5: Write test for spec-defined header persistence**

```java
@Test void shouldPersistSpecDefinedHeaderValue() {
    app.fillInput("X-Request-ID", "persist-me");
    app.checkHeaderParamPersist("X-Request-ID");

    app.navigateHome();

    then(app.inputValue("X-Request-ID")).isEqualTo("persist-me");
}
```

- [x] **Step 6: Add persist checkbox to spec-defined header param fields**

In `MethodFragmentGenerator.java`, when `param.getIn()` is `"header"`, add a persist checkbox to the field label row. The existing code creates the field on line 92 with `field().label(span(param.getName()), badges)`. Change this to conditionally include the persist checkbox:

```java
var inputField = "header".equals(param.getIn())
        ? field().label(span(param.getName()), badges,
                element("label").classes("checkbox", "is-size-7", "header-persist")
                        .content(element("input").attr("type", "checkbox").classes("header-persist-check"),
                                span(" persist")))
        : field().label(span(param.getName()), badges);
```

This replaces the existing `var inputField = field().label(span(param.getName()), badges);` on line 92.

- [x] **Step 7: Add persistence logic for spec-defined headers in app.js**

In `app.js`, after HTMX content swaps (`htmx:afterSwap` handler), add persistence restore and event binding for spec-defined header params:

```javascript
// Restore persisted spec-defined header values
document.querySelectorAll('[data-param-in="header"]').forEach(function(inp) {
    var form = inp.closest('form[data-path]');
    if (!form) return;
    var method = form.getAttribute('data-method');
    var path = form.getAttribute('data-path');
    var name = inp.getAttribute('name');
    var key = 'openapi-ui-header:' + method + ':' + path + ':' + name;
    var saved = localStorage.getItem(key);
    if (saved !== null) {
        inp.value = saved;
        var persistCheck = inp.closest('.field').querySelector('.header-persist-check');
        if (persistCheck) persistCheck.checked = true;
    }
});
```

Add change handlers for persist checkboxes on spec-defined header params (delegated from detail):

```javascript
detail.addEventListener('change', function(e) {
    var checkbox = e.target.closest('.header-persist-check');
    if (!checkbox) return;
    var fieldEl = checkbox.closest('.field');
    var inp = fieldEl.querySelector('[data-param-in="header"]');
    if (!inp) return;
    var form = inp.closest('form[data-path]');
    var method = form.getAttribute('data-method');
    var path = form.getAttribute('data-path');
    var name = inp.getAttribute('name');
    var key = 'openapi-ui-header:' + method + ':' + path + ':' + name;
    if (checkbox.checked) {
        localStorage.setItem(key, inp.value);
    } else {
        localStorage.removeItem(key);
    }
});
detail.addEventListener('input', function(e) {
    var inp = e.target.closest('[data-param-in="header"]');
    if (!inp) return;
    var fieldEl = inp.closest('.field');
    var persistCheck = fieldEl.querySelector('.header-persist-check');
    if (!persistCheck || !persistCheck.checked) return;
    var form = inp.closest('form[data-path]');
    var method = form.getAttribute('data-method');
    var path = form.getAttribute('data-path');
    var name = inp.getAttribute('name');
    localStorage.setItem('openapi-ui-header:' + method + ':' + path + ':' + name, inp.value);
});
```

- [x] **Step 8: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldPersistSpecDefinedHeaderValue'`
Expected: PASS

- [x] **Step 9: Add per-op custom header persistence**

Similar pattern: on persist checkbox change for per-op custom headers, save/remove from localStorage with key `openapi-ui-custom-header:<METHOD>:<path>:<name>`. On fragment load, restore from scan.

```javascript
// In the custom header add handler, also bind persist logic:
detail.addEventListener('change', function(e) {
    var checkbox = e.target.closest('.custom-headers .custom-header-persist-check');
    if (!checkbox) return;
    var row = checkbox.closest('.custom-header-row');
    var form = row.closest('form[data-path]');
    var method = form.getAttribute('data-method');
    var path = form.getAttribute('data-path');
    var name = row.querySelector('.custom-header-name').value.trim();
    var value = row.querySelector('.custom-header-value').value;
    var key = 'openapi-ui-custom-header:' + method + ':' + path + ':' + name;
    if (checkbox.checked && name) {
        localStorage.setItem(key, value);
    } else if (name) {
        localStorage.removeItem(key);
    }
});
```

Restore on fragment load (in `htmx:afterSwap`):

```javascript
// Restore persisted per-op custom headers
var form = document.querySelector('#detail form[data-path]');
if (form) {
    var method = form.getAttribute('data-method');
    var path = form.getAttribute('data-path');
    var prefix = 'openapi-ui-custom-header:' + method + ':' + path + ':';
    var container = form.querySelector('.custom-headers');
    if (container) {
        var addBtn = container.querySelector('.custom-header-add');
        for (var i = 0; i < localStorage.length; i++) {
            var key = localStorage.key(i);
            if (!key.startsWith(prefix)) continue;
            var name = key.substring(prefix.length);
            var value = localStorage.getItem(key);
            var row = document.createElement('div');
            row.className = 'custom-header-row';
            row.innerHTML =
                '<input type="text" class="input is-small custom-header-name" placeholder="Header name" value="' + name.replace(/"/g, '&quot;') + '">' +
                '<input type="text" class="input is-small custom-header-value" placeholder="Value" value="' + value.replace(/"/g, '&quot;') + '">' +
                '<label class="checkbox is-size-7 custom-header-persist"><input type="checkbox" class="custom-header-persist-check" checked> persist</label>' +
                '<button type="button" class="delete is-small custom-header-remove"></button>';
            container.insertBefore(row, addBtn);
        }
    }
}
```

- [x] **Step 10: Write test for per-op custom header persistence**

```java
@Test void shouldPersistPerOperationCustomHeader() {
    app.clickButton("+ Add custom header");
    app.fillCustomHeader(0, "X-Debug", "verbose");
    app.checkCustomHeaderPersist(0);

    app.navigateHome();

    then(app.customHeaderRowCount()).isEqualTo(1);
    then(app.customHeaderName(0)).isEqualTo("X-Debug");
    then(app.customHeaderValue(0)).isEqualTo("verbose");
}
```

- [x] **Step 11: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithHeaderParams#shouldPersistPerOperationCustomHeader'`
Expected: PASS

- [x] **Step 12: Write test for unchecking persist removes localStorage entry**

```java
@Test void shouldRemovePersistedHeaderWhenUnchecked() {
    app.clickGlobalHeadersToggle();
    app.clickButton("+ Add global header");
    app.fillGlobalHeader(0, "X-Temp", "val");
    app.checkGlobalHeaderPersist(0);
    app.uncheckGlobalHeaderPersist(0);

    app.navigateHome();

    app.clickGlobalHeadersToggle();
    then(app.globalHeaderRowCount()).isEqualTo(0);
}
```

- [x] **Step 13: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldRemovePersistedHeaderWhenUnchecked'`
Expected: PASS

- [x] **Step 14: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 15: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
       core/src/main/java/com/github/t1/openapi/ui/generator/MethodFragmentGenerator.java \
       core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java \
       core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java
git commit -m "add localStorage persistence for all header types"
```

---

### Task 8: Add header parameter to demo app

Extend the demo petstore API so at least one endpoint has a spec-defined header parameter, exercising the feature end-to-end.

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`

- [x] **Step 1: Add header parameter to GET /pets**

In `PetResource.java`, add a `@HeaderParam` to the `list` method:

```java
@GET @Produces(APPLICATION_JSON)
@Operation(summary = "List all pets", description = "Returns all pets from the system. ...")
public List<Pet> list(
        @QueryParam("status") PetStatus status,
        @HeaderParam("X-Request-ID") @Parameter(description = "Unique request correlation identifier") String requestId) {
    if (status == null) return PETS;
    return PETS.stream().filter(p -> p.status == status).toList();
}
```

Add the necessary import:
```java
import jakarta.ws.rs.HeaderParam;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
```

- [x] **Step 2: Build and verify**

Run: `mvn package -pl demo -am`
Expected: Build succeeds. The generated OpenAPI spec at `demo/target/generated/openapi.yaml` should include the header parameter.

- [x] **Step 3: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java
git commit -m "add X-Request-ID header parameter to demo GET /pets"
```

---

### Task 9: Update documentation

Update `README.md` to document the request headers feature.

**Files:**
- Modify: `README.md`

- [x] **Step 1: Add header features to README**

In the "API Documentation" section of `README.md`, add:
```
- Request header parameters with type badge, required marker, and optional persistence
```

In the "Try It Out" section, add:
```
- Global headers panel: set headers that apply to all requests, with optional localStorage persistence
- Per-operation custom headers: add arbitrary headers per endpoint
- Header merge priority: per-operation > spec-defined > global
```

- [x] **Step 2: Commit**

```bash
git add README.md
git commit -m "document request headers feature"
```

---

### Task 10: Visual review and final validation

Run the full test suite, review screenshots, and verify the feature works end-to-end.

- [x] **Step 1: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests PASS.

- [x] **Step 2: Review screenshots**

Check `core/target/screenshots/` for any screenshots showing header parameters. Use the `frontend-design` plugin to review visual quality — check that header badges, persist checkboxes, custom header rows, and the global headers panel look consistent with the existing UI.

- [x] **Step 3: Squash commits**

Per project conventions, squash all commits from this plan into a single commit:

```bash
git rebase -i <commit-before-first-plan-commit>
```

Squash into: `add request headers support (spec-defined, custom, global with persistence)`
