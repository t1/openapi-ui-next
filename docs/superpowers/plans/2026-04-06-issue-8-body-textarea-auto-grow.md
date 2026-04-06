# Body Textarea Auto-Grow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the request body textarea auto-grow its height to fit content — on typing, pasting, width changes, example selection, and cache restoration.

**Architecture:** Add an `autoGrow(textarea)` JS function that sets `style.height` based on `scrollHeight`. Attach it to `input` events, `ResizeObserver` (for width changes), and call it after example select and field restore. Remove fixed `rows="6"` from Java; replace with CSS `min-height`. Remove `resize: vertical` from CSS.

**Tech Stack:** JavaScript (vanilla), CSS, Java (bulma-java HTML generation), Playwright (browser tests)

---

### Task 1: Add browser test for textarea auto-grow on input

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java:1235-1326`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Add `requestBodyHeight()` helper to AppFixture**

Add this method to `AppFixture.java` after the `fillRequestBody` method (around line 252):

```java
double requestBodyHeight() {
    return page.locator("#detail textarea[data-request-body]").boundingBox().height;
}
```

- [ ] **Step 2: Write failing test for auto-grow on input**

Add this test inside the `GivenAppWithRequestBody` nested class in `BrowserTest.java` (after `shouldShowBodyBoxWithSchemaToggle`):

```java
@Test void shouldAutoGrowTextareaWhenContentIsAdded() {
    app.clickTreeNode("pets/index.html");
    app.waitForDetailContent("Add a pet");
    var initialHeight = app.requestBodyHeight();

    app.fillRequestBody("{\n  \"name\": \"Fido\",\n  \"age\": 3,\n  \"extra1\": \"a\",\n  \"extra2\": \"b\",\n  \"extra3\": \"c\",\n  \"extra4\": \"d\",\n  \"extra5\": \"e\",\n  \"extra6\": \"f\",\n  \"extra7\": \"g\",\n  \"extra8\": \"h\"\n}");

    then(app.requestBodyHeight()).isGreaterThan(initialHeight);
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithRequestBody#shouldAutoGrowTextareaWhenContentIsAdded"` (60s timeout)
Expected: FAIL — height remains the same because there is no auto-grow yet.

### Task 2: Implement auto-grow — JS function and input event

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css:141-145`
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java:264`

- [ ] **Step 1: Add `autoGrow` function to app.js**

Add this function near the top of the `DOMContentLoaded` handler (e.g. after the `schemaToggleCache` declaration around line 10):

```javascript
function autoGrow(textarea) {
    textarea.style.height = 'auto';
    textarea.style.height = textarea.scrollHeight + 'px';
}
```

- [ ] **Step 2: Initialize auto-grow on HTMX content swap**

In app.js, find the block that sets up event listeners after HTMX swaps (search for `htmx:afterSettle` or the section that calls `restoreFields`). After `restoreFields(form)` is called, add auto-grow initialization for all request body textareas in the newly loaded form. Also add auto-grow after example select changes.

Find the `restoreFields(form)` call in the HTMX settle handler and add after it:

```javascript
form.querySelectorAll('textarea[data-request-body]').forEach(function(ta) {
    ta.addEventListener('input', function() { autoGrow(ta); });
    new ResizeObserver(function() { autoGrow(ta); }).observe(ta);
    autoGrow(ta);
});
```

Also find the example select handler (around line 392 where it sets `textarea.value = sel.value`) and add after it:

```javascript
autoGrow(textarea);
```

- [ ] **Step 3: Remove `rows="6"` from OperationFragmentGenerator.java**

In `OperationFragmentGenerator.java` line 264, remove `.attr("rows", "6")`.

Change:
```java
var textareaEl = textarea()
        .attr("data-request-body", "true")
        .classes("is-family-code")
        .attr("rows", "6");
```

To:
```java
var textareaEl = textarea()
        .attr("data-request-body", "true")
        .classes("is-family-code");
```

- [ ] **Step 4: Update CSS — replace `resize: vertical` with `min-height` and `overflow-y: hidden`**

In `app.css`, change the `#detail textarea[data-request-body]` rule (lines 141-145):

From:
```css
#detail textarea[data-request-body] {
    font-family: var(--mono-font);
    font-size: 0.875rem;
    resize: vertical;
}
```

To:
```css
#detail textarea[data-request-body] {
    font-family: var(--mono-font);
    font-size: 0.875rem;
    resize: none;
    overflow-y: hidden;
    min-height: 10rem;
}
```

- [ ] **Step 5: Run the auto-grow test to verify it passes**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithRequestBody#shouldAutoGrowTextareaWhenContentIsAdded"` (60s timeout)
Expected: PASS

### Task 3: Add browser test for auto-grow on width reduction

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Add `dragSplitHandle` helper to AppFixture if not already present**

Check if AppFixture already has a method to drag the split pane handle. If not, add:

```java
void dragSplitHandleBy(int deltaX) {
    var handle = page.locator("#detail .split-handle");
    var box = handle.boundingBox();
    page.mouse().move(box.x + box.width / 2, box.y + box.height / 2);
    page.mouse().down();
    page.mouse().move(box.x + box.width / 2 + deltaX, box.y + box.height / 2);
    page.mouse().up();
}
```

- [ ] **Step 2: Write failing test for auto-grow on width reduction**

Add this test inside `GivenAppWithRequestBody`:

```java
@Test void shouldAutoGrowTextareaWhenWidthIsReduced() {
    app.clickTreeNode("pets/index.html");
    app.waitForDetailContent("Add a pet");
    app.fillRequestBody("{\"name\": \"Fido\", \"age\": 3, \"active\": true, \"tag\": \"dog\", \"breed\": \"labrador\"}");
    var initialHeight = app.requestBodyHeight();

    app.dragSplitHandleBy(-200);

    then(app.requestBodyHeight()).isGreaterThan(initialHeight);
}
```

- [ ] **Step 3: Run the test to verify it passes**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithRequestBody#shouldAutoGrowTextareaWhenWidthIsReduced"` (60s timeout)
Expected: PASS — the `ResizeObserver` from Task 2 should already handle this.

### Task 4: Run full test suite and verify existing tests still pass

**Files:** None (verification only)

- [ ] **Step 1: Run the full GivenAppWithRequestBody tests**

Run: `mvn test -pl core -Dtest="BrowserTest\$GivenAppWithRequestBody"` (60s timeout)
Expected: All tests PASS

- [ ] **Step 2: Run the unit test that checks for `data-request-body`**

Run: `mvn test -pl core -Dtest="OpenApiUiGeneratorTest#shouldGenerateRequestBodyTextarea"` (60s timeout)
Expected: PASS

- [ ] **Step 3: Run the full test suite**

Run: `mvn test -pl core` (60s timeout)
Expected: All tests PASS

- [ ] **Step 4: Review screenshots**

Inspect `core/target/screenshots/` for both light and dark mode screenshots to visually confirm the textarea looks correct. Pay attention to:
- Request body textarea has reasonable default height
- Textarea integrates well with the split pane layout
- No visual regressions in other screenshots
