# x-links for nested/embedded objects — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support `x-links` OpenAPI extension for linking array items in response bodies to their detail operations.

**Architecture:** The `x-links` extension on response objects uses the same Link Object structure as standard `links`, but parameter expressions support `[*]` as an array wildcard. The Java generator reads `x-links` from response extensions and merges them with standard links for schema rendering and `data-response-links` serialization. The JS resolver expands `[*]` expressions against the actual JSON response body, producing indexed pointer map entries that the existing rendering logic picks up.

**Tech Stack:** Java 21, swagger-parser (ApiResponse extensions), bulma-java, vanilla JS, Playwright browser tests, MicroProfile OpenAPI annotations (@Extension).

---

### Task 1: Extend test YAML fixture with x-links

Add `x-links` to the existing `response-links.yaml` test fixture on the `GET /pets/{petId}` response. This adds array items to the schema and declares x-links that reference them.

**Files:**
- Modify: `core/src/test/resources/response-links.yaml`

- [ ] **Step 1: Add visits array and x-links to the test fixture**

Add a `visits` array property to the `GET /pets/{petId}` response schema, and add `x-links` alongside the existing `links`. Also add a `GET /pets/{petId}/visits/{visitId}` target operation.

In `core/src/test/resources/response-links.yaml`, after the existing `links` block on the `GET /pets/{petId}` response (after line 80), add the `x-links` extension. Also extend the response schema to include a `visits` array with items that have `id` and `reason` properties.

The updated `GET /pets/{petId}` path should have this schema:

```yaml
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  name:
                    type: string
                  owner:
                    type: object
                    properties:
                      id:
                        type: integer
                      name:
                        type: string
                  visits:
                    type: array
                    items:
                      type: object
                      properties:
                        id:
                          type: integer
                        reason:
                          type: string
```

And after the existing `links` block, add:

```yaml
          x-links:
            GetVisitDetail:
              operationId: getVisitDetail
              parameters:
                visitId: $response.body#/visits[*]/id
              description: Get details of this visit
```

Add a new target operation at the end of the paths:

```yaml
  /pets/{petId}/visits/{visitId}:
    get:
      summary: Get a visit
      operationId: getVisitDetail
      parameters:
        - name: petId
          in: path
          required: true
          schema:
            type: integer
        - name: visitId
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: A visit
          content:
            application/json:
              schema:
                type: object
                properties:
                  id:
                    type: integer
                  reason:
                    type: string
```

- [ ] **Step 2: Run existing tests to confirm fixture changes don't break anything**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks,BrowserTest\$GivenAppWithResponseBodyLinks" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: All existing tests pass. The new `x-links` and `visits` schema are ignored by the current code (extensions are not read yet).

- [ ] **Step 3: Commit**

```
git add core/src/test/resources/response-links.yaml
git commit -m "test: add x-links and visits array to response-links fixture (#19)"
```

---

### Task 2: Read x-links from response extensions (Java)

Teach `OperationFragmentGenerator` to read `x-links` from `response.getExtensions()` and merge them with standard `links` so they flow into schema rendering and `data-response-links` serialization.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java`
- Test: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [ ] **Step 1: Write failing browser test for x-links in schema view**

In `BrowserTest.java`, inside the existing `GivenAppWithResponseLinks` nested class (which uses `response-links.yaml`), add a test that verifies x-links render in the schema view. The test should check that the `visits` array items have a link row showing `→ GetVisitDetail`.

Add this test inside the `GivenAppWithResponseLinks` class:

```java
@Test void shouldShowXLinksInSchemaView() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");
    app.toggleSchema("response");
    app.expandNestedSchema("visits");

    then(app.schemaLinkSubRowExists("200", "GetVisitDetail")).isTrue();
}
```

This uses the existing `schemaLinkSubRowExists(statusCode, linkName)` helper in `AppFixture` (line 492), which checks for a `.schema-link-row` element containing the link name text. The test expands the `visits` nested schema first (since array item properties are collapsed by default), then verifies the `GetVisitDetail` link row appears.

- [ ] **Step 2: Run the test to see it fail**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldShowXLinksInSchemaView" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: FAIL — `x-links` are not read from extensions yet, so no link row appears for `visits[*]/id`.

- [ ] **Step 3: Implement x-links reading and merging**

In `OperationFragmentGenerator.java`, create a helper method that merges standard `links` with `x-links` from extensions:

```java
/**
 * Merges standard response links with x-links from response extensions.
 * x-links use the same Link Object structure but support [*] array wildcards in body expressions.
 */
