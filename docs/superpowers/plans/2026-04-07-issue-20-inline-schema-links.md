# Inline Schema Links Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Inline OpenAPI response links into the body and header sections as sub-rows beneath the property they source from, removing the separate "Links" section entirely.

**Architecture:** Parse each link's runtime expressions to determine which response properties they reference. Pass the links map into `SchemaRenderer` (for body links) and `schemaHeaders()` (for header links). Each renderer emits a link sub-row after the matching property row. Remove the `responseLinks()` method and all `schema-link-*` CSS classes.

**Tech Stack:** Java (bulma-java HTML generation), CSS, JUnit 5 + Playwright browser tests

---

### Task 1: Add Header-Sourced Link Test Data

Extend the test fixture YAML to include a header-sourced link, so we can test both body and header link placement.

**Files:**
- Modify: `core/src/test/resources/response-links.yaml`

- [ ] **Step 1: Add a response header and a header-sourced link to the getPet operation**

Add an `X-Request-Id` header to the `GET /pets/{petId}` 200 response, and add a second link `GetPetByRequestId` that sources from that header. Also add a target operation `/pets/by-request-id/{requestId}`.

In `response-links.yaml`, replace the `GET /pets/{petId}` response and add the new operation:

```yaml
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
          headers:
            X-Request-Id:
              description: Echoed request identifier
              schema:
                type: string
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
                  ownerId:
                    type: integer
          links:
            GetOwner:
              operationId: getOwner
              parameters:
                ownerId: $response.body#/ownerId
              description: Get the owner of this pet
            GetPetByRequestId:
              operationId: getPetByRequestId
              parameters:
                requestId: $response.header.X-Request-Id
              description: Look up by request ID
```

Add the new target operation after `/owners/{ownerId}`:

```yaml
  /pets/by-request-id/{requestId}:
    get:
      summary: Get pet by request ID
      operationId: getPetByRequestId
      parameters:
        - name: requestId
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: A pet
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
```

- [ ] **Step 2: Run tests to verify nothing breaks**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks" -Dgroups="" -DexcludedGroups="" 2>&1 | tail -20`

