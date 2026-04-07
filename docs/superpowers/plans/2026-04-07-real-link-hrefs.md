# Real `<a href>` Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace custom JS/data-attribute link mechanisms with real `<a href>` links and query-parameter-based field pre-filling.

**Architecture:** Extend hash navigation to parse query parameters. Generate schema links server-side as `<a href>`. Replace body-link regex wrapping with structured JSON traversal that produces `<a href>` tags. Delete all custom click handlers and `_pendingParamFill` machinery.

**Tech Stack:** Java (bulma-java HTML generation), JavaScript (app.js), Playwright browser tests.

**Spec:** `docs/superpowers/specs/2026-04-07-real-link-hrefs-design.md`

---

### Task 1: Hash query parameter parsing and field filling

Extend `navigateFromHash()` to parse query parameters from the hash and fill form fields.
This is the foundation — body links and schema links will generate hrefs that rely on this.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` (navigateFromHash at lines 1154-1203, afterSwap at lines 419-430)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [x] **Step 1: Write failing test — navigate to hash with query params fills field**

In `BrowserTest.java`, in the `GivenAppWithResponseLinks` nested class (around line 2240), add a test:

```java
@Test void shouldFillFieldFromHashQueryParameter() {
    app.navigateToHash("pets/{petId}/GET?petId=42");
    app.waitForDetailContent("Get a pet");

    then(app.inputValue("petId")).isEqualTo("42");
}
```

In `AppFixture.java`, add:

```java
void navigateToHash(String hash) {
    page.evaluate("location.hash = '#" + hash + "'");
    // trigger popstate since programmatic hash change doesn't fire it
    page.evaluate("window.dispatchEvent(new PopStateEvent('popstate'))");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldFillFieldFromHashQueryParameter" -DdangerouslyDisableSandbox=true`
Expected: FAIL — query params are currently ignored, field stays empty.

- [x] **Step 3: Implement query parameter parsing in navigateFromHash()**

In `app.js`, modify `navigateFromHash()` (line 1154). At the start, split the route on `?`:

```javascript
function navigateFromHash() {
    let fullRoute = decodeURIComponent(location.hash.replace(/^#/, ''));
    if (!fullRoute) return false;

    // Parse query parameters from hash
    var queryParams = {};
    var qIndex = fullRoute.indexOf('?');
    if (qIndex >= 0) {
        var queryString = fullRoute.substring(qIndex + 1);
        fullRoute = fullRoute.substring(0, qIndex);
        queryString.split('&').forEach(function(pair) {
            var parts = pair.split('=');
            if (parts.length === 2) {
                queryParams[decodeURIComponent(parts[0])] = decodeURIComponent(parts[1]);
            }
        });
    }

    let route = fullRoute;
    // ... rest of existing logic unchanged ...
```

Store `queryParams` in a global so the afterSwap handler can apply them. Replace
`window._pendingParamFill` with `window._pendingParams`:

```javascript
    // At the end of navigateFromHash(), before return:
    if (Object.keys(queryParams).length > 0) {
        window._pendingParams = queryParams;
    }
```

In the `htmx:afterSwap` handler, replace the `_pendingParamFill` block (lines 419-430) with:

```javascript
if (window._pendingParams) {
    var params = window._pendingParams;
    window._pendingParams = null;
    var targetForm = document.querySelector('#detail form[data-path]');
    if (targetForm) {
        for (var name in params) {
            var input = targetForm.querySelector('[name="' + name + '"]');
            if (input) {
                input.value = params[name];
                input.dispatchEvent(new Event('input', {bubbles: true}));
            }
        }
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldFillFieldFromHashQueryParameter" -DdangerouslyDisableSandbox=true`
Expected: PASS

- [x] **Step 5: Write test — unknown query params are silently ignored**

```java
@Test void shouldIgnoreUnknownHashQueryParameters() {
    app.navigateToHash("pets/{petId}/GET?petId=42&unknownParam=ignored");
    app.waitForDetailContent("Get a pet");

    then(app.inputValue("petId")).isEqualTo("42");
}
```

Note: this test uses the existing `response-links.yaml` fixture which only has one param (`petId`) on the getPet operation. Unknown params are silently ignored — the test validates that multi-param parsing works and known params are filled.

- [x] **Step 6: Run test to verify it passes (should already pass)**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldIgnoreUnknownHashQueryParameters" -DdangerouslyDisableSandbox=true`
Expected: PASS (the parsing already supports multiple params from Step 3).

- [x] **Step 7: Run full test suite**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true`
Expected: All tests pass.

- [x] **Step 8: Commit**

```bash
git add -A && git commit -m "feat: parse query parameters from hash and fill form fields"
```

---

### Task 2: Schema links as real `<a href>`

Replace `<span data-operation-id>` schema links with `<a href="#path/METHOD">`.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java` (responseLinks at lines 510-537, constructor)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java` (operationFragment/responseFragments calls at lines 306, 310)
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` (delete schema-link click handler at lines 1113-1128)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [x] **Step 1: Write failing test — schema link is a real `<a>` with href**

In `BrowserTest.java`, in `GivenAppWithResponseLinks`, add:

```java
@Test void shouldRenderSchemaLinkNameAsRealLink() {
    navigateToPetDetail();

    then(app.schemaLinkHref("200", 0)).contains("#owners/{ownerId}/GET");
}
```

In `AppFixture.java`, add:

```java
String schemaLinkHref(String statusCode, int index) {
    return page.locator("#detail .schema-status-panel[data-status='" + statusCode + "'] .schema-link-name").nth(index)
            .getAttribute("href");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldRenderSchemaLinkNameAsRealLink" -DdangerouslyDisableSandbox=true`
Expected: FAIL — `getAttribute("href")` returns null because schema links are currently `<span>` elements.

- [x] **Step 3: Pass operationIdMap to OperationFragmentGenerator**

In `OperationFragmentGenerator.java`, add a field and modify the constructor:

```java
class OperationFragmentGenerator {
    private final HttpMethod method;
    private final io.swagger.v3.oas.models.Operation operation;
    private final ApiPath path;
    private final ApiPath displayPath;
    private final Map<String, String[]> operationIdMap;

    private OperationFragmentGenerator(Operation operation, Map<String, String[]> operationIdMap) {
        this.method = operation.method();
        this.operation = operation.spec();
        this.path = operation.path();
        this.displayPath = path.withResolvedParams(this.operation);
        this.operationIdMap = operationIdMap;
    }

    static Element operationFragment(Operation operation, Map<String, String[]> operationIdMap) {
        return new OperationFragmentGenerator(operation, operationIdMap).fragment();
    }

    static Map<String, String> responseFragments(Operation operation, Map<String, String[]> operationIdMap) {
        return new OperationFragmentGenerator(operation, operationIdMap).responseFragmentFiles();
    }
```

In `OpenApiUiGenerator.java`, update the calls at lines 306 and 310:

```java
var fragment = operationFragment(operation, operationIdMap);
// ...
for (var responseEntry : responseFragments(operation, operationIdMap).entrySet()) {
```

The `operationIdMap` variable is already in scope (line 74) — it just needs to be passed to `writeFragments` and then to each call. Trace the call chain: `generate()` → `writeOutput()` → `writeFragments()`. Add the parameter at each level.

- [x] **Step 4: Generate schema links as `<a href>` instead of `<span>`**

In `OperationFragmentGenerator.java`, in `responseLinks()` (line 510), replace the schema link name and operation spans with `<a>` elements. Use the `operationIdMap` to resolve the href:

```java
private Element responseLinks(ApiResponse response) {
    var linksSection = div().classes("schema-response-links");
    linksSection.content(span("Links").classes("schema-links-label"));
    var linksGrid = div().classes("schema-links");
    for (var entry : response.getLinks().entrySet()) {
        var linkName = entry.getKey();
        var link = entry.getValue();
        var href = operationIdHref(link.getOperationId());
        var nameEl = href != null
                ? element("a").attr("href", href).classes("schema-link-name").content(linkName)
                : span(linkName).classes("schema-link-name");
        linksGrid.content(nameEl);
        var details = span().classes("schema-link-details");
        if (link.getOperationId() != null) {
            var opEl = href != null
                    ? element("a").attr("href", href).classes("schema-link-operation").content(link.getOperationId())
                    : span(link.getOperationId()).classes("schema-link-operation");
            details.content(opEl);
        }
        if (link.getDescription() != null) {
            details.content(span(link.getDescription()).classes("schema-link-desc"));
        }
        if (link.getParameters() != null && !link.getParameters().isEmpty()) {
            for (var param : link.getParameters().entrySet()) {
                details.content(span(param.getKey() + " ← " + param.getValue()).classes("schema-link-param"));
            }
        }
        linksGrid.content(details);
    }
    linksSection.content(linksGrid);
    return linksSection;
}

private String operationIdHref(String operationId) {
    if (operationId == null || operationIdMap == null) return null;
    var target = operationIdMap.get(operationId);
    if (target == null) return null;
    return "#" + target[0] + "/" + target[1];
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldRenderSchemaLinkNameAsRealLink" -DdangerouslyDisableSandbox=true`
Expected: PASS

- [x] **Step 6: Delete the schema-link click handler in app.js**

Delete lines 1113-1128 (the `document.addEventListener('click', ...)` handler for `.schema-link-name` and `.schema-link-operation`).

- [x] **Step 7: Write failing test — clicking schema link navigates (via real href)**

The existing tests `shouldNavigateToTargetOperationWhenClickingSchemaLinkName` and `shouldNavigateToTargetOperationWhenClickingSchemaLinkOperation` should still pass since they click the element and check the result. Run them:

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldNavigateToTargetOperationWhenClickingSchemaLinkName+shouldNavigateToTargetOperationWhenClickingSchemaLinkOperation" -DdangerouslyDisableSandbox=true`
Expected: PASS — real `<a href>` links navigate natively.

- [x] **Step 8: Update test for data-operation-id removal**

The test `shouldHaveOperationIdOnSchemaLinkNames` checks for `data-operation-id` attribute. Schema links no longer have this attribute — they have `href` instead. Update the test:

```java
@Test void shouldHaveHrefOnSchemaLinkNames() {
    navigateToPetDetail();

    then(app.schemaLinkHref("200", 0)).isNotNull();
    then(app.schemaLinkHref("200", 0)).startsWith("#");
}
```

Update `AppFixture.responseLinkNameAttribute` if needed, or remove it if only used by the old test.

- [x] **Step 9: Keep shouldEmbedResponseLinksDataOnForm test**

The `data-response-links` attribute is still used by body link JS code (Task 3). Test kept.

- [x] **Step 10: Run full test suite**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true`
Expected: All tests pass.

- [x] **Step 11: Commit**

```bash
git add -A && git commit -m "feat: render schema links as real <a href> elements"
```

---

### Task 3: Body links as real `<a href>`

Replace the regex-based `applyBodyLinks()` with structured JSON traversal that produces real `<a href>` tags.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` (applyBodyLinks at lines 785-853, body-link click handler at lines 1131-1149, the call at line 761)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java` (delete responseLinksData at lines 112-145)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`
- Modify: `core/src/test/resources/response-links.yaml` (if adding nested data for testing)

- [x] **Step 1: Write failing test — body link with nested JSON has correct href**

This is the test the agent should have written — verifying correct link targets with duplicate keys. Add a new test in `GivenAppWithResponseBodyLinks`:

```java
@Test void shouldLinkCorrectFieldWhenKeysAreDuplicated() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");
    app.fillInput("petId", "42");
    app.mockEndpoint("/pets/42", "application/json",
            "{\"id\":42,\"name\":\"Buddy\",\"ownerId\":7,\"owner\":{\"id\":7,\"name\":\"Alice\"}}");
    app.clickSend();
    app.waitForResponse();

    // The pet's "id" (42) should link to getVisits (petId=42)
    then(app.bodyLinkHref(0)).contains("petId=42");
    // The "ownerId" (7) should link to getOwner (ownerId=7)
    then(app.bodyLinkHref(1)).contains("ownerId=7");
}
```

In `AppFixture.java`, add:

```java
String bodyLinkHref(int index) {
    return page.locator("#detail pre.response .body-link").nth(index).getAttribute("href");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks#shouldLinkCorrectFieldWhenKeysAreDuplicated" -DdangerouslyDisableSandbox=true`
Expected: FAIL — current body links use `href="#"` (no real href), and with duplicate keys the links are misassigned.

- [x] **Step 3: Replace applyBodyLinks() with structured JSON traversal**

Delete the entire `applyBodyLinks()` function (lines 785-853). Replace it with a new function that:

1. Parses the response body JSON.
2. Walks the link metadata to find values at each JSON pointer path.
3. Renders the JSON as formatted text, wrapping matching values in `<a>` tags with computed hrefs.
4. Applies syntax highlighting after wrapping (or wraps after highlighting, using the JSON structure to locate values).

The implementation approach: render the JSON to pretty-printed text, then apply hljs. Then for each link parameter, find the value in the highlighted HTML — but now using the full JSON path context to disambiguate duplicate keys.

A simpler alternative: skip hljs for the response body when links exist, and render the JSON manually with `<a>` tags and CSS classes for syntax coloring. This avoids fighting hljs entirely.

The recommended approach: render JSON to highlighted HTML using hljs first, then walk the original parsed JSON tree in parallel with the HTML DOM to locate the correct value nodes. Since we have the parsed JSON structure, we can count key occurrences to find the right one.

Concrete implementation — replace `applyBodyLinks` with:

```javascript
function applyBodyLinks(pre, form, body, statusCode) {
    var linksAttr = form.getAttribute('data-response-links');
    if (!linksAttr) return;
    var allLinks;
    try { allLinks = JSON.parse(linksAttr); } catch(e) { return; }
    var statusLinks = allLinks[statusCode];
    if (!statusLinks) return;

    var parsed;
    try { parsed = JSON.parse(body); } catch(e) { return; }

    // Build a map from JSON pointer path → {operationId, paramName, value}
    var pointerMap = {};  // pointer → [{operationId, paramName}]
    for (var linkName in statusLinks) {
        var link = statusLinks[linkName];
        if (!link.parameters) continue;
        for (var paramName in link.parameters) {
            var expr = link.parameters[paramName];
            var match = expr.match(/^\$response\.body#\/(.+)$/);
            if (!match) continue;
            var pointer = match[1];
            var parts = pointer.split('/');
            var val = parsed;
            for (var i = 0; i < parts.length; i++) {
                if (val === undefined || val === null) break;
                val = val[parts[i]];
            }
            if (val === undefined || val === null) continue;
            if (!pointerMap[pointer]) pointerMap[pointer] = [];
            pointerMap[pointer].push({
                operationId: link.operationId,
                paramName: paramName,
                value: String(val)
            });
        }
    }
    if (Object.keys(pointerMap).length === 0) return;

    // Render JSON manually with links
    var html = renderJsonWithLinks(parsed, pointerMap, []);
    var codeEl = pre.querySelector('code') || pre;
    codeEl.innerHTML = html;
}

function renderJsonWithLinks(value, pointerMap, path) {
    if (value === null) return '<span class="hljs-literal">null</span>';
    if (typeof value === 'boolean') return '<span class="hljs-literal">' + value + '</span>';
    if (typeof value === 'number') {
        var pointer = path.join('/');
        var entries = pointerMap[pointer];
        if (entries && entries.length > 0) {
            return renderLinkedValue(String(value), entries, 'hljs-number');
        }
        return '<span class="hljs-number">' + value + '</span>';
    }
    if (typeof value === 'string') {
        var pointer = path.join('/');
        var entries = pointerMap[pointer];
        var escaped = escapeHtml(value);
        if (entries && entries.length > 0) {
            return '<span class="hljs-string">"' + renderLinkedValue(escaped, entries, '') + '"</span>';
        }
        return '<span class="hljs-string">"' + escaped + '"</span>';
    }
    if (Array.isArray(value)) {
        if (value.length === 0) return '<span class="hljs-punctuation">[]</span>';
        var items = value.map(function(item, i) {
            return renderJsonWithLinks(item, pointerMap, path.concat(String(i)));
        });
        return '<span class="hljs-punctuation">[</span>\n'
            + items.map(function(item) { return indent(item, path.length + 1); }).join('<span class="hljs-punctuation">,</span>\n')
            + '\n' + indentStr(path.length) + '<span class="hljs-punctuation">]</span>';
    }
    if (typeof value === 'object') {
        var keys = Object.keys(value);
        if (keys.length === 0) return '<span class="hljs-punctuation">{}</span>';
        var pairs = keys.map(function(key) {
            var childPath = path.concat(key);
            var keyHtml = '<span class="hljs-attr">"' + escapeHtml(key) + '"</span>';
            var valHtml = renderJsonWithLinks(value[key], pointerMap, childPath);
            return indent(keyHtml + '<span class="hljs-punctuation">:</span> ' + valHtml, path.length + 1);
        });
        return '<span class="hljs-punctuation">{</span>\n'
            + pairs.join('<span class="hljs-punctuation">,</span>\n')
            + '\n' + indentStr(path.length) + '<span class="hljs-punctuation">}</span>';
    }
    return String(value);
}

function renderLinkedValue(text, entries, cssClass) {
    // Use the first matching link entry for the href
    var entry = entries[0];
    var target = window._operationIdMap ? window._operationIdMap[entry.operationId] : null;
    if (!target) {
        return cssClass ? '<span class="' + cssClass + '">' + text + '</span>' : text;
    }
    var href = '#' + target[0] + '/' + target[1] + '?' + encodeURIComponent(entry.paramName) + '=' + encodeURIComponent(entry.value);
    var inner = cssClass ? '<span class="' + cssClass + '">' + text + '</span>' : text;
    return '<a class="body-link" href="' + href + '">' + inner + '</a>';
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function indentStr(level) {
    var s = '';
    for (var i = 0; i < level; i++) s += '  ';
    return s;
}

function indent(html, level) {
    return indentStr(level) + html;
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks#shouldLinkCorrectFieldWhenKeysAreDuplicated" -DdangerouslyDisableSandbox=true`
Expected: PASS

- [x] **Step 5: Update existing body-link tests for real hrefs**

Update `shouldWrapMatchingJsonValuesAsBodyLinks` — body links now have real `href` attributes:

```java
@Test void shouldWrapMatchingJsonValuesAsBodyLinks() {
    navigateToPetDetailAndSend();

    then(app.bodyLinkCount()).isEqualTo(2);
    then(app.bodyLinkText(0)).isEqualTo("42"); // id → getVisits
    then(app.bodyLinkText(1)).isEqualTo("7");  // ownerId → getOwner
    then(app.bodyLinkHref(0)).contains("petId=42");
    then(app.bodyLinkHref(1)).contains("ownerId=7");
}
```

Update `shouldNavigateToTargetOperationWhenClickingBodyLink` — navigation now works via real link click, no custom handler needed:

```java
@Test void shouldNavigateToTargetOperationWhenClickingBodyLink() {
    navigateToPetDetailAndSend();
    app.clickBodyLink(1); // ownerId=7 → getOwner

    app.waitForDetailContent("Get an owner");
}
```

Update `shouldFillParameterFieldsWhenClickingBodyLink` — should still work since hash query params fill fields:

```java
@Test void shouldFillParameterFieldsWhenClickingBodyLink() {
    navigateToPetDetailAndSend();
    app.clickBodyLink(1); // ownerId=7 → getOwner
    app.waitForDetailContent("Get an owner");

    then(app.inputValue("ownerId")).isEqualTo("7");
}
```

- [x] **Step 6: Run updated body-link tests**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks" -DdangerouslyDisableSandbox=true`
Expected: All pass.

- [x] **Step 7: Delete body-link click handler from app.js**

Delete lines 1131-1149 (the `document.addEventListener('click', ...)` for `.body-link[data-operation-id]`).

- [x] **Step 8: Run body-link tests again after click handler removal**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks" -DdangerouslyDisableSandbox=true`
Expected: All pass — navigation now works via real `<a href>` natively.

- [x] **Step 9: Run full test suite**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true`
Expected: All tests pass.

- [x] **Step 10: Commit**

```bash
git add -A && git commit -m "feat: replace body links with real <a href> using structured JSON traversal"
```

---

### Task 4: Clean up legacy code

Remove `_pendingParamFill` references and unused test fixtures. Note: `data-response-links`
and `responseLinksData()` stay — the body link JS still reads link metadata from that attribute.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` (remove any remaining `_pendingParamFill` references)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java` (remove unused methods)

- [x] **Step 1: Search for remaining `_pendingParamFill` references**

Grep app.js for `_pendingParamFill`. All references should already be gone (replaced by
`_pendingParams` in Task 1, click handler deleted in Task 3). Verify and delete any stragglers.

- [x] **Step 2: Clean up unused AppFixture methods**

Check which AppFixture methods are no longer referenced by any test:
- `responseLinkNameAttribute()` — if only used by the deleted `shouldHaveOperationIdOnSchemaLinkNames` test
- `operationFormAttribute()` — if only used by the deleted `shouldEmbedResponseLinksDataOnForm` test

Delete any that are unused.

- [x] **Step 3: Run full test suite**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true`
Expected: All tests pass.

- [x] **Step 4: Commit**

```bash
git add -A && git commit -m "refactor: remove legacy link code and unused fixtures"
```

---

### Task 5: Demo app verification

Verify the demo app exercises the new link mechanism with its nested response data.

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` (add demo-realistic test if not already covered)

- [x] **Step 1: Write failing test — nested demo-like data with correct link targets**

If not already covered by Task 3 Step 1, add a test using a response shape matching the demo app (pet with nested owner and visits array):

```java
@Test void shouldHandleNestedJsonWithMultipleIdFields() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");
    app.fillInput("petId", "1");
    app.mockEndpoint("/pets/1", "application/json",
            "{\"id\":1,\"name\":\"Max\",\"ownerId\":3,\"owner\":{\"id\":3,\"name\":\"Alice\"}}");
    app.clickSend();
    app.waitForResponse();

    // pet.id (1) → getVisits with petId=1
    then(app.bodyLinkHref(0)).contains("visits");
    then(app.bodyLinkHref(0)).contains("petId=1");
    // pet.ownerId (3) → getOwner with ownerId=3
    then(app.bodyLinkHref(1)).contains("owners");
    then(app.bodyLinkHref(1)).contains("ownerId=3");
    // owner.id should NOT have a body link (no link points to owner/id)
    then(app.bodyLinkCount()).isEqualTo(2);
}
```

- [x] **Step 2: Run test**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks#shouldHandleNestedJsonWithMultipleIdFields" -DdangerouslyDisableSandbox=true`
Expected: PASS (if Task 3 implementation is correct).

- [ ] **Step 3: Run the demo app and manually verify**

Run: `mvn quarkus:dev -pl demo`
Navigate to a pet, send a request, verify body links point to the correct operations.

- [x] **Step 4: Run full test suite and take screenshots**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true`
Review screenshots in `core/target/screenshots/` — verify body links and schema links look correct.

- [x] **Step 5: Commit (if any fixes were needed)**

```bash
git add -A && git commit -m "test: add nested JSON body link tests matching demo app data"
```

---

### Task 6: Squash and finalize

- [ ] **Step 1: Squash commits into one**

```bash
git rebase -i HEAD~N  # where N is the number of commits from this plan
```

Squash into: `feat: replace link click handlers with real <a href> and query params`

- [ ] **Step 2: Run full test suite one final time**

Run: `mvn test -DdangerouslyDisableSandbox=true`
Expected: All tests pass.

- [ ] **Step 3: Review screenshots**

Check `core/target/screenshots/` for `response-links` and `response-body-links` screenshots.