@SuppressWarnings("unchecked")
private static Map<String, io.swagger.v3.oas.models.links.Link> allLinks(ApiResponse response) {
    var result = new LinkedHashMap<String, io.swagger.v3.oas.models.links.Link>();
    if (response.getLinks() != null) result.putAll(response.getLinks());
    if (response.getExtensions() != null) {
        var xLinks = response.getExtensions().get("x-links");
        if (xLinks instanceof Map<?, ?> xLinksMap) {
            for (var entry : ((Map<String, Object>) xLinksMap).entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> linkMap) {
                    var link = new io.swagger.v3.oas.models.links.Link();
                    var map = (Map<String, Object>) linkMap;
                    link.setOperationId((String) map.get("operationId"));
                    link.setDescription((String) map.get("description"));
                    if (map.get("parameters") instanceof Map<?, ?> params) {
                        var paramMap = new LinkedHashMap<String, String>();
                        for (var p : ((Map<String, Object>) params).entrySet()) {
                            paramMap.put(p.getKey(), String.valueOf(p.getValue()));
                        }
                        link.setParameters(paramMap);
                    }
                    result.put(entry.getKey(), link);
                }
            }
        }
    }
    return result;
}
```

Then replace all uses of `response.getLinks()` with `allLinks(response)`:

1. In `responseLinksData()` (line 120): change `if (response.getLinks() == null || response.getLinks().isEmpty()) continue;` and `for (var linkEntry : response.getLinks().entrySet())` to use `allLinks(response)`.

2. In `statusCodePanel()` (lines 499 and 504): change `response.getLinks()` to `allLinks(response)`.

3. In `parseResponseSource()` (line 513): strip `[*]` from body expressions so that `$response.body#/visits[*]/id` becomes path `visits/id`, which matches the schema property path `visits/id`.

Update `parseResponseSource`:

```java
private static String[] parseResponseSource(String expression) {
    if (expression == null) return null;
    if (expression.startsWith("$response.body#/")) {
        var path = expression.substring("$response.body#/".length());
        path = path.replace("[*]", ""); // strip array wildcards for schema matching
        return new String[]{"body", path};
    }
    if (expression.startsWith("$response.header.")) {
        return new String[]{"header", expression.substring("$response.header.".length())};
    }
    return null;
}
```

Note: `replace("[*]", "")` turns `visits[*]/id` into `visits/id` which matches the schema path. For `visits[*]/veterinarian/id` it produces `visits/veterinarian/id` — also correct since the schema renderer walks through array items transparently.

- [ ] **Step 4: Run the test to see it pass**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks#shouldShowXLinksInSchemaView" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: PASS

