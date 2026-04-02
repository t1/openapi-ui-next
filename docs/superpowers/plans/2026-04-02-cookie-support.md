# Cookie Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support OpenAPI cookie parameters in Try-it-out/copy modes and show an inline note on documented Set-Cookie response headers.

**Architecture:** Two independent changes in app.js: (1) route `in: cookie` params into a `Cookie` header instead of query string, (2) show a browser-limitation note on absent `set-cookie` response headers. Demo app extended to exercise both.

**Tech Stack:** JavaScript (app.js), Java (demo JAX-RS annotations), Playwright browser tests

---

### Task 1: Send cookie parameters as Cookie header in Try-it-out mode

**Files:**
- Create: `core/src/test/resources/cookie-param.yaml`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:1035-1047`
- Test: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` (new nested class)

- [ ] **Step 1: Create test fixture with cookie parameter**

Create `core/src/test/resources/cookie-param.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Cookie Param API
  version: "1.0"
servers:
  - url: https://api.example.com
paths:
  /pets:
    get:
      summary: List pets
      parameters:
        - name: session
          in: cookie
          schema:
            type: string
      responses:
        "200":
          description: OK
```

- [ ] **Step 2: Write failing browser test**

Add to `BrowserTest.java` a new nested class:

```java
@ResourceLock("cookie-param") @Nested class GivenAppWithCookieParam {
    @RegisterExtension static AppFixture app = launch("cookie-param.yaml").withBaseUrlOverride();

    @Test void shouldSendCookieParamAsCookieHeader() {
        app.mockEndpoint("/pets", "application/json", "[]");
        app.focusTree();
        app.pressKey("Enter");
        app.waitForDetailContent("List pets");
        app.fillInput("session", "abc123");
        app.clickSend();
        app.waitForResponse();

        then(app.lastRequestHeader("Cookie")).isEqualTo("session=abc123");
    }
}
```

- [ ] **Step 3: Add `lastRequestHeader` helper to AppFixture**

Add to `AppFixture.java`:

```java
String lastRequestHeader(String name) {
    return lastRequestHeaders.get(name);
}
```

And capture request headers in the mock server's `sendResponse` path — store them in a field:

```java
private Map<String, String> lastRequestHeaders = new java.util.concurrent.ConcurrentHashMap<>();
```

In `mockEndpoint`, capture headers before sending the response:

```java
void mockEndpoint(String path, String contentType, String body) {
    replaceContext("/api" + path, exchange -> {
        lastRequestHeaders.clear();
        exchange.getRequestHeaders().forEach((k, v) -> lastRequestHeaders.put(k, v.getFirst()));
        sendResponse(exchange, 200, contentType, body);
    });
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithCookieParam#shouldSendCookieParamAsCookieHeader'`
Expected: FAIL — cookie param is sent as query string, not as Cookie header

- [ ] **Step 5: Implement cookie parameter routing in app.js**

In `app.js` around line 1035-1047, add a `cookie` case to collect cookie params into a `Cookie` header. Change the `inputs.forEach` block:

```javascript
var cookieParts = [];
inputs.forEach(function(inp) {
    var name = inp.getAttribute('name');
    var paramIn = inp.getAttribute('data-param-in') || 'query';
    var isCheckbox = inp.type === 'checkbox';
    var val = isCheckbox ? (inp.checked ? 'true' : '') : inp.value;
    if (paramIn === 'path' || pathTemplate.includes('{' + name + '}')) {
        resolvedPath = resolvedPath.replace('{' + name + '}', encodeURIComponent(val));
    } else if (paramIn === 'header') {
        if (val) requestHeaders[name] = val;
    } else if (paramIn === 'cookie') {
        if (val) cookieParts.push(name + '=' + val);
    } else if (val) {
        queryParams.push(name + '=' + encodeURIComponent(val));
    }
});
if (cookieParts.length > 0) requestHeaders['Cookie'] = cookieParts.join('; ');
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithCookieParam#shouldSendCookieParamAsCookieHeader'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/test/resources/cookie-param.yaml \
  core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
  core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java \
  core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java
git commit -m "send cookie parameters as Cookie header in try-it-out mode"
```

