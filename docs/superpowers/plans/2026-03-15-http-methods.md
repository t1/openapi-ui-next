# HTTP Methods & Request Body Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make POST/PUT/PATCH/DELETE fully functional by fixing HTTP method passing in all modes and adding request body textarea input with JSON skeleton pre-fill.

**Architecture:** Fix the existing JS to pass the HTTP method to `fetch()` and `curl`. Add request body textarea generation in `OpenApiUiGenerator.generateFragments()` when an operation has a `requestBody`. Add skeleton generation from schema. Extend the demo app with PUT and PATCH endpoints.

**Tech Stack:** Java 21, bulma-java, swagger-parser, Playwright (browser tests), Quarkus (demo), JUnit 5

**Spec:** `docs/superpowers/specs/2026-03-15-http-methods-design.md`

---

## Chunk 1: Fix HTTP Method in fetch() and curl

### Task 1: Fix fetch() to pass the HTTP method

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:403` (APP_JS fetch call)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`
- Test resource: `core/src/test/resources/post-endpoint.yaml` (new)

- [x]**Step 1: Create a test spec with a POST endpoint**

Create `core/src/test/resources/post-endpoint.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Test API
  version: 1.0.0
servers:
  - url: https://api.example.com
paths:
  /pets:
    post:
      summary: Add a pet
      operationId: addPet
      responses:
        '201':
          description: Pet created
```

- [x]**Step 2: Write a failing browser test that POST is sent**

In `BrowserTest.java`, add a new nested class after `GivenAppWithParams`:

```java
@Nested class GivenAppWithPostEndpoint {
    @RegisterExtension static AppFixture app =
            context.launch("post-endpoint.yaml").withBaseUrlOverride();

    @Test void tryModeSendsPostRequest() {
        app.mockEndpoint("/pets", "POST", "application/json", "{\"id\":1}");
        app.clickTreeNode("pets/POST.html");
        app.waitForDetailContent("Add a pet");
        app.clickSend();
        app.waitForResponse();

        then(app.responseText()).contains("\"id\"");
    }
}
```

This requires `mockEndpoint` to check the HTTP method. Currently it ignores it.

- [x]**Step 3: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithPostEndpoint#tryModeSendsPostRequest'`
Expected: FAIL — the mock needs a method-aware overload, and fetch sends GET.

- [x]**Step 4: Add method-aware mock to AppFixture**

In `AppFixture.java`, add a new `mockEndpoint` overload that checks the request method. Modify the existing mock methods to accept any method (or add a new one):

```java
void mockEndpoint(String path, String expectedMethod, String contentType, String body) {
    try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
    server.createContext("/api" + path, exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
            exchange.sendResponseHeaders(405, 0);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        var bytes = body.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    });
}
```

- [x]**Step 5: Fix fetch() to pass HTTP method**

In `OpenApiUiGenerator.java`, change line 403 from:

```javascript
fetch(url).then(function(resp) {
```

to:

```javascript
fetch(url, { method: method }).then(function(resp) {
```

- [x]**Step 6: Run the test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithPostEndpoint#tryModeSendsPostRequest'`
Expected: PASS

- [x]**Step 7: Verify existing tests still pass**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 8: Commit**

```bash
git add core/src/test/resources/post-endpoint.yaml core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "fix fetch to pass HTTP method"
```

### Task 2: Fix curl mode to include -X METHOD

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:394-395` (APP_JS curl)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x]**Step 1: Write a failing test for curl with method**

In `BrowserTest.java` `GivenAppWithParams` (not InTryMode), the existing `curlModeCopiesCommand` test checks for `curl` and the URL. Add a new test that verifies the method is included:

```java
@Test void curlModeIncludesMethod() {
    app.clickModeButton("curl");
    app.clickTreeNode("pets/{petId}/GET.html");
    app.waitForInput("petId");
    app.fillInput("petId", "42");
    app.clickSend();

    then(app.readClipboard()).startsWith("curl -X GET");
}
```

- [x]**Step 2: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithParams#curlModeIncludesMethod'`
Expected: FAIL — clipboard contains `curl https://...` without `-X GET`.

