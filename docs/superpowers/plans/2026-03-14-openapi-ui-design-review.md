# OpenAPI UI Design Review Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix bugs, add interaction feedback, and improve visual design of the generated OpenAPI UI.

**Architecture:** All changes are in `OpenApiUiGenerator.java` — the Java code that builds HTML structure, and the `TREE_KEYBOARD_JS` string constant for client-side behavior. A new `openapi-ui.css` string constant provides custom styling beyond Bulma defaults.

**Tech Stack:** Java 21, bulma-java, Playwright (tests)

**Spec:** `docs/superpowers/specs/2026-03-14-openapi-ui-design-review-design.md`

**Skills:** Invoke `tdder:java` and `tdder:maven` before writing any code.

---

## Chunk 1: Bug Fixes

### Task 1: Fix empty summary em-dash

The tree shows `pets GET —` with a dangling dash when an operation has no summary.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:179-188`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Test resource: `core/src/test/resources/one-get.yaml` (verify it has a summary to confirm existing behavior)

- [x] **Step 1: Read the test spec `one-get.yaml`** to understand current test data

- [x] **Step 2: Write a failing test** in `BrowserTest.GivenAppWithOneGet`

A test that verifies tree items without a summary don't show the em-dash. The existing
`one-get.yaml` spec has a summary ("List pets"), so the tree shows `pets GET — List pets`.
Add a test that checks the tree item text contains "List pets" but does NOT contain a
standalone ` — ` with nothing after it. Actually, the simplest approach: create a new spec
file `no-summary.yaml` that has an operation without a summary, and verify the tree item text
does not contain `—`.

Create `core/src/test/resources/no-summary.yaml`:
```yaml
openapi: "3.1.0"
info:
  title: No Summary API
  version: "1.0"
paths:
  /items:
    get:
      operationId: listItems
      responses:
        "200":
          description: OK
```

Add test in `BrowserTest`:
```java
@Nested class GivenAppWithNoSummary {
    @RegisterExtension static AppFixture app = context.launch("no-summary.yaml");

    @Test void shouldNotShowEmDashWithoutSummary() {
        then(app.selectedTreeItemText()).doesNotContain("—");
    }
}
```

- [x] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithNoSummary'`
Expected: FAIL — tree item text currently contains `—` even when summary is empty.

- [x] **Step 4: Fix the generator**

In `OpenApiUiGenerator.java` line 183-184, change the tree item span to conditionally
include the em-dash and summary:

Current code (line 183-184):
```java
var summary = operation.getSummary() != null ? operation.getSummary() : "";
item.content(span(" " + method + " — " + summary)
```

New code:
```java
var summary = operation.getSummary();
var label = summary != null ? " " + method + " — " + summary : " " + method;
item.content(span(label)
```

- [x] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 6: Commit**

```bash
git add core/src/test/resources/no-summary.yaml
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "fix dangling em-dash when operation has no summary"
```

### Task 2: Fix fetch URL resolution

"Try" mode sends requests to the wrong URL (e.g. `http://pets/1` instead of
`http://localhost:8080/pets/1`) because the base URL `/` is not resolved against the origin.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (the `TREE_KEYBOARD_JS` constant, line ~304)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x] **Step 1: Write a failing test**

The existing `InTryMode` tests already use `withBaseUrlOverride()` which replaces the spec's
absolute URL with the test server URL. That masks the bug. Add a new test that uses a spec
with a relative base URL (`/`) to reproduce the real issue.

Create `core/src/test/resources/relative-base.yaml`:
```yaml
openapi: "3.1.0"
info:
  title: Relative Base API
  version: "1.0"
servers:
  - url: /
paths:
  /items:
    get:
      operationId: listItems
      summary: List items
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: string
```