### Task 2: Include cookie params in curl copy mode

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:1074-1082`
- Test: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` (add test to GivenAppWithCookieParam)

- [ ] **Step 1: Write failing test**

Add to `GivenAppWithCookieParam`:

```java
@Test void shouldIncludeCookieParamInCurlCommand() {
    app.focusTree();
    app.pressKey("Enter");
    app.waitForDetailContent("List pets");
    app.fillInput("session", "abc123");
    app.clickModeButton("curl");
    app.clickSend();

    then(app.readClipboard()).contains("-b 'session=abc123'");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithCookieParam#shouldIncludeCookieParamInCurlCommand'`
Expected: FAIL — no `-b` flag in curl output

- [ ] **Step 3: Implement curl cookie flag**

In the curl branch of app.js (around line 1074-1082), after building `headerFlags`, add cookie handling. The `cookieParts` array is already populated from Task 1. Add before the `cmd` assembly:

```javascript
if (mode === 'curl') {
    var headerFlags = Object.keys(requestHeaders).filter(function(h) {
        return h !== 'Cookie';
    }).map(function(h) {
        return "-H '" + h + ": " + requestHeaders[h] + "'";
    }).join(' ');
    var cmd = 'curl -X ' + method;
    if (headerFlags) cmd += ' ' + headerFlags;
    if (cookieParts.length > 0) cmd += " -b '" + cookieParts.join('; ') + "'";
    if (bodyValue) cmd += " -H 'Content-Type: application/json' -d '" + bodyValue + "'";
    cmd += ' ' + url;
    navigator.clipboard.writeText(cmd);
    showCopied(sendBtn);
}
```

Note: filter out `Cookie` from regular headers since it's handled via `-b`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithCookieParam#shouldIncludeCookieParamInCurlCommand'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
  core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "include cookie params as curl -b flag in copy mode"