- [ ] **Step 5: Run all existing link tests to confirm no regressions**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks,BrowserTest\$GivenAppWithResponseBodyLinks" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: All pass.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "feat: read x-links from response extensions for schema view (#19)"
```

---

### Task 3: Expand [*] expressions in JS body link resolver

Extend `applyBodyLinks()` in `app.js` to handle `[*]` wildcard segments. When a pointer segment is `[*]` and the current value is an array, iterate all elements and produce indexed pointer map entries.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Test: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [ ] **Step 1: Write failing browser test for x-links body badges**

In `BrowserTest.java`, add a new nested class for x-links body link tests:

```java
@ResourceLock("x-links-body") @Nested class GivenAppWithXLinksBodyLinks {
    @RegisterExtension static AppFixture app = launch("response-links.yaml").withBaseUrlOverride();

    private void navigateToPetDetailAndSendWithVisits() {
        app.expandFirstNode();
        app.clickTreeNode("pets/{petId}/index.html");
        app.waitForDetailContent("Get a pet");
        app.fillInput("petId", "42");
        app.mockEndpoint("/pets/42", "application/json",
                "{\"id\":42,\"name\":\"Buddy\",\"owner\":{\"id\":7,\"name\":\"Alice\"},"
                + "\"visits\":[{\"id\":10,\"reason\":\"Checkup\"},{\"id\":11,\"reason\":\"Vaccination\"}]}");
        app.clickSend();
        app.waitForResponse();
    }

    @Test void shouldShowBodyLinkBadgesOnArrayItems() {
        navigateToPetDetailAndSendWithVisits();

        // Standard links: id→GetVisits, id→GetPetAgain, owner/id→GetOwner = 3
        // x-links: visits/0/id→GetVisitDetail, visits/1/id→GetVisitDetail = 2
        // Total = 5
        then(app.bodyLinkCount()).isEqualTo(5);
    }

    @Test void shouldShowXLinkBadgeWithCorrectName() {
        navigateToPetDetailAndSendWithVisits();

        var badgeTexts = IntStream.range(0, app.bodyLinkCount())
                .mapToObj(app::bodyLinkText)
                .toList();
        then(badgeTexts).contains("GetVisitDetail");
    }

    @Test void shouldFillParameterWhenClickingXLinkBadge() {
        navigateToPetDetailAndSendWithVisits();

        // Find the GetVisitDetail badge and click it
        var xLinkIndex = IntStream.range(0, app.bodyLinkCount())
                .filter(i -> "GetVisitDetail".equals(app.bodyLinkText(i)))
                .findFirst().orElseThrow();
        app.clickBodyLink(xLinkIndex);
        app.waitForDetailContent("Get a visit");
        app.waitForInputValue("visitId", "10");

        then(app.inputValue("visitId")).isEqualTo("10");
    }
}
```

- [ ] **Step 2: Run the test to see it fail**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithXLinksBodyLinks#shouldShowBodyLinkBadgesOnArrayItems" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: FAIL — the JS resolver doesn't handle `[*]` yet, so `visits[*]/id` won't resolve against the JSON body.

- [ ] **Step 3: Implement [*] expansion in applyBodyLinks**

In `app.js`, replace the pointer resolution logic in `applyBodyLinks()` (lines 807-814) with a recursive function that handles `[*]`:

Replace the current pointer resolution block:

```javascript
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
                    value: String(val),
                    linkName: linkName
                });
```

With:

```javascript
                var pointer = match[1];
                if (pointer.indexOf('[*]') >= 0) {
                    // Expand wildcard expressions into indexed pointer entries
                    expandWildcardPointer(pointer.split('/'), 0, parsed, [], pointerMap, link, paramName, linkName);
                } else {
                    // Standard pointer resolution (no wildcards)
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
                        value: String(val),
                        linkName: linkName
                    });
                }
```

Add the `expandWildcardPointer` function after `applyBodyLinks`.

Note: `$response.body#/visits[*]/id` → pointer is `visits[*]/id` → split by `/` gives `["visits[*]", "id"]`. The segment `visits[*]` means "access property `visits`, then iterate". So `[*]` is a suffix on a segment, not a standalone segment:

```javascript
    function expandWildcardPointer(segments, segIdx, current, pathSoFar, pointerMap, link, paramName, linkName) {
        if (current === undefined || current === null) return;
        if (segIdx >= segments.length) {
            var pointer = pathSoFar.join('/');
            if (!pointerMap[pointer]) pointerMap[pointer] = [];
            pointerMap[pointer].push({
                operationId: link.operationId,
                paramName: paramName,
                value: String(current),
                linkName: linkName
            });
            return;
        }
        var seg = segments[segIdx];
        if (seg.endsWith('[*]')) {
            // Property access + array wildcard: e.g. "visits[*]"
            var prop = seg.substring(0, seg.length - 3);
            var arr = current[prop];
            if (!Array.isArray(arr)) return;
            for (var i = 0; i < arr.length; i++) {
                expandWildcardPointer(segments, segIdx + 1, arr[i], pathSoFar.concat(prop, String(i)), pointerMap, link, paramName, linkName);
            }
        } else {
            // Regular property access
            expandWildcardPointer(segments, segIdx + 1, current[seg], pathSoFar.concat(seg), pointerMap, link, paramName, linkName);
        }
    }
```

This correctly handles `visits[*]/id`:
1. seg=`visits[*]`: access `current["visits"]` (the array), then for each element `i`, recurse with path `["visits", "0"]` / `["visits", "1"]`
2. seg=`id`: access `current["id"]`, recurse with path `["visits", "0", "id"]` / `["visits", "1", "id"]`
3. End: create `pointerMap["visits/0/id"]` and `pointerMap["visits/1/id"]`