Expected: Tests pass (the existing link tests still find GetOwner; the new link/header will be rendered by the current `responseLinks()` method which doesn't know about headers, but the YAML parses fine).

Note: Some existing tests that assert exact link counts or names may need adjustment if the new link shows up. If tests fail due to the new `GetPetByRequestId` link appearing in the current Links section, update the assertions to expect both links.

- [ ] **Step 3: Commit**

```bash
git add core/src/test/resources/response-links.yaml
git commit -m "test: add header-sourced link to response-links fixture"
```

---

### Task 2: Parse Runtime Expressions to Identify Link Sources

Create a utility to parse OpenAPI runtime expressions like `$response.body#/ownerId` and `$response.header.X-Request-Id`, identifying which response property each link parameter sources from.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java` (add inner class or private methods)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [ ] **Step 1: Write failing test — body link appears as sub-row under the source property**

In `BrowserTest.java`, in the `GivenAppWithResponseLinks` nested class, add a test that verifies the GetOwner link appears as a sub-row under `ownerId` in the body schema grid (not in a separate Links section):

```java
@Test void shouldShowBodyLinkAsSubRowUnderSourceProperty() {
    navigateToPetDetail();
    app.toggleSchema("response");
    // The link sub-row should appear inside the schema-props grid,
    // after the ownerId property row
    then(app.schemaLinkSubRowExists("200", "ownerId", "GetOwner")).isTrue();
}
```

In `AppFixture.java`, add the helper:

```java
boolean schemaLinkSubRowExists(String statusCode, String propertyName, String linkName) {
    // Look for a link row inside the schema-props grid that follows the property
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    return page.locator(panel + " .schema-props .schema-link-row")
            .filter(new Locator.FilterOptions().setHasText(linkName))
            .count() > 0;
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldShowBodyLinkAsSubRowUnderSourceProperty" 2>&1 | tail -20`

Expected: FAIL — no `.schema-link-row` elements exist yet.

- [ ] **Step 3: Write failing test — header link appears as sub-row under the source header**

```java
@Test void shouldShowHeaderLinkAsSubRowUnderSourceHeader() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.schemaLinkSubRowExists("200", "X-Request-Id", "GetPetByRequestId")).isTrue();
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldShowHeaderLinkAsSubRowUnderSourceHeader" 2>&1 | tail -20`

Expected: FAIL.

- [ ] **Step 5: Implement runtime expression parsing and link sub-row rendering**

In `OperationFragmentGenerator.java`, add a method to extract the source type and property name from a runtime expression:

```java
/** Parses "$response.body#/ownerId" → ["body", "ownerId"] or "$response.header.X-Foo" → ["header", "X-Foo"]. Returns null for unparseable expressions. */
private static String[] parseResponseSource(String expression) {
    if (expression == null) return null;
    if (expression.startsWith("$response.body#/")) {
        var pointer = expression.substring("$response.body#/".length());
        // For nested paths like "owner/id", take the last segment
        var lastSlash = pointer.lastIndexOf('/');
        var leaf = lastSlash >= 0 ? pointer.substring(lastSlash + 1) : pointer;
        return new String[]{"body", leaf};
    }
    if (expression.startsWith("$response.header.")) {
        return new String[]{"header", expression.substring("$response.header.".length())};
    }
    return null;
}
```

Add a method to collect links that source from a specific property in a specific section (body or header):

```java
/** Returns links whose parameters reference the given property in the given source type. */
private static Map<String, io.swagger.v3.oas.models.links.Link> linksForProperty(
        Map<String, io.swagger.v3.oas.models.links.Link> allLinks, String sourceType, String propertyName) {
    if (allLinks == null) return Map.of();
    var result = new LinkedHashMap<String, io.swagger.v3.oas.models.links.Link>();
    for (var entry : allLinks.entrySet()) {
        var link = entry.getValue();
        if (link.getParameters() == null) continue;
        for (var param : link.getParameters().values()) {
            var source = parseResponseSource(param.toString());
            if (source != null && source[0].equals(sourceType) && source[1].equals(propertyName)) {
                result.put(entry.getKey(), link);
                break;
            }
        }
    }
    return result;
}
```

Add a method to render a link sub-row element:

```java
private Element linkSubRow(String linkName, io.swagger.v3.oas.models.links.Link link, String contextSourceType, String contextPropertyName) {
    var row = span().classes("schema-link-row");
    var href = operationIdHref(link.getOperationId());
    var nameEl = href != null
            ? element("a").attr("href", href).content("→ " + linkName)
            : span("→ " + linkName);
    row.content(nameEl);
    if (link.getDescription() != null) {
        row.content(span(link.getDescription()).classes("schema-prop-desc"));
    }
    if (link.getParameters() != null) {
        for (var param : link.getParameters().entrySet()) {
            var expr = param.getValue().toString();
            var display = formatParamShort(expr, contextSourceType, contextPropertyName);
            row.content(span(param.getKey() + " ← " + display).classes("schema-link-param"));
        }
    }
    return row;
}
```

Add the short-form parameter formatter:

```java
/** Formats a runtime expression for display. Omits $response. prefix. If the source matches context, shows just the property name. Otherwise shows "header.Name" or "body.prop". */
private static String formatParamShort(String expression, String contextSourceType, String contextPropertyName) {
    var source = parseResponseSource(expression);
    if (source == null) return expression; // unparseable, show raw
    if (source[0].equals(contextSourceType)) return source[1]; // same source, just property name
    return source[0] + "." + source[1]; // cross-source
}
```

Modify `SchemaRenderer.addPropertyRow()` to accept and render link sub-rows. The `SchemaRenderer` needs access to links, so change it from a static inner class to a non-static inner class, or pass links as a parameter. The simplest approach: add a `Map<String, io.swagger.v3.oas.models.links.Link>` field to `SchemaRenderer`:

Change the class to non-static and add a `links` field:

```java
private class SchemaRenderer {
    private final Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<String, io.swagger.v3.oas.models.links.Link> links;

    SchemaRenderer(Map<String, io.swagger.v3.oas.models.links.Link> links) {
        this.links = links != null ? links : Map.of();
    }
```

In `addPropertyRow()`, after `table.content(details);` and before the nested-property block, add:

```java
// render link sub-rows for this property
var propertyLinks = linksForProperty(links, "body", name);
for (var linkEntry : propertyLinks.entrySet()) {
    table.content(span().classes("schema-prop-name")); // empty name column
    table.content(linkSubRow(linkEntry.getKey(), linkEntry.getValue(), "body", name));
}
```

Update the call site in `statusCodePanel()` — change `new SchemaRenderer().render(panel, mediaType.getSchema())` to:

```java
var links = response.getLinks();
new SchemaRenderer(links).render(panel, mediaType.getSchema());
```

Modify `schemaHeaders()` to accept and render header link sub-rows. Add a `links` parameter:

```java
private Element schemaHeaders(ApiResponse response, Map<String, io.swagger.v3.oas.models.links.Link> links) {
```

Inside the header loop, after `headerProps.content(nameEl, details);`, add:

```java
var headerLinks = linksForProperty(links, "header", entry.getKey());
for (var linkEntry : headerLinks.entrySet()) {
    headerProps.content(span().classes("schema-prop-name")); // empty name column
    headerProps.content(linkSubRow(linkEntry.getKey(), linkEntry.getValue(), "header", entry.getKey()));
}
```

Update the call in `statusCodePanel()`:

```java
if (responseHasHeaders) {
    if (responseHasBody) panel.content(span("Headers"));
    panel.content(schemaHeaders(response, response.getLinks()));
}
```

Remove the separate links section from `statusCodePanel()` — delete these lines:

```java
if (response.getLinks() != null && !response.getLinks().isEmpty()) {
    panel.content(responseLinks(response));
}
```

- [ ] **Step 6: Run the two new tests to verify they pass**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldShowBodyLinkAsSubRowUnderSourceProperty+shouldShowHeaderLinkAsSubRowUnderSourceHeader" 2>&1 | tail -20`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat: inline links as sub-rows under source properties"
```

---

### Task 3: Add CSS for Link Sub-Rows and Remove Old Link CSS

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`

- [ ] **Step 1: Write failing test — link sub-row is clickable**

In `BrowserTest.java`, `GivenAppWithResponseLinks`:

```java
@Test void shouldMakeInlineLinkNameClickable() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkCursor("200", "ownerId", "GetOwner")).isEqualTo("pointer");
}
```

In `AppFixture.java`:

```java
String inlineLinkCursor(String statusCode, String propertyName, String linkName) {
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    return page.locator(panel + " .schema-link-row a")
            .filter(new Locator.FilterOptions().setHasText(linkName))
            .first()
            .evaluate("el => getComputedStyle(el).cursor").toString();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldMakeInlineLinkNameClickable" 2>&1 | tail -20`

Expected: May pass already (links are `<a>` elements which default to pointer cursor). If it passes, that's fine — move on.

- [ ] **Step 3: Add minimal CSS for schema-link-row and remove old schema-link CSS**

In `app.css`, replace the entire `/* response links */` section (lines 416-465) with:

```css
/* inline link sub-rows */
.schema-link-row {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0.1875rem 0.375rem;
    font-size: 0.8125rem;
}
.schema-link-row a {
    color: var(--bulma-link);
    font-weight: 500;
    text-decoration: none;
}
.schema-link-row a:hover {
    text-decoration: underline;
}
.schema-link-param {
    font-family: var(--bulma-family-code);
    font-size: 0.8125rem;
    color: var(--bulma-text-weak);
}
```

This removes: `.schema-response-links`, `.schema-links-label`, `.schema-links`, `.schema-link-name`, `.schema-link-details`, `.schema-link-operation`, `.schema-link-desc`.

- [ ] **Step 4: Run all link tests**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks" 2>&1 | tail -30`

Expected: Some old tests will fail because they reference removed CSS classes. That's expected — we'll update them in Task 4.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "style: replace schema-link CSS with inline link sub-row styles"
```

---

### Task 4: Update Existing Tests and Fixtures for New Link Structure

The old tests reference CSS classes and structure that no longer exist. Update them to use the new inline structure.

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Update AppFixture — replace old link helpers with new ones**

Remove these methods from `AppFixture.java`:
- `hasResponseLinks()`
- `responseLinkNames()`
- `responseLinkOperationId()`
- `responseLinkDescription()`
- `responseLinkParams()`
- `clickSchemaLinkName()`
- `clickSchemaLinkOperation()`
- `schemaLinkHref()`
- `schemaLinkNameCursor()`
- `schemaLinkOperationCursor()`

Add replacement methods:

```java
/** Returns all link names found as inline sub-rows in the schema for the given status code. */
List<String> inlineLinkNames(String statusCode) {
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    return page.locator(panel + " .schema-link-row a").allTextContents().stream()
            .map(t -> t.replaceFirst("^→ ", ""))
            .toList();
}

/** Returns the description text of an inline link sub-row. */
String inlineLinkDescription(String statusCode, String linkName) {
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    var row = page.locator(panel + " .schema-link-row")
            .filter(new Locator.FilterOptions().setHasText(linkName));
    return row.locator(".schema-prop-desc").textContent();
}

/** Returns all parameter mapping texts of an inline link sub-row. */
List<String> inlineLinkParams(String statusCode, String linkName) {
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    var row = page.locator(panel + " .schema-link-row")
            .filter(new Locator.FilterOptions().setHasText(linkName));
    return row.locator(".schema-link-param").allTextContents();
}

/** Returns the href of an inline link. */
String inlineLinkHref(String statusCode, String linkName) {
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    return page.locator(panel + " .schema-link-row a")
            .filter(new Locator.FilterOptions().setHasText(linkName))
            .first()
            .getAttribute("href");
}

/** Clicks an inline link by name. */
void clickInlineLink(String statusCode, String linkName) {
    var panel = "#detail .schema-status-panel[data-status='" + statusCode + "']";
    page.locator(panel + " .schema-link-row a")
            .filter(new Locator.FilterOptions().setHasText(linkName))
            .first()
            .click();
}
```

- [ ] **Step 2: Rewrite the existing tests in GivenAppWithResponseLinks**

Replace the old test methods with updated versions:

```java
@Test void shouldShowLinksInlineInSchema() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkNames("200")).contains("GetOwner");
}