Add test:
```java
@Nested class GivenAppWithRelativeBase {
    @RegisterExtension static AppFixture app = context.launch("relative-base.yaml");

    @Test void tryModeShouldResolveRelativeBaseUrl() {
        app.mockEndpoint("/items", "application/json", "{\"id\":\"1\"}");
        app.focusTree();
        app.pressKey("Enter");
        app.waitForDetailContent("List items");
        app.clickSend();
        app.waitForResponse();

        then(app.responseText()).contains("\"id\"");
    }
}
```

The base URL is `/` so fetch will go to `http://localhost:<port>/items`. The existing
`mockEndpoint` prefixes `/api`, so add a `mockRootEndpoint` method to `AppFixture` that
registers handlers without the `/api` prefix:

```java
void mockRootEndpoint(String path, String contentType, String body) {
    try {server.removeContext(path);} catch (IllegalArgumentException ignored) {}
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

Update the test to use `app.mockRootEndpoint("/items", "application/json", "{\"id\":\"1\"}")`
instead of `app.mockEndpoint(...)`.

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithRelativeBase'`
Expected: FAIL — fetch goes to wrong URL.

- [x] **Step 3: Fix the JavaScript**

In `TREE_KEYBOARD_JS`, line ~304, change URL construction to resolve relative base URLs:

Current code (line 304):
```javascript
var url = baseUrl + pathTemplate;
```

New code:
```javascript
var url = baseUrl.startsWith('http') ? baseUrl + pathTemplate
        : new URL(baseUrl + pathTemplate, window.location.origin).href;
```

This uses `new URL()` to properly normalize the path (avoiding double slashes when baseUrl
is `/` and pathTemplate is `/items`).

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 5: Commit**

```bash
git add core/src/test/resources/relative-base.yaml
git add core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "fix fetch URL resolution for relative base URLs"
```

### Task 3: Fix fetch error handling

Network errors and non-2xx responses fail silently.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (TREE_KEYBOARD_JS, lines ~321-335)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x] **Step 1: Write a failing test for error responses**

Add test in `GivenAppWithOneGet.InTryMode`:
```java
@Test void tryModeShowsErrorForFailedFetch() {
    app.mockEndpoint("/pets", "text/plain", "not found", 404);
    navigateToListPetsAndSend();

    then(app.responseText()).contains("404");
}
```

This requires adding a `mockEndpoint` overload that accepts a status code. Add to
`AppFixture`:
```java
void mockEndpoint(String path, String contentType, String body, int statusCode) {
    try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
    server.createContext("/api" + path, exchange -> {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        var bytes = body.getBytes();
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    });
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet$InTryMode#tryModeShowsErrorForFailedFetch'`
Expected: FAIL — no response pre element rendered for error responses.

- [x] **Step 3: Fix the JavaScript**

In `TREE_KEYBOARD_JS`, replace the fetch block (lines ~321-335):

Current code:
```javascript
} else if (mode === 'try') {
    fetch(url).then(function(resp) {
        var ct = resp.headers.get('Content-Type') || '';
        return resp.text().then(function(text) {
            var pre = document.createElement('pre');
            if (ct.includes('json')) {
                try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
            }
            pre.textContent = text;
            var existing = detail.querySelector('pre.response');
            if (existing) existing.remove();
            pre.className = 'response';
            detail.appendChild(pre);
        });
    });
}
```

