# Visual Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refine the OpenAPI UI's visual presentation with an inverted box layout, segmented mode toggle, simplified path params, and grouped method tag addons.

**Architecture:** Four independent CSS/Java changes to `Tree.java` and `OpenApiUiGenerator.java`. Each change is isolated: path params, method addons, layout inversion, mode toggle. Existing Playwright browser tests validate all changes via screenshots.

**Tech Stack:** Java 21, bulma-java, Bulma CSS, Playwright tests

**Spec:** `docs/superpowers/specs/2026-03-16-visual-polish-design.md`

---

## Task 1: Simplify Path Parameter Styling

Remove italic and purple color from path params. Keep curly braces, use lighter gray.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java:143-149` (CSS)
- Test: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write failing test**

Add a test in `TreeTest` asserting the CSS for `tree-param` does NOT use italic or purple:

```java
@Test void treeParamCssHasNoItalic() {
    then(Tree.css())
            .contains(".tree-param")
            .doesNotContain("italic")
            .doesNotContain("#7c5cbf");
}
```

Note: existing tests use `then()` from `org.assertj.core.api.BDDAssertions`.

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='TreeTest#treeParamCssHasNoItalic'`
Expected: FAIL — current CSS contains both `italic` and `#7c5cbf`

- [x] **Step 3: Update the CSS**

In `Tree.java` lines 143-149, replace the `.tree-param` block:

```css
.tree-param {
    font-weight: 400;
    color: var(--bulma-text-weak);
    font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
    font-size: 0.85rem;
}
```

Changes: `font-weight: 600` → `400`, `color: #7c5cbf` → `var(--bulma-text-weak)`, removed `font-style: italic`.

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='TreeTest#treeParamCssHasNoItalic'`
Expected: PASS

- [x] **Step 5: Run full test suite and review screenshots**

Run: `mvn test -pl core`
Expected: All tests pass. Check `core/target/screenshots/tree-expanded.png` — path params should appear in lighter gray, no italic, with curly braces.

- [x] **Step 6: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "simplify path param styling to lighter weight only"
```

---

## Task 2: Group Method Tag Addons

Fuse per-path method tags into a single grouped addon block with shared border.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:127-189` (addNodes + methodAddon)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:368-377` (APP_CSS method-addon)
- Test: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`

- [x] **Step 1: Write failing test**

Add a test in `OpenApiUiGeneratorTest` asserting method addons are wrapped in a `method-group` container. Follow the existing test pattern — use `generate()` then read `index.html`:

```java
@Test void methodAddonsAreGrouped() throws Exception {
    generate("/multi-method.yaml");

    var indexHtml = Files.readString(outputDir.resolve("index.html"));
    then(indexHtml).contains("method-group");
}
```

If `/multi-method.yaml` doesn't exist, use an existing spec that has methods (e.g. `/nested-paths.yaml`).

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#methodAddonsAreGrouped'`
Expected: FAIL — no `method-group` class exists yet

- [x] **Step 3: Add the grouped addon wrapper**

In `OpenApiUiGenerator.java`, replace the method addon loop in both `addNodes` methods. Currently at lines 132-135 and 160-163, individual `methodAddon()` spans are added to `label`. Wrap them in a group instead:

```java
if (!child.operations.isEmpty()) {
    var group = span().classes("method-group");
    for (var method : child.operations.keySet()) {
        group.content(span(method.name()).classes("method-addon", "method-" + method.name().toLowerCase()));
    }
    label.content(group);
}
```

Apply in both `addNodes(Tree, PathNode, String)` and `addNodes(Tree.Node, PathNode, String)`.

- [x] **Step 4: Update the CSS**

In `APP_CSS`, replace the `.method-addon` block (lines 368-377) and add `.method-group`:

```css
.method-group {
    display: inline-flex;
    border-radius: 3px;
    overflow: hidden;
    border: 1px solid var(--bulma-border);
    margin-left: 6px;
    vertical-align: middle;
    line-height: 1;
}
.method-addon {
    font-size: 0.65rem;
    padding: 2px 4px;
    color: white;
    font-weight: 600;
}
.method-addon:not(:last-child) {
    border-right: 1px solid var(--bulma-border);
}
```

Keep the `.method-get`, `.method-post`, etc. color classes unchanged.

- [x] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#methodAddonsAreGrouped'`
Expected: PASS

- [x] **Step 6: Run full test suite and review screenshots**

Run: `mvn test -pl core`
Expected: All tests pass. Existing test `shouldRenderMethodTagAddons` should still pass — it checks for `method-addon` class which is preserved. Check `core/target/screenshots/tree-expanded.png` for fused method blocks.