@Test void shouldShowLinkDescription() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkDescription("200", "GetOwner")).isEqualTo("Get the owner of this pet");
}

@Test void shouldShowLinkParameters() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkParams("200", "GetOwner")).containsExactly("ownerId ← ownerId");
}

@Test void shouldNotShowLinksWhenNoLinks() {
    app.expandFirstNode();
    app.clickTreeNode("pets/index.html");
    app.waitForDetailContent("List pets");
    app.toggleSchema("response");
    then(app.inlineLinkNames("200")).isEmpty();
}

@Test void shouldHaveHrefOnInlineLink() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkHref("200", "GetOwner")).contains("#owners/{ownerId}/GET");
}

@Test void shouldNavigateToTargetOperationWhenClickingInlineLink() {
    navigateToPetDetail();
    app.toggleSchema("response");
    app.clickInlineLink("200", "GetOwner");
    app.waitForDetailContent("Get an owner");
}

@Test void shouldShowInlineLinkAsClickable() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkCursor("200", "ownerId", "GetOwner")).isEqualTo("pointer");
}
```

Remove the old tests: `shouldShowLinksSection`, `shouldShowLinkNames`, `shouldShowLinkOperationId`, `shouldShowLinkDescription`, `shouldShowLinkParameters`, `shouldNotShowLinksSectionWhenNoLinks`, `shouldHaveHrefOnSchemaLinkNames`, `shouldNavigateToTargetOperationWhenClickingSchemaLinkName`, `shouldRenderSchemaLinkNameAsRealLink`, `shouldShowSchemaLinksAsClickable`.

Keep unchanged: `shouldAddOperationIdToForm`, `shouldEmbedResponseLinksDataOnForm`, `shouldFillFieldFromHashQueryParameter`, `shouldIgnoreUnknownHashQueryParameters`, `shouldTakeScreenshotOfResponseLinks`.

- [ ] **Step 3: Run all link tests**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks+BrowserTest\$GivenAppWithResponseBodyLinks" 2>&1 | tail -30`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "test: update link tests for inline sub-row structure"
```

---

### Task 5: Remove responseLinks() Method and Clean Up

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java`

