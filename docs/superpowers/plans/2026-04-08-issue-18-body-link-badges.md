# Issue #18: Body Link Badges — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace clickable JSON values with clickable named badges that show the OpenAPI link name.

**Architecture:** Change rendering in `app.js` to output a plain value followed by badge `<a>` elements with the link name. Update `.body-link` CSS for badge styling. Pass `linkName` through the pointer map. Update all existing body link browser tests. Add demo app links across all resources.

**Tech Stack:** JavaScript (app.js), CSS (app.css), Java (demo annotations, browser tests)

---

### Task 1: Render body links as named badges

Change the JS rendering so that values are plain and badges with link names appear after them. Update CSS. Update all existing tests to match.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:798-883`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css:438-455`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java:2468-2567`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java:504-532`

- [ ] **Step 1: Write failing test — badge shows link name**

In `BrowserTest.java`, in the `GivenAppWithResponseBodyLinks` class, add:

```java
@Test void shouldShowLinkNameInBadge() {
    navigateToPetDetailAndSend();

    then(app.bodyLinkText(0)).isEqualTo("GetOwner");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks#shouldShowLinkNameInBadge" -DdangerouslyDisableSandbox=true`
Expected: FAIL — body link text is currently `"7"` (the value), not `"GetOwner"`.

- [ ] **Step 3: Implement badge rendering in app.js**

In `app.js`, in the `applyBodyLinks` function (line ~816), add `linkName` to the pushed object:

```javascript
pointerMap[pointer].push({
    operationId: link.operationId,
    paramName: paramName,
    value: String(val),
    linkName: linkName
});
```

Replace the `renderLinkedValue` function (lines 875-883) with `renderLinkBadge`:

```javascript
function renderLinkBadge(entry) {
    var target = window._operationIdMap ? window._operationIdMap[entry.operationId] : null;
    if (!target) return '';
    var href = '#' + target.path + '/' + target.method + '?' + encodeURIComponent(entry.paramName) + '=' + encodeURIComponent(entry.value);
    return ' <a class="body-link" href="' + escapeHtml(href) + '">' + escapeHtml(entry.linkName) + '</a>';
}
```

In `renderJsonWithLinks`, replace the number branch (lines ~833-839):

```javascript
if (typeof value === 'number') {
    var pointer = path.join('/');
    var entries = pointerMap[pointer];
    var valueHtml = '<span class="hljs-number">' + value + '</span>';
    if (entries && entries.length > 0) {
        return valueHtml + entries.map(renderLinkBadge).join('');
    }
    return valueHtml;
}
```

Replace the string branch (lines ~841-848):

```javascript
if (typeof value === 'string') {
    var pointer = path.join('/');
    var entries = pointerMap[pointer];
    var escaped = escapeHtml(value);
    var valueHtml = '<span class="hljs-string">"' + escaped + '"</span>';
    if (entries && entries.length > 0) {
        return valueHtml + entries.map(renderLinkBadge).join('');
    }
    return valueHtml;
}
```

- [ ] **Step 4: Update .body-link CSS for badge styling**

Replace the `.body-link` styles in `app.css` (lines 438-455):

```css
.body-link {
    color: var(--bulma-link);
    text-decoration: none;
    cursor: pointer;
    font-size: 0.75em;
    border: 1px solid var(--bulma-link);
    border-radius: 3px;
    padding: 0 4px;
    margin-left: 4px;
    vertical-align: middle;
}
.body-link:hover {
    background: var(--bulma-link);
    color: var(--bulma-link-invert);
}
```

Remove the `.body-link::after` pseudo-element entirely.

- [ ] **Step 5: Update existing tests**

Update `shouldWrapMatchingJsonValuesAsBodyLinks`:

```java
@Test void shouldWrapMatchingJsonValuesAsBodyLinks() {
    navigateToPetDetailAndSend();

    then(app.bodyLinkCount()).isEqualTo(1);
    then(app.bodyLinkText(0)).isEqualTo("GetOwner");
    then(app.bodyLinkHref(0)).contains("ownerId=7");
}
```

Remove these obsolete tests:
- `shouldHaveBoldFontWeightForBetterVisibility`
- `shouldShowExternalLinkIconAfterValue`

Remove `bodyLinkFontWeight()` and `bodyLinkAfterContent()` from `AppFixture.java`.

Add `bodyLinkBorderStyle()` to `AppFixture.java`:

```java
String bodyLinkBorderStyle(int index) {
    return page.locator("#detail pre.response .body-link").nth(index).evaluate("el => getComputedStyle(el).borderStyle").toString();
}
```

Add a badge style test:

```java
@Test void shouldStyleBadgeWithBorder() {
    navigateToPetDetailAndSend();

    then(app.bodyLinkBorderStyle(0)).isEqualTo("solid");
}
```

- [ ] **Step 6: Run all body link tests**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks" -DdangerouslyDisableSandbox=true`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.css
git add core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git add core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java
git commit -m "feat: render body links as named badges instead of clickable values"
```

### Task 2: Test multiple badges on the same field

**Files:**
- Modify: `core/src/test/resources/response-links.yaml`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`

- [ ] **Step 1: Add two links referencing the same field in the test fixture**

In `response-links.yaml`, add two links to `GET /pets/{petId}` that both use `$response.body#/id` (after the existing `GetPetByRequestId` link, line ~69):