New code:
```javascript
} else if (mode === 'try') {
    fetch(url).then(function(resp) {
        var ct = resp.headers.get('Content-Type') || '';
        return resp.text().then(function(text) {
            var pre = document.createElement('pre');
            if (!resp.ok) {
                text = resp.status + ' ' + resp.statusText + '\\n' + text;
            } else if (ct.includes('json')) {
                try { text = JSON.stringify(JSON.parse(text), null, 2); } catch(e) {}
            }
            pre.textContent = text;
            var existing = detail.querySelector('pre.response');
            if (existing) existing.remove();
            pre.className = 'response';
            detail.appendChild(pre);
        });
    }).catch(function(err) {
        var pre = document.createElement('pre');
        pre.textContent = 'Network error: ' + err.message;
        var existing = detail.querySelector('pre.response');
        if (existing) existing.remove();
        pre.className = 'response';
        detail.appendChild(pre);
    });
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 5: Commit**

```bash
git add core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "show error responses and network failures in Try mode"
```

## Chunk 2: Interaction Feedback

### Task 4: Add clipboard feedback

In curl/httpie modes, clicking Send copies to clipboard with no visual indication.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (TREE_KEYBOARD_JS, lines ~317-319)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x] **Step 1: Write a failing test**

In `GivenAppWithParams`:
```java
@Test void curlModeShowsCopiedFeedback() {
    app.clickModeButton("curl");
    app.focusTree();
    app.pressKey("ArrowRight");
    app.clickTreeNode("pets/{petId}/GET.html");
    app.waitForInput("petId");
    app.fillInput("petId", "42");
    app.clickSend();

    then(app.sendButtonText()).isEqualTo("Copied!");
}
```

Add `sendButtonText()` to `AppFixture`:
```java
String sendButtonText() {return page.locator("#detail button[data-path]").textContent();}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithParams#curlModeShowsCopiedFeedback'`
Expected: FAIL — button still says "Send".

- [x] **Step 3: Fix the JavaScript**

In `TREE_KEYBOARD_JS`, after the clipboard write calls (lines ~317-319), add feedback:

Current code:
```javascript
if (mode === 'curl') {
    navigator.clipboard.writeText('curl ' + url);
} else if (mode === 'httpie') {
    navigator.clipboard.writeText('http ' + method + ' ' + url);
```

New code:
```javascript
if (mode === 'curl') {
    navigator.clipboard.writeText('curl ' + url);
    showCopied(sendBtn);
} else if (mode === 'httpie') {
    navigator.clipboard.writeText('http ' + method + ' ' + url);
    showCopied(sendBtn);
```

Add the `showCopied` function inside the DOMContentLoaded handler, before the Send button
handler:
```javascript
function showCopied(btn) {
    var original = btn.textContent;
    btn.textContent = 'Copied!';
    setTimeout(function() { btn.textContent = original; }, 1500);
}
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 5: Commit**

```bash
git add core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "show Copied! feedback on clipboard copy"
```

### Task 5: Change Send button label to Copy in clipboard modes

The button says "Send" in curl/httpie modes but it copies to clipboard.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (TREE_KEYBOARD_JS)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/AppFixture.java` (fix `clickSend()` locator)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

**Important prerequisite:** The `clickSend()` method in `AppFixture` uses
`page.locator("#detail button:text('Send')")` which matches by visible text. After this task
changes the label to "Copy", existing tests in curl/httpie mode will break. Fix `clickSend()`
to use a label-agnostic selector:

```java
void clickSend() {page.locator("#detail button[data-path]").click();}
```

This must be done before the JS change.

- [x] **Step 1: Write a failing test**

In `GivenAppWithParams`:
```java
@Test void curlModeShowsCopyButtonLabel() {
    app.clickModeButton("curl");
    app.focusTree();
    app.pressKey("ArrowRight");
    app.clickTreeNode("pets/{petId}/GET.html");
    app.waitForInput("petId");

    then(app.sendButtonText()).isEqualTo("Copy");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithParams#curlModeShowsCopyButtonLabel'`
Expected: FAIL — button says "Send".

- [x] **Step 3: Fix the JavaScript**

Two triggers need to update the button label:

**(a) Mode change** — in the mode toggle handler (lines ~279-287), after setting the mode,
update any existing Send button:

Add after `btn.classList.add('is-selected', 'is-primary');` (line ~285):
```javascript
var newMode = btn.getAttribute('data-mode-btn');
var sendBtns = document.querySelectorAll('#detail button[data-path]');
sendBtns.forEach(function(b) { b.textContent = newMode === 'try' ? 'Send' : 'Copy'; });
```

**(b) Fragment load** — add an HTMX `afterSwap` listener to update the button after a
fragment loads:

Add after the mode toggle block:
```javascript
document.body.addEventListener('htmx:afterSwap', function() {
    var currentMode = modeContainer ? modeContainer.getAttribute('data-mode') : 'try';
    if (currentMode !== 'try') {
        var sendBtns = document.querySelectorAll('#detail button[data-path]');
        sendBtns.forEach(function(b) { b.textContent = 'Copy'; });
    }
});
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 5: Commit**

```bash
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "show Copy label on Send button in curl/httpie modes"
```

### Task 6: Add loading state for Try mode

No feedback during fetch.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (TREE_KEYBOARD_JS)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x] **Step 1: Write a failing test**

This is harder to test because the loading state is transient. Test that after a successful
fetch, the button is re-enabled and shows "Send" (not stuck in "Sending..." state):

In `GivenAppWithOneGet.InTryMode`:
```java
@Test void tryModeSendButtonRecoversAfterResponse() {
    app.mockEndpoint("/pets", "application/json", "{\"id\":\"1\"}");
    navigateToListPetsAndSend();

    then(app.sendButtonText()).isEqualTo("Send");
    then(app.isSendButtonEnabled()).isTrue();
}
```

Add `isSendButtonEnabled()` to `AppFixture`:
```java
boolean isSendButtonEnabled() {
    return (Boolean) page.evaluate(
            "() => !document.querySelector('#detail button[data-path]').disabled");
}
```

- [x] **Step 2: Run test to verify it passes** (it should pass with current code as a baseline)

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet$InTryMode#tryModeSendButtonRecoversAfterResponse'`
Expected: PASS — this is a regression guard.

- [x] **Step 3: Add loading state to the JavaScript**

In `TREE_KEYBOARD_JS`, in the try mode block, wrap the fetch with loading state management:

Before `fetch(url)`, add:
```javascript
sendBtn.disabled = true;
sendBtn.textContent = 'Sending...';
```

In the `.then()` callback, after appending the response, add:
```javascript
sendBtn.disabled = false;
sendBtn.textContent = 'Send';
```

In the `.catch()` callback, after appending the error, add:
```javascript
sendBtn.disabled = false;
sendBtn.textContent = 'Send';
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 5: Commit**

```bash
git add core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "show loading state on Send button during fetch"
```

## Chunk 3: Visual Design

### Task 7: Add custom stylesheet with tree item states

Add selected, hover, and focus styling for tree items.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x] **Step 1: Write a failing test**

Verify the custom stylesheet is linked and the CSS file exists:

In `GivenAppWithOneGet`:
```java
@Test void shouldIncludeCustomStylesheet() {
    then(app.hasStylesheet("openapi-ui.css")).isTrue();
}
```

Add `hasStylesheet()` to `AppFixture`:
```java
boolean hasStylesheet(String name) {
    return (Boolean) page.evaluate(
            "name => !!document.querySelector('link[rel=stylesheet][href=\"' + name + '\"]')", name);
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldIncludeCustomStylesheet'`
Expected: FAIL — no custom stylesheet linked.

- [x] **Step 3: Add the custom stylesheet**

In `OpenApiUiGenerator.java`, add a new string constant:
```java
private static final String CUSTOM_CSS = """
        [role="treeitem"] > span {
            cursor: pointer;
            padding: 2px 6px;
            border-radius: 4px;
        }
        [role="treeitem"] > span:hover {
            background-color: hsl(0, 0%, 96%);
        }
        [role="treeitem"][aria-selected="true"] > span:first-child {
            background-color: hsl(217, 71%, 95%);
        }
        [role="tree"]:focus-visible [role="treeitem"][aria-selected="true"] > span:first-child {
            outline: 2px solid hsl(217, 71%, 53%);
            outline-offset: 1px;
        }
        """;
```

In the `generate()` method, after writing `index.html` (line 78), add:
```java
Files.writeString(outputDir.resolve("openapi-ui.css"), CUSTOM_CSS);
```

In the page builder (line 72), add the stylesheet link:
```java
var page = html(pageTitle)
        .stylesheet("bulma.min.css")
        .stylesheet("openapi-ui.css")
        .script("htmx.min.js")
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/AppFixture.java
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "add custom stylesheet with tree item hover and selected states"
```

### Task 8: Add method badges

Color-coded method tags in the tree and detail heading.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x] **Step 1: Write a failing test**