- [x] **Step 7: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "group method tag addons into single fused block per path"
```

---

## Task 3: Invert Layout — Tree in Box, Content on Background

Move the Bulma Box from the detail pane to the tree. Detail content sits on the gray background.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:77` (detail pane)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:82-88` (columns layout)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:363-464` (APP_CSS)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/AppFixture.java` (no fixture changes expected, but verify)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

No existing tests reference `.box` in assertions, so this change should not break test assertions. The `#detail` locator used throughout `AppFixture` works on any element with `id="detail"`.

- [x] **Step 1: Write failing test**

Add a test in `BrowserTest.GivenAppWithOneGet` using the `AppFixture` pattern. Add a helper method to `AppFixture` for checking tree-in-box:

In `AppFixture.java`, add:
```java
boolean isTreeInBox() {
    return page.locator(".box [role='tree']").count() == 1;
}

boolean isDetailInBox() {
    return page.locator(".box#detail").count() == 1;
}
```

In `BrowserTest.GivenAppWithOneGet`, add:
```java
@Test void treeIsInBoxAndDetailIsNot() {
    then(app.isTreeInBox()).isTrue();
    then(app.isDetailInBox()).isFalse();
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#treeIsInBoxAndDetailIsNot'`
Expected: FAIL — currently detail IS in a box, tree is NOT

- [x] **Step 3: Swap box and div**

In `OpenApiUiGenerator.java`:

Line 77 — change `box()` to `div()`:
```java
var detail = div().id("detail").attr("tabindex", "0");
```

Lines 82-88 — wrap the tree `list` in a `box()`:
```java
var body = section().content(container().content(
        detailHeader,
        columns().classes("is-desktop").content(
                column().classes("is-one-third").content(box().content(list)),
                column().classes("detail-column").content(detail)
        )
));
```

- [x] **Step 4: Update CSS for inverted layout**

In `APP_CSS`, update the sidebar column CSS (lines 383-392) and detail column:

```css
.columns.is-desktop > .column.is-one-third {
    background-color: var(--bulma-scheme-main-bis);
    padding: 1.25rem;
    display: flex;
    flex-direction: column;
}
.columns.is-desktop > .column.is-one-third > .box {
    flex: 1;
}
.detail-column {
    padding-left: 2rem;
    background-color: var(--bulma-scheme-main-bis);
}
```

Remove `border-right` from the sidebar (the box edge replaces it).

Update `#detail pre` background (line 435) — use `var(--bulma-scheme-main)` (white) instead of `var(--bulma-scheme-main-bis)` so code blocks remain distinct on the gray ground.

Update the desktop media query:
```css
@media screen and (min-width: 1024px) {
    .columns.is-desktop > .column.is-one-third {
        min-height: calc(100vh - 4rem);
    }
    .detail-column {
        min-height: calc(100vh - 4rem);
    }
}
```

- [x] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#treeIsInBoxAndDetailIsNot'`
Expected: PASS

- [x] **Step 6: Run full test suite and review screenshots**

Run: `mvn test -pl core`
Expected: All tests pass. Check `core/target/screenshots/layout-desktop.png` — tree in floating box, detail on gray, white code blocks.

- [x] **Step 7: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git commit -m "invert layout: tree in box, content on background"
```

---

## Task 4: Segmented Mode Toggle

Replace Bulma button group with an iOS-style segmented control.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:69-74` (modeToggle HTML)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:363-464` (APP_CSS)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:466-485` (APP_JS mode toggle)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/AppFixture.java:207-209` (mode button locators)
- Test: `core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java`

**Impact on existing tests:** `AppFixture.isModeButtonVisible()` and `clickModeButton()` use `page.locator("button:text('...')")` which will break when buttons become `<span>` elements. These must be updated to use `[data-mode-btn]` locators instead. Tests affected:
- `BrowserTest.GivenAppWithOneGet.modeToggleHasThreeOptionsAndSwitches` (lines 49-56)
- All `app.clickModeButton()` calls in `GivenAppWithParams` (lines 290, 303, 311, 321, 331)
- All `app.clickModeButton()` calls in `GivenAppWithRequestBody` (lines 374, 387)

- [x] **Step 1: Update AppFixture locators**

In `AppFixture.java`, update the mode methods to work with `<span>` elements:

Line 207:
```java
boolean isModeButtonVisible(String mode) {
    return page.locator("[data-mode-btn='" + mode.toLowerCase() + "']").isVisible();
}
```

Line 209:
```java
void clickModeButton(String mode) {
    page.locator("[data-mode-btn='" + mode.toLowerCase() + "']").click();
}
```

Note: `isModeButtonVisible("Try")` calls in tests pass `"Try"` but `data-mode-btn` stores lowercase `"try"`. Add `.toLowerCase()` to handle this.

- [x] **Step 2: Write failing test**

Add a test in `BrowserTest.GivenAppWithOneGet` for the segmented control. Add a helper to `AppFixture`:

```java
boolean hasSegmentedControl() {
    return page.locator(".segmented-control").count() == 1;
}