- [ ] **Step 1: Delete the `responseLinks()` method**

Remove the entire `responseLinks()` method (the one that creates `div.schema-response-links`). It should no longer be called from anywhere after Task 2's changes.

- [ ] **Step 2: Run full test suite**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true 2>&1 | tail -30`

Expected: PASS. All tests pass with the method removed.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "refactor: remove unused responseLinks() method"
```

---

### Task 6: Add Header Link Tests

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Write test — header-sourced link shows under header**

In `GivenAppWithResponseLinks`:

```java
@Test void shouldShowHeaderLinkInHeadersSection() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.schemaLinkSubRowExists("200", "X-Request-Id", "GetPetByRequestId")).isTrue();
}

@Test void shouldShowHeaderLinkDescription() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkDescription("200", "GetPetByRequestId")).isEqualTo("Look up by request ID");
}

@Test void shouldShowHeaderLinkParams() {
    navigateToPetDetail();
    app.toggleSchema("response");
    then(app.inlineLinkParams("200", "GetPetByRequestId")).containsExactly("requestId ← X-Request-Id");
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldShowHeaderLinkInHeadersSection+shouldShowHeaderLinkDescription+shouldShowHeaderLinkParams" 2>&1 | tail -20`

Expected: PASS (implementation was done in Task 2).