In `GivenAppWithOneGet`:
```java
@Test void shouldShowMethodBadge() {
    then(app.hasMethodBadge("GET")).isTrue();
}
```

Add `hasMethodBadge()` to `AppFixture`:
```java
boolean hasMethodBadge(String method) {
    return page.locator(".method-badge.method-" + method.toLowerCase()).isVisible();
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldShowMethodBadge'`
Expected: FAIL — no elements with class `method-badge`.

- [x] **Step 3: Add method badges to the generator**

In `renderNode()` (line ~184), change the tree item content from plain text to a badge span:

Current code:
```java
var label = summary != null ? " " + method + " — " + summary : " " + method;
item.content(span(label)
```

New code:
```java
var badge = span(method.name()).classes("method-badge", "method-" + method.name().toLowerCase());
var labelText = summary != null ? " — " + summary : "";
item.content(span().content(badge).content(labelText)
```

In `generateFragments()` (line ~109), add a badge to the detail heading:

Current code:
```java
var fragment = div().content(
        title(method.name() + " /" + fullPath),
```

New code:
```java
var headingBadge = span(method.name()).classes("method-badge", "method-" + method.name().toLowerCase());
var fragment = div().content(
        div().classes("is-flex", "is-align-items-center", "mb-4").content(
                headingBadge,
                element("h2").classes("title", "is-3", "mb-0").content(" /" + fullPath)),
```

This uses `element("h2")` to preserve semantic heading markup while reducing from `is-1` to
`is-3` per the spec. The `element()` factory is available from `HtmlBasics.*`.

Add CSS for method badges to `CUSTOM_CSS`:
```css
.method-badge {
    display: inline-block;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 700;
    color: white;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    vertical-align: middle;
}
.method-get { background-color: hsl(141, 53%, 53%); }
.method-post { background-color: hsl(217, 71%, 53%); }
.method-put { background-color: hsl(44, 100%, 48%); }
.method-delete { background-color: hsl(348, 86%, 61%); }
.method-patch { background-color: hsl(271, 100%, 71%); }
```

- [x] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl core`
Expected: ALL PASS. Existing tests that call `detailText()` check for text content like
"List pets" which comes from the summary `p()` element (unchanged). The heading structure
change (from `title()` to badge + `h2`) does not affect `textContent()` assertions since
they match substrings.

- [x] **Step 5: Take screenshots and review**

Run: `mvn test -pl core`
Then visually review `core/target/screenshots/` using the `frontend-design` plugin per
CLAUDE.md instructions. Adjust colors/sizing if needed.

- [x] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/AppFixture.java
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "add color-coded method badges to tree and detail heading"
```

### Task 9: Add tree/detail separator

Add a visual boundary between the tree and detail columns.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (CUSTOM_CSS)

- [x] **Step 1: Add CSS for the separator**

No test needed — this is pure visual styling. Add to `CUSTOM_CSS`:
```css
.columns.is-desktop > .column.is-one-third {
    border-right: 1px solid hsl(0, 0%, 92%);
    padding-right: 1.5rem;
}
```

- [x] **Step 2: Run tests to verify nothing broke**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 3: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "add visual separator between tree and detail panes"
```

### Task 10: Final screenshot review

- [x] **Step 1: Run all tests**

Run: `mvn test -pl core`
Expected: ALL PASS

- [x] **Step 2: Review screenshots**

Review all screenshots in `core/target/screenshots/` using the `frontend-design` plugin.
Verify the combined visual result is cohesive and the design improvements work together.

- [x] **Step 3: Run full build**

Run: `mvn verify`
Expected: ALL PASS including demo module.