- [x]**Step 3: Fix curl command generation**

In `OpenApiUiGenerator.java`, change line 395 from:

```javascript
navigator.clipboard.writeText('curl ' + url);
```

to:

```javascript
navigator.clipboard.writeText('curl -X ' + method + ' ' + url);
```

- [x]**Step 4: Update existing curl test assertion**

The existing `curlModeCopiesCommand` test checks `.contains("curl")`. Update it to match the new format:

```java
then(app.readClipboard())
        .contains("curl -X GET")
        .contains("https://api.example.com/pets/42");
```

- [x]**Step 5: Run all tests to verify they pass**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "fix curl mode to include -X METHOD"
```

---

## Chunk 2: Request Body Textarea & JSON Skeleton

### Task 3: Generate request body textarea in fragment

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:196-229` (generateFragments)
- Test: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`
- Create: `core/src/test/resources/request-body.yaml` (new test spec)

- [x]**Step 1: Create a test spec with a request body**

Create `core/src/test/resources/request-body.yaml`:

```yaml
openapi: 3.0.3
info:
  title: Test API
  version: 1.0.0
servers:
  - url: https://api.example.com
paths:
  /pets:
    post:
      summary: Add a pet
      operationId: addPet
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                name:
                  type: string
                age:
                  type: integer
                active:
                  type: boolean
      responses:
        '201':
          description: Pet created
```

- [x]**Step 2: Write a failing test for textarea generation**

In `OpenApiUiGeneratorTest.java`:

```java
@Test void shouldGenerateRequestBodyTextarea() throws Exception {
    var specPath = Path.of(getClass().getResource("/request-body.yaml").toURI());

    new OpenApiUiGenerator(specPath, outputDir).generate();

    var fragment = Files.readString(outputDir.resolve("pets/POST.html"));
    then(fragment).contains("data-request-body");
    then(fragment).contains("\"name\"");
    then(fragment).contains("\"age\"");
}
```

- [x]**Step 3: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldGenerateRequestBodyTextarea'`
Expected: FAIL — no `data-request-body` in fragment.

- [x]**Step 4: Implement request body textarea generation**

In `OpenApiUiGenerator.java`, in the `generateFragments` method, after the parameter inputs section (line 206) and before the response schema section (line 207), add request body handling:

```java
if (operation.getRequestBody() != null && operation.getRequestBody().getContent() != null) {
    var jsonContent = operation.getRequestBody().getContent().get("application/json");
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
```

Add the `generateJsonSkeleton` method:

```java
@SuppressWarnings("unchecked")
private String generateJsonSkeleton(Schema<?> schema) {
    var properties = (Map<String, Schema<?>>) schema.getProperties();
    if (properties == null) return "{}";
    var sb = new StringBuilder("{\n");
    var first = true;
    for (var entry : properties.entrySet()) {
        if (!first) sb.append(",\n");
        first = false;
        sb.append("  \"").append(entry.getKey()).append("\": ");
        sb.append(defaultValue(entry.getValue().getType()));
    }
    sb.append("\n}");
    return sb.toString();
}

private static String defaultValue(String type) {
    if (type == null) return "null";
    return switch (type) {
        case "string" -> "\"\"";
        case "integer", "number" -> "0";
        case "boolean" -> "false";
        case "array" -> "[]";
        case "object" -> "{}";
        default -> "null";
    };
}
```

Also add the `element("textarea")` import — `element` is already available from `HtmlBasics.*`.

- [x]**Step 5: Run the test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldGenerateRequestBodyTextarea'`
Expected: PASS

- [x]**Step 6: Run all tests**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 7: Commit**

```bash
git add core/src/test/resources/request-body.yaml core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "generate request body textarea with JSON skeleton"
```

### Task 4: Frontend sends request body in Try mode

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (APP_JS)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x]**Step 1: Write a failing browser test for POST with body**