```

### Task 3: Include cookie params in httpie copy mode

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:1084-1093`
- Test: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` (add test to GivenAppWithCookieParam)

- [ ] **Step 1: Write failing test**

Add to `GivenAppWithCookieParam`:

```java
@Test void shouldIncludeCookieParamInHttpieCommand() {
    app.focusTree();
    app.pressKey("Enter");
    app.waitForDetailContent("List pets");
    app.fillInput("session", "abc123");
    app.clickModeButton("httpie");
    app.clickSend();

    then(app.readClipboard()).contains("Cookie:session=abc123");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithCookieParam#shouldIncludeCookieParamInHttpieCommand'`
Expected: FAIL — cookie sent as Cookie header arg, but format may be wrong or missing

- [ ] **Step 3: Implement httpie cookie header**

In the httpie branch of app.js, similarly filter out `Cookie` from regular headers and add it explicitly. httpie sends cookies via `Cookie:name=value`:

```javascript
} else if (mode === 'httpie') {
    var headerArgs = Object.keys(requestHeaders).filter(function(h) {
        return h !== 'Cookie';
    }).map(function(h) {
        return h + ':' + requestHeaders[h];
    }).join(' ');
    var cmd = bodyValue
            ? "echo '" + bodyValue + "' | http " + method + ' ' + url + " Content-Type:application/json"
            : 'http ' + method + ' ' + url;
    if (headerArgs) cmd += ' ' + headerArgs;
    if (cookieParts.length > 0) cmd += ' Cookie:' + cookieParts.join('\\; ');
    navigator.clipboard.writeText(cmd);
    showCopied(sendBtn);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithCookieParam#shouldIncludeCookieParamInHttpieCommand'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
  core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "include cookie params in httpie copy mode"
```

### Task 4: Show browser-limitation note on Set-Cookie response headers

**Files:**
- Create: `core/src/test/resources/response-set-cookie.yaml`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:639-651`
- Test: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` (new nested class)

- [ ] **Step 1: Create test fixture with Set-Cookie response header**

Create `core/src/test/resources/response-set-cookie.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Set-Cookie API
  version: "1.0"
servers:
  - url: https://api.example.com
paths:
  /login:
    post:
      summary: Login
      responses:
        "200":
          description: Logged in
          headers:
            Set-Cookie:
              description: Session cookie
              schema:
                type: string
          content:
            application/json:
              schema:
                type: object
                properties:
                  ok:
                    type: boolean
```

- [ ] **Step 2: Write failing browser test**

Add to `BrowserTest.java`:

```java
@ResourceLock("response-set-cookie") @Nested class GivenAppWithSetCookieHeader {
    @RegisterExtension static AppFixture app = launch("response-set-cookie.yaml").withBaseUrlOverride();

    @Test void shouldShowBrowserLimitationNoteForSetCookieHeader() {
        app.mockEndpoint("/login", "application/json", "{\"ok\":true}");
        app.focusTree();
        app.pressKey("Enter");
        app.waitForDetailContent("Login");
        app.clickSend();
        app.waitForResponse();

        then(app.responseHeaderText("set-cookie")).contains("not visible to JS");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithSetCookieHeader#shouldShowBrowserLimitationNoteForSetCookieHeader'`
Expected: FAIL — shows "—" instead of the browser limitation note

- [ ] **Step 4: Implement Set-Cookie note in app.js**

In the response header population logic (around line 639-651), after the `headersByName` lookup, add a special case for `set-cookie`. Change the `else` branch (line 648-651) to:

```javascript
} else {
    if (name === 'set-cookie') {
        el.textContent = 'browser sends these automatically, not visible to JS';
    } else {
        el.textContent = '\u2014';
    }
    el.classList.add('response-header-absent');
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithSetCookieHeader#shouldShowBrowserLimitationNoteForSetCookieHeader'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/test/resources/response-set-cookie.yaml \
  core/src/main/resources/com/github/t1/openapi/ui/generator/app.js \
  core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "show browser limitation note on Set-Cookie response headers"
```

### Task 5: Add cookie parameter and Set-Cookie header to demo app

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java:47-59` (GET /pets)

- [ ] **Step 1: Add cookie parameter and Set-Cookie response header to GET /pets**

Add a `@CookieParam` to the `list` method and a `Set-Cookie` header to its `@APIResponse`:

```java
@GET @Produces(APPLICATION_JSON)
@Operation(summary = "List all pets", description = "Returns all pets from the system. ...")
@APIResponse(responseCode = "200", description = "A list of pets",
        headers = @Header(name = "Set-Cookie", description = "Session tracking cookie",
                schema = @Schema(type = SchemaType.STRING)))
public List<Pet> list(
        @QueryParam("status") PetStatus status,
        @HeaderParam("X-Request-ID") @Parameter(description = "Unique request correlation identifier") String requestId,
        @CookieParam("session_id") @Parameter(description = "Session identifier for tracking") String sessionId) {
    if (status == null) return PETS;
    return PETS.stream().filter(p -> p.status == status).toList();
}
```

- [ ] **Step 2: Rebuild and verify demo app**

Run: `mvn package -pl demo -am`
Verify the generated OpenAPI spec includes the cookie parameter and Set-Cookie header.

- [ ] **Step 3: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java
git commit -m "add cookie parameter and Set-Cookie response header to demo GET /pets"
```

### Task 6: Run full test suite and squash commits

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl core` (with `dangerouslyDisableSandbox: true`, timeout 60s)
Expected: All tests pass

- [ ] **Step 2: Squash all cookie-support commits into one**

```bash
git rebase -i HEAD~5
```

Squash into a single commit: "support cookie parameters and Set-Cookie response header note"

- [ ] **Step 3: Verify demo app renders correctly**

Run: `mvn package -pl demo -am`
Start the app and verify the cookie parameter input and Set-Cookie note are visible in the UI.