- [ ] **Step 3: Write test — header link navigates to target**

```java
@Test void shouldNavigateWhenClickingHeaderLink() {
    navigateToPetDetail();
    app.toggleSchema("response");
    app.clickInlineLink("200", "GetPetByRequestId");
    app.waitForDetailContent("Get pet by request ID");
}
```

- [ ] **Step 4: Run test**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "test: add header-sourced link tests"
```

---

### Task 7: Review Screenshots and Run Full Suite

**Files:**
- No code changes expected (unless screenshots reveal visual issues)

- [ ] **Step 1: Run full test suite to generate fresh screenshots**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true 2>&1 | tail -20`

Expected: All tests pass.

- [ ] **Step 2: Review screenshots**

Check `core/target/screenshots/response-links.png` and `response-links-dark.png`. Verify:
- No separate "Links" section with separator line
- GetOwner link appears as a sub-row under `ownerId` in the body schema
- GetPetByRequestId link appears as a sub-row under `X-Request-Id` in the headers
- Link sub-rows align within the `schema-props` grid
- Both light and dark mode look correct

Check `core/target/screenshots/response-body-links.png` — body links in the try-it response should be unaffected.

- [ ] **Step 3: Run the full Maven build (all modules)**

Run: `mvn test 2>&1 | tail -20`

Expected: All modules pass.

- [ ] **Step 4: Squash all commits into one**

```bash
git rebase -i origin/trunk
# squash all task commits into one
```

Final commit message: `feat: inline schema links as sub-rows under source properties (#20)`

- [ ] **Step 5: Close issue #20**

```bash
gh issue close 20
```