In `BrowserTest.java`, update the `GivenAppWithPostEndpoint` — but now use `request-body.yaml` for a POST with a requestBody schema. Add a new nested class:

```java
@Nested class GivenAppWithRequestBody {
    @RegisterExtension static AppFixture app =
            context.launch("request-body.yaml").withBaseUrlOverride();

    @Test void tryModeSendsRequestBody() {
        app.mockEndpointWithBodyEcho("/pets", "POST");
        app.clickTreeNode("pets/POST.html");
        app.waitForDetailContent("Add a pet");
        app.fillRequestBody("{\"name\": \"Fido\", \"age\": 3}");
        app.clickSend();
        app.waitForResponse();

        then(app.responseText()).contains("Fido");
    }
}
```

This needs new AppFixture methods: `fillRequestBody` and `mockEndpointWithBodyEcho`.

- [x]**Step 2: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithRequestBody#tryModeSendsRequestBody'`
Expected: FAIL — methods don't exist yet.

- [x]**Step 3: Add AppFixture helpers**

In `AppFixture.java`:

```java
void fillRequestBody(String body) {
    page.locator("#detail textarea[data-request-body]").fill(body);
}

void mockEndpointWithBodyEcho(String path, String expectedMethod) {
    try {server.removeContext("/api" + path);} catch (IllegalArgumentException ignored) {}
    server.createContext("/api" + path, exchange -> {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expectedMethod)) {
            exchange.sendResponseHeaders(405, 0);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        var requestBody = new String(exchange.getRequestBody().readAllBytes());
        var bytes = requestBody.getBytes();
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    });
}
```

Note: The mock needs CORS preflight handling because `fetch` with `Content-Type: application/json` triggers a preflight OPTIONS request.

- [x]**Step 4: Update APP_JS to send request body**

In `OpenApiUiGenerator.java` APP_JS, in the try mode section, before the `fetch()` call, add body detection. Replace the try-mode block (lines 400-429) with:

```javascript
} else if (mode === 'try') {
    sendBtn.disabled = true;
    sendBtn.textContent = 'Sending...';
    var bodyTextarea = detail.querySelector('textarea[data-request-body]');
    var fetchOptions = { method: method };
    if (bodyTextarea && bodyTextarea.value) {
        fetchOptions.body = bodyTextarea.value;
        fetchOptions.headers = { 'Content-Type': 'application/json' };
    }
    fetch(url, fetchOptions).then(function(resp) {
```