```yaml
            GetVisits:
              operationId: getVisits
              parameters:
                petId: $response.body#/id
              description: List visits for this pet
            GetPetAgain:
              operationId: getPet
              parameters:
                petId: $response.body#/id
              description: Reload this pet
```

- [ ] **Step 2: Write test for multiple badges**

In `GivenAppWithResponseBodyLinks`, add:

```java
@Test void shouldShowMultipleBadgesOnSameField() {
    navigateToPetDetailAndSend();

    var badgeTexts = IntStream.range(0, app.bodyLinkCount())
            .mapToObj(app::bodyLinkText)
            .toList();
    then(badgeTexts).contains("GetVisits", "GetPetAgain");
}
```

Add `import java.util.stream.IntStream;` if not already present.

- [ ] **Step 3: Update existing tests for new badge count**

With the added links, mock responses now produce 3 badges (GetOwner on `owner/id`, GetVisits and GetPetAgain on `id`). Update these tests:

`shouldWrapMatchingJsonValuesAsBodyLinks`:
```java
then(app.bodyLinkCount()).isEqualTo(3);
```

`shouldLinkCorrectFieldWhenKeysAreDuplicated` — the mock adds an `extra.id` field. With the new links, `id` gets 2 badges and `owner.id` gets 1 badge, `extra.id` gets none:
```java
then(app.bodyLinkCount()).isEqualTo(3);
```

`shouldNotWrapNonMatchingJsonValues` — same response, same count:
```java
then(app.bodyLinkCount()).isEqualTo(3);
```

`shouldHandleNestedJsonWithMultipleIdFields` — same:
```java
then(app.bodyLinkCount()).isEqualTo(3);
then(app.bodyLinkHref(0)).contains("petId=1");
```

- [ ] **Step 4: Run all body link tests**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithResponseBodyLinks" -DdangerouslyDisableSandbox=true`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/test/resources/response-links.yaml
git add core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java
git commit -m "test: verify multiple badges on the same JSON field"
```

### Task 3: Add demo app links

**Files:**
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerResource.java`
- Modify: `demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java`

- [ ] **Step 1: Add `visits` link to PetResource and rename `GetOwner` to `owner`**

In `PetResource.java`, change the `links` annotation on the `get()` method `@APIResponse` (lines 81-83) from a single `@Link` to an array:

```java
            links = {
                    @Link(name = "owner", operationId = "getOwner",
                            description = "Get the owner of this pet",
                            parameters = @LinkParameter(name = "id", expression = "$response.body#/owner/id")),
                    @Link(name = "visits", operationId = "listPetVisits",
                            description = "List visits for this pet",
                            parameters = @LinkParameter(name = "petId", expression = "$response.body#/id"))})
```

Note: `links` becomes an array `{...}`. The closing `})` closes both the array and `@APIResponse`.

- [ ] **Step 2: Add `pets` link to OwnerResource**

Add imports:

```java
import org.eclipse.microprofile.openapi.annotations.links.Link;
import org.eclipse.microprofile.openapi.annotations.links.LinkParameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
```

Add `operationId` to the `listPets` method:

```java
@GET @Path("/{ownerId}/pets") @Operation(operationId = "listOwnerPets", summary = "List pets for an owner")
```

Add `@APIResponse` with link on the `get()` method:

```java
@GET @Path("/{id}")
@Operation(operationId = "getOwner", summary = "Get an owner by ID")
@APIResponse(responseCode = "200", description = "An owner",
        links = @Link(name = "pets", operationId = "listOwnerPets",
                description = "List pets owned by this person",
                parameters = @LinkParameter(name = "ownerId", expression = "$response.body#/id")))
```

- [ ] **Step 3: Add `pet` link to VisitResource**

Add imports:

```java
import org.eclipse.microprofile.openapi.annotations.links.Link;
import org.eclipse.microprofile.openapi.annotations.links.LinkParameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
```

Add `operationId` to the single-visit GET and a `@APIResponse` with link:

```java
@GET @Path("/{visitId}") @Tag(name = "visits") @Tag(name = "pets")
@Operation(operationId = "getVisit", summary = "Get a visit by ID")
@APIResponse(responseCode = "200", description = "A visit",
        links = @Link(name = "pet", operationId = "getPet",
                description = "The pet for this visit",
                parameters = @LinkParameter(name = "id", expression = "$response.body#/petId")))
```

- [ ] **Step 4: Build demo to verify annotations compile**

Run: `mvn compile -pl demo -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add demo/src/main/java/com/github/t1/openapi/ui/demo/PetResource.java
git add demo/src/main/java/com/github/t1/openapi/ui/demo/OwnerResource.java
git add demo/src/main/java/com/github/t1/openapi/ui/demo/VisitResource.java
git commit -m "feat: add response links across demo app resources"
```

### Task 4: Final validation

- [ ] **Step 1: Run full core test suite**

Run: `mvn test -pl core -DdangerouslyDisableSandbox=true`
Expected: ALL PASS (60s timeout)

- [ ] **Step 2: Review screenshots**

Check `core/target/screenshots/` for the body links screenshot (both light and dark mode). Verify:
- Values are plain (not styled as links)
- Badges appear after values with border and link color
- Badge text shows the OpenAPI link name

- [ ] **Step 3: Run full project build**

Run: `mvn test -DdangerouslyDisableSandbox=true`
Expected: ALL PASS

- [ ] **Step 4: Squash into single commit**

```bash
git reset --soft <commit-before-task-1>
git commit -m "feat: render body links as named badges (#18)"
```