This matches what `renderJsonWithLinks` builds: `path.concat(String(i))` for arrays (line 856), `path.concat(key)` for object keys (line 866).

- [ ] **Step 4: Run the test to see it pass**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithXLinksBodyLinks" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: All three tests pass.

- [ ] **Step 5: Run all link tests to confirm no regressions**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseLinks,BrowserTest\$GivenAppWithResponseBodyLinks,BrowserTest\$GivenAppWithXLinksBodyLinks" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: All pass.

- [ ] **Step 6: Commit**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "feat: expand [*] wildcard in x-links body expressions (#19)"
```

---

### Task 4: Add deeper nesting test (multi-level [*])

Verify that multiple `[*]` segments work for nested arrays (e.g., `visits[*]/treatments[*]/id`).

**Files:**
- Modify: `core/src/test/resources/response-links.yaml`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [ ] **Step 1: Extend test fixture with nested array and multi-level x-link**

In `response-links.yaml`, add a `treatments` array inside visit items, and add a second x-link:

In the `visits` array items schema, add:

```yaml
                        treatments:
                          type: array
                          items:
                            type: object
                            properties:
                              id:
                                type: integer
                              name:
                                type: string
```

Add another x-link:

```yaml
            GetTreatment:
              operationId: getTreatment
              parameters:
                treatmentId: $response.body#/visits[*]/treatments[*]/id
              description: Get treatment details
```

Add a target operation:

```yaml
  /treatments/{treatmentId}:
    get:
      summary: Get a treatment
      operationId: getTreatment
      parameters:
        - name: treatmentId
          in: path
          required: true
          schema:
            type: integer
      responses:
        '200':
          description: A treatment
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

- [ ] **Step 2: Write failing test for multi-level wildcard body links**

In the `GivenAppWithXLinksBodyLinks` class in `BrowserTest.java`, add:

```java
@Test void shouldExpandMultiLevelWildcards() {
    app.expandFirstNode();
    app.clickTreeNode("pets/{petId}/index.html");
    app.waitForDetailContent("Get a pet");
    app.fillInput("petId", "42");
    app.mockEndpoint("/pets/42", "application/json",
            "{\"id\":42,\"name\":\"Buddy\",\"owner\":{\"id\":7,\"name\":\"Alice\"},"
            + "\"visits\":[{\"id\":10,\"reason\":\"Checkup\","
            + "\"treatments\":[{\"id\":100,\"name\":\"Antibiotics\"},{\"id\":101,\"name\":\"Painkillers\"}]}]}");
    app.clickSend();
    app.waitForResponse();

    var badgeTexts = IntStream.range(0, app.bodyLinkCount())
            .mapToObj(app::bodyLinkText)
            .toList();
    // Standard: GetVisits, GetPetAgain on id=42; GetOwner on owner/id=7 (3)
    // x-links: GetVisitDetail on visits/0/id=10 (1)
    // x-links: GetTreatment on visits/0/treatments/0/id=100, visits/0/treatments/1/id=101 (2)
    // Total = 6
    then(app.bodyLinkCount()).isEqualTo(6);
    then(badgeTexts).contains("GetTreatment");
}
```

- [ ] **Step 3: Run the test to see it pass (should already work with existing implementation)**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithXLinksBodyLinks#shouldExpandMultiLevelWildcards" -Dplaywright.launch.headless=true` (timeout 60000)

Expected: PASS — the recursive `expandWildcardPointer` already handles multiple `[*]` segments.

- [ ] **Step 4: Commit**

```
git add core/src/test/resources/response-links.yaml core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "test: verify multi-level [*] wildcard expansion (#19)"
```

---

### Task 5: Demo app — add x-links to PetResource