(The rest of the fetch chain stays the same, just remove the old `{ method: method }` since it's now in `fetchOptions`.)

- [x]**Step 5: Run the test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithRequestBody#tryModeSendsRequestBody'`
Expected: PASS

- [x]**Step 6: Run all tests**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 7: Commit**

```bash
git add core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "send request body in try mode"
```

### Task 5: Include request body in curl and httpie commands

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (APP_JS curl/httpie)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

- [x]**Step 1: Write failing tests for curl and httpie with body**

In `BrowserTest.java` `GivenAppWithRequestBody`, add:

```java
@Test void curlModeIncludesRequestBody() {
    app.clickModeButton("curl");
    app.clickTreeNode("pets/POST.html");
    app.waitForDetailContent("Add a pet");
    app.fillRequestBody("{\"name\": \"Fido\"}");
    app.clickSend();

    then(app.readClipboard())
            .contains("curl -X POST")
            .contains("-H 'Content-Type: application/json'")
            .contains("-d '{\"name\": \"Fido\"}'");
}

@Test void httpieModeIncludesRequestBody() {
    app.clickModeButton("httpie");
    app.clickTreeNode("pets/POST.html");
    app.waitForDetailContent("Add a pet");
    app.fillRequestBody("{\"name\": \"Fido\"}");
    app.clickSend();

    then(app.readClipboard())
            .contains("http POST")
            .contains("echo '{\"name\": \"Fido\"}'");
}
```

- [x]**Step 2: Run the tests to verify they fail**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithRequestBody'`
Expected: FAIL for the new tests.

- [x]**Step 3: Update curl and httpie JS to include body**

In `OpenApiUiGenerator.java` APP_JS, update the curl and httpie blocks. Before the mode checks, add body detection (shared with try mode):

```javascript
var bodyTextarea = detail.querySelector('textarea[data-request-body]');
var bodyValue = bodyTextarea ? bodyTextarea.value : '';

if (mode === 'curl') {
    var cmd = 'curl -X ' + method + ' ' + url;
    if (bodyValue) cmd = 'curl -X ' + method + " -H 'Content-Type: application/json' -d '" + bodyValue + "' " + url;
    navigator.clipboard.writeText(cmd);
    showCopied(sendBtn);
} else if (mode === 'httpie') {
    var cmd = 'http ' + method + ' ' + url;
    if (bodyValue) cmd = "echo '" + bodyValue + "' | http " + method + ' ' + url + " Content-Type:application/json";
    navigator.clipboard.writeText(cmd);
    showCopied(sendBtn);
} else if (mode === 'try') {
```

Move the `bodyTextarea` declaration above all three mode blocks so it's shared. The try mode block should reuse `bodyValue` instead of querying again.

- [x]**Step 4: Run all tests to verify they pass**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java
git commit -m "include request body in curl and httpie commands"
```

### Task 6: Add request body CSS styling

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (APP_CSS)

- [x]**Step 1: Write a failing test for textarea styling**

In `OpenApiUiGeneratorTest.java`:

```java
@Test void shouldIncludeRequestBodyStyles() throws Exception {
    var specPath = Path.of(getClass().getResource("/request-body.yaml").toURI());
    new OpenApiUiGenerator(specPath, outputDir).generate();

    var css = Files.readString(outputDir.resolve("openapi-ui.css"));
    then(css).contains("data-request-body");
}
```

- [x]**Step 2: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldIncludeRequestBodyStyles'`
Expected: FAIL

- [x]**Step 3: Add CSS for the textarea**

In `OpenApiUiGenerator.java` APP_CSS, add:

```css
#detail textarea[data-request-body] {
    font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
    font-size: 0.875rem;
    resize: vertical;
}
```

- [x]**Step 4: Run all tests**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "add request body textarea CSS styling"
```

---

## Chunk 3: Demo App Endpoints

### Task 7: Add PUT /pets/{id} to demo app

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`
- Test: `demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java`

- [x]**Step 1: Write a failing test for PUT**

In `PetResourceTest.java`, add:

```java
@Test void shouldUpdatePet() {
    // create a pet to update
    var id = given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"Temp\",\"status\":\"available\",\"ownerId\":1}")
            .when().post("/pets")
            .then().statusCode(201)
            .extract().path("id");

    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"Updated\",\"status\":\"adopted\",\"ownerId\":2}")
            .when().put("/pets/" + id)
            .then()
            .statusCode(200)
            .body("name", is("Updated"))
            .body("status", is("adopted"));
}

@Test void shouldReturn404ForPutUnknownPet() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"X\",\"status\":\"available\",\"ownerId\":1}")
            .when().put("/pets/999")
            .then()
            .statusCode(404);
}
```

- [x]**Step 2: Run the tests to verify they fail**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldUpdatePet'`
Expected: FAIL — 405 Method Not Allowed (no PUT handler).

- [x]**Step 3: Implement PUT endpoint**

In `PetResource.java`, add the PUT method:

```java
@PUT @Path("/{id}") @Operation(summary = "Update a pet")
public Pet update(@PathParam("id") long id, Pet pet) {
    for (int i = 0; i < PETS.size(); i++) {
        if (PETS.get(i).id() == id) {
            var updated = new Pet(id, pet.name(), pet.status(), pet.ownerId());
            PETS.set(i, updated);
            return updated;
        }
    }
    throw new NotFoundException();
}
```

Add the `PUT` import: `import jakarta.ws.rs.PUT;`

- [x]**Step 4: Run the tests to verify they pass**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldUpdatePet+shouldReturn404ForPutUnknownPet'`
Expected: PASS

- [x]**Step 5: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java
git commit -m "add PUT /pets/{id} to demo app"
```

### Task 8: Add PATCH /pets/{id} to demo app

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`
- Test: `demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java`

- [x]**Step 1: Write a failing test for PATCH**

In `PetResourceTest.java`, add:

```java
@Test void shouldPatchPet() {
    // create a pet to patch
    var id = given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"Temp\",\"status\":\"available\",\"ownerId\":1}")
            .when().post("/pets")
            .then().statusCode(201)
            .extract().path("id");

    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"Patched\"}")
            .when().patch("/pets/" + id)
            .then()
            .statusCode(200)
            .body("name", is("Patched"))
            .body("status", is("available"));
}

@Test void shouldReturn404ForPatchUnknownPet() {
    given()
            .contentType(APPLICATION_JSON)
            .body("{\"name\":\"X\"}")
            .when().patch("/pets/999")
            .then()
            .statusCode(404);
}
```

- [x]**Step 2: Run the tests to verify they fail**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldPatchPet'`
Expected: FAIL — 405 Method Not Allowed (no PATCH handler).

- [x]**Step 3: Implement PATCH endpoint**

In `PetResource.java`, add the PATCH method. Since `Pet` is a record (immutable), accept a `JsonObject` and merge non-null fields:

```java
@PATCH @Path("/{id}") @Operation(summary = "Partially update a pet")
public Pet patch(@PathParam("id") long id, JsonObject patch) {
    for (int i = 0; i < PETS.size(); i++) {
        var existing = PETS.get(i);
        if (existing.id() == id) {
            var updated = new Pet(id,
                    patch.containsKey("name") ? patch.getString("name") : existing.name(),
                    patch.containsKey("status") ? patch.getString("status") : existing.status(),
                    patch.containsKey("ownerId") ? patch.getJsonNumber("ownerId").longValue() : existing.ownerId());
            PETS.set(i, updated);
            return updated;
        }
    }
    throw new NotFoundException();
}
```

Add imports: `import jakarta.ws.rs.PATCH;` and `import jakarta.json.JsonObject;`

- [x]**Step 4: Run the tests to verify they pass**

Run: `mvn test -pl demo -Dtest='PetResourceTest#shouldPatchPet+shouldReturn404ForPatchUnknownPet'`
Expected: PASS

- [x]**Step 5: Run all demo tests**

Run: `mvn test -pl demo`
Expected: All tests pass.

- [x]**Step 6: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java demo/src/test/java/com/github/t1/openapi/ui/demo/PetResourceTest.java
git commit -m "add PATCH /pets/{id} to demo app"
```

### Task 9: Screenshot review

- [x]**Step 1: Run core tests to generate screenshots**

Run: `mvn test -pl core`
Expected: All tests pass. Screenshots generated in `core/target/screenshots/`.

- [x]**Step 2: Review screenshots**

Review `core/target/screenshots/` for design quality using the `frontend-design` skill. Specifically check:
- Request body textarea appearance in the detail pane
- Proper spacing between parameters, textarea, and send button
- Method badges still display correctly

- [x]**Step 3: Build demo to verify end-to-end**

Run: `mvn package -pl demo -am`
Expected: Build succeeds, generated UI includes PUT and PATCH fragments with request body textarea.

---

## Chunk 4: Squash and finalize

### Task 10: Squash commits

- [x]**Step 1: Squash all commits from this work into one**

Count the commits made during this plan (N) and squash them:

```bash
git reset --soft HEAD~N && git commit -m "add request body support and fix HTTP method handling"
```

- [x]**Step 2: Verify final state**

Run: `mvn test -pl core`
Expected: All tests pass.

- [x]**Step 3: Tick spec checkbox**

Mark the spec as complete in the design doc if applicable.