boolean isSegmentActive(String mode) {
    return page.locator(".segmented-control [data-mode-btn='" + mode + "'].is-active").count() == 1;
}
```

In `BrowserTest.GivenAppWithOneGet`:
```java
@Test void modeToggleIsSegmentedControl() {
    then(app.hasSegmentedControl()).isTrue();
    then(app.isSegmentActive("try")).isTrue();
}
```

- [x] **Step 3: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#modeToggleIsSegmentedControl'`
Expected: FAIL — no `.segmented-control` exists

- [x] **Step 4: Replace mode toggle HTML**

In `OpenApiUiGenerator.java` lines 69-74:

```java
var modeToggle = div().attr("data-mode", "try").attr("data-base-url", baseUrl)
        .classes("segmented-control").content(
                span("Try").classes("is-active").attr("data-mode-btn", "try"),
                span("httpie").attr("data-mode-btn", "httpie"),
                span("curl").attr("data-mode-btn", "curl")
        );
```

- [x] **Step 5: Add segmented control CSS**

Add to `APP_CSS`:

```css
.segmented-control {
    display: inline-flex;
    gap: 1px;
    background: var(--bulma-scheme-main-ter);
    border-radius: 6px;
    padding: 2px;
}
.segmented-control > span {
    padding: 5px 14px;
    font-size: 0.75rem;
    color: var(--bulma-text-weak);
    border-radius: 5px;
    cursor: pointer;
    transition: all 0.15s;
    user-select: none;
}
.segmented-control > span.is-active {
    background: var(--bulma-scheme-main);
    color: var(--bulma-text-strong);
    font-weight: 500;
    box-shadow: 0 1px 2px rgba(0,0,0,0.06);
}
```

Remove the old `.buttons.has-addons` references if any exist in APP_CSS.

- [x] **Step 6: Update mode toggle JS**

In `APP_JS`, update the click handler (lines 473-484) — replace `is-selected` and `is-primary` with `is-active`:

```javascript
modeContainer.querySelectorAll('[data-mode-btn]').forEach(function(btn) {
    btn.addEventListener('click', function() {
        modeContainer.setAttribute('data-mode', btn.getAttribute('data-mode-btn'));
        modeContainer.querySelectorAll('[data-mode-btn]').forEach(function(b) {
            b.classList.remove('is-active');
        });
        btn.classList.add('is-active');
        var newMode = btn.getAttribute('data-mode-btn');
        var sendBtns = document.querySelectorAll('#detail button[data-path]');
        sendBtns.forEach(function(b) { b.textContent = newMode === 'try' ? 'Send' : 'Copy'; });
    });
});
```

- [x] **Step 7: Run full test suite and review screenshots**

Run: `mvn test -pl core`
Expected: All tests pass. The existing `modeToggleHasThreeOptionsAndSwitches` test should still pass thanks to the updated `AppFixture` locators. Check `core/target/screenshots/layout-desktop.png` for the segmented pill control.

- [x] **Step 8: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/BrowserTest.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git commit -m "replace mode toggle with segmented control"
```

---

## Task 5: Final Review and Squash

- [x] **Step 1: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests pass

- [x] **Step 2: Review all screenshots**

Review all screenshots in `core/target/screenshots/` for design quality:
- `layout-desktop.png` — inverted layout, segmented toggle, grouped tags
- `tree-expanded.png` — lighter path params, grouped method addons
- `params-filled.png` — detail content on gray background, white code blocks
- `layout-mobile.png` — mobile layout still works

- [x] **Step 3: Squash commits**

Squash the four visual polish commits into one using `git reset --soft`:

```bash
git log --oneline -5   # verify the 4 commits to squash
git reset --soft HEAD~4
git commit -m "visual polish: inverted layout, segmented toggle, grouped tags, lighter params"
```

- [x] **Step 4: Update README if needed**

Check `README.md` for references to the old layout or mode toggle and update if needed.