Add `x-links` to the demo app's `GET /pets/{id}` response so that embedded visits become clickable.

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`

- [ ] **Step 1: Add @Extension for x-links on PetResource**

In `PetResource.java`, add an `@Extension` annotation to the `@APIResponse` on `GET /{id}` (line 76). The extension declares an x-link from each visit's id to the `getVisit` operation.

Add the import:

```java
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
```

Then add the `extensions` attribute to the `@APIResponse(responseCode = "200", ...)` annotation:

```java
@APIResponse(responseCode = "200", description = "A pet",
        headers = @Header(name = "X-Request-ID", description = "Echoed request identifier",
                schema = @Schema(type = SchemaType.STRING)),
        content = {@Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = PetResponse.class)),
                @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = PetResponse.class))},
        links = {
                @Link(name = "owner", operationId = "getOwner",
                        description = "Get the owner of this pet",
                        parameters = @LinkParameter(name = "id", expression = "$response.body#/owner/id")),
                @Link(name = "visits", operationId = "listPetVisits",
                        description = "List visits for this pet",
                        parameters = @LinkParameter(name = "petId", expression = "$response.body#/id"))},
        extensions = @Extension(name = "x-links", parseValue = true,
                value = "{\"visitDetail\":{\"operationId\":\"getVisit\","
                        + "\"description\":\"Get details of this visit\","
                        + "\"parameters\":{\"visitId\":\"$response.body#/visits[*]/id\"}}}"))
```

- [ ] **Step 2: Build the demo app and verify x-links appear in generated OpenAPI**

Run: `mvn package -pl demo -am -DskipTests` (timeout 60000)

Then check the generated OpenAPI spec:

Run: `grep -A5 "x-links" demo/target/classes/META-INF/openapi.yaml` or check the output contains the x-links extension.

Expected: The `x-links` extension appears on the 200 response of `GET /pets/{id}`.

- [ ] **Step 3: Run demo app tests if any exist, otherwise run full test suite**

Run: `mvn test` (timeout 60000)

Expected: All pass.

- [ ] **Step 4: Commit**

```
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java
git commit -m "feat: add x-links to demo PetResource for visit details (#19)"
```

---

### Task 6: Update README documentation

Document the `x-links` extension in the README.

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add x-links documentation to README**

In `README.md`, in the **API Documentation** section under Features (after the bullet about collapsible nested object properties, around line 59), add a bullet:

```markdown
- `x-links` extension for array item links: when a response embeds an array of sub-resources
  (e.g., a pet's visits), `x-links` lets you declare per-item links using `[*]` as an array
  wildcard in the parameter expression (e.g., `$response.body#/visits[*]/id`). These render
  as clickable badges on each array element in the response body, and as link rows in the
  schema view — same visual treatment as standard OpenAPI Links.
```

- [ ] **Step 2: Add x-links details section**

Add a new collapsible details section after the existing "Nested schema expanded" details block (after line 84):

```markdown
<details>
<summary>x-links for array items</summary>

Standard OpenAPI Links can't express "for each item in an array, link to its detail operation"
because JSON Pointer has no wildcard syntax. The `x-links` extension uses the same Link Object
structure but supports `[*]` in body expressions:

```yaml
responses:
  200:
    links:
      GetOwner:
        operationId: getOwner
        parameters:
          ownerId: $response.body#/owner/id
    x-links:
      GetVisit:
        operationId: getVisit
        parameters:
          visitId: $response.body#/visits[*]/id
```

After "Try it out", each visit's `id` in the response body gets a clickable `→ GetVisit` badge
that navigates to the target operation and fills in the parameter. Multiple `[*]` segments are
supported for nested arrays (e.g., `$response.body#/visits[*]/treatments[*]/id`).
</details>
```

- [ ] **Step 3: Commit**

```
git add README.md
git commit -m "docs: document x-links extension for array item links (#19)"
```

---

### Task 7: Screenshot review and full validation

Run the full test suite, review screenshots, and squash commits.

**Files:**
- No new files

- [ ] **Step 1: Run full test suite**

Run: `mvn test` (timeout 60000)

Expected: All pass.

- [ ] **Step 2: Review screenshots**

Check `core/target/screenshots/` for any screenshots that show x-links (response-body-links screenshots). Verify:
- Body link badges appear on array item values
- Styling is consistent with existing badges
- Both light and dark mode look correct

- [ ] **Step 3: Squash commits and push**

Squash all commits from this plan into one:

```
git rebase -i HEAD~N  # where N is the number of commits from this plan
```

Squash into: `feat: x-links extension for array item links (#19)`

Then push: `git push`
