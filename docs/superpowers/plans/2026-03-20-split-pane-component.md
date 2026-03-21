# SplitPane Component Extraction Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the resizable split pane from `OpenApiUiGenerator` into a reusable `SplitPane` component following the `Tree.java` pattern.

**Architecture:** Create `SplitPane.java` extending `AbstractElement<SplitPane>` with static `css()` and `js()` methods. The component owns the grid layout, drag handle, and localStorage persistence. Panel styling (backgrounds, padding, min-heights) stays in the app.

**Tech Stack:** Java, htmljava (`AbstractElement`), CSS Grid, vanilla JS pointer events

**Spec:** `docs/superpowers/specs/2026-03-20-split-pane-component-design.md`

---

## Chunk 1: Create SplitPane Component (TDD)

### Task 1: SplitPane renders correct HTML structure

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/SplitPane.java`
- Test: `core/src/test/java/com/github/t1/openapi/ui/SplitPaneTest.java`

- [ ] **Step 1: Write failing test for basic rendering**

@tdder:java @tdder:tdd

```java
package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;

import static com.github.t1.htmljava.HtmlBasics.div;
import static com.github.t1.openapi.ui.SplitPane.splitPane;
import static org.assertj.core.api.BDDAssertions.then;

class SplitPaneTest {
    @Test void shouldRenderSplitLayout() {
        var pane = splitPane()
                .first(div().content("left"))
                .second(div().content("right"));

        var html = pane.render();

        then(html)
                .contains("class=\"split-layout\"")
                .contains("class=\"split-first\"")
                .contains("class=\"split-handle\"")
                .contains("class=\"split-second\"")
                .contains("left")
                .contains("right")
                .doesNotContain("data-persist");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldRenderSplitLayout'`
Expected: FAIL — `SplitPane` class does not exist

- [ ] **Step 3: Write minimal SplitPane implementation**

```java
package com.github.t1.openapi.ui;

import com.github.t1.htmljava.AbstractElement;
import com.github.t1.htmljava.Renderable;

import static com.github.t1.htmljava.HtmlBasics.div;

public class SplitPane extends AbstractElement<SplitPane> {

    public static SplitPane splitPane() { return new SplitPane(); }

    private SplitPane() {
        super("div");
        classes("split-layout");
    }

    public SplitPane first(Renderable content) {
        // Will be rendered in build; store for now
        this.firstContent = content;
        return this;
    }

    public SplitPane second(Renderable content) {
        this.secondContent = content;
        return this;
    }

    private Renderable firstContent;
    private Renderable secondContent;
    private boolean built;

    @Override public String render() {
        if (!built) {
            content(div().classes("split-first").content(firstContent));
            content(div().classes("split-handle"));
            content(div().classes("split-second").content(secondContent));
            built = true;
        }
        return super.render();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldRenderSplitLayout'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/SplitPane.java core/src/test/java/com/github/t1/openapi/ui/SplitPaneTest.java
git commit -m "add SplitPane component with basic HTML structure"
```

### Task 2: SplitPane provides CSS

- [ ] **Step 1: Write failing test for css()**

```java
@Test void shouldProvideSplitLayoutCss() {
    var css = SplitPane.css();

    then(css)
            .contains(".split-layout")
            .contains(".split-handle")
            .contains(".split-handle::after")
            .contains("col-resize");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldProvideSplitLayoutCss'`
Expected: FAIL — `css()` method does not exist

- [ ] **Step 3: Add css() method with extracted CSS**

Add to `SplitPane.java`:

```java
public static String css() { return CSS; }

private static final String CSS = """
        .split-layout {
            display: grid;
            grid-template-columns: minmax(150px, 1fr) 0px minmax(150px, 2fr);
            gap: 0;
        }
        .split-handle {
            width: 14px;
            margin-left: -7px;
            margin-right: -7px;
            cursor: col-resize;
            background: transparent;
            position: relative;
            z-index: 1;
        }
        .split-handle::after {
            content: '\\2022\\a\\2022\\a\\2022\\a\\2022\\a\\2022\\a\\2022\\a\\2022';
            white-space: pre;
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-150%, -50%);
            color: var(--bulma-text-weak);
            font-size: 0.6rem;
            line-height: 0.7;
            opacity: 0.7;
            transition: opacity 0.15s;
        }
        .split-handle:hover::after {
            opacity: 1;
            color: var(--bulma-link);
        }
        @media screen and (max-width: 1023px) {
            .split-layout {
                grid-template-columns: 1fr;
            }
            .split-handle {
                display: none;
            }
        }
        """;
```

Note: The responsive mobile collapse (`max-width: 1023px`) is part of the component. The desktop min-heights (`min-width: 1024px` media query) stay in app CSS since they reference app-specific values like `100vh - 4rem`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldProvideSplitLayoutCss'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/SplitPane.java core/src/test/java/com/github/t1/openapi/ui/SplitPaneTest.java
git commit -m "add SplitPane.css() with grid layout and handle styles"
```

### Task 3: SplitPane provides JS with configurable persistence

- [ ] **Step 1: Write failing test for js() without persistence**

```java
@Test void shouldProvideJsWithDragBehavior() {
    var js = SplitPane.js();

    then(js)
            .contains("split-handle")
            .contains("pointerdown")
            .contains("pointermove");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldProvideJsWithDragBehavior'`
Expected: FAIL — `js()` method does not exist

- [ ] **Step 3: Add js() method with drag behavior (no persistence)**

Add to `SplitPane.java`:

```java
public static String js() { return JS; }

private static final String JS = """
        document.addEventListener('DOMContentLoaded', function() {
            var splitHandle = document.querySelector('.split-handle');
            var splitLayout = document.querySelector('.split-layout');
            if (!splitHandle || !splitLayout) return;
            var persistKey = splitLayout.getAttribute('data-persist');
            if (persistKey) {
                var savedWidth = localStorage.getItem(persistKey);
                if (savedWidth) {
                    splitLayout.style.gridTemplateColumns = savedWidth + 'px 0px 1fr';
                }
            }
            splitHandle.addEventListener('pointerdown', function(e) {
                e.preventDefault();
                var splitFirst = splitLayout.querySelector('.split-first');
                var startX = e.clientX;
                var startWidth = splitFirst.getBoundingClientRect().width;
                function onMove(e) {
                    var newWidth = Math.max(150, startWidth + e.clientX - startX);
                    var maxWidth = splitLayout.getBoundingClientRect().width - 150;
                    newWidth = Math.min(newWidth, maxWidth);
                    splitLayout.style.gridTemplateColumns = newWidth + 'px 0px 1fr';
                }
                function onUp() {
                    document.removeEventListener('pointermove', onMove);
                    document.removeEventListener('pointerup', onUp);
                    if (persistKey) {
                        var finalWidth = splitFirst.getBoundingClientRect().width;
                        localStorage.setItem(persistKey, Math.round(finalWidth));
                    }
                }
                document.addEventListener('pointermove', onMove);
                document.addEventListener('pointerup', onUp);
            });
        });
        """;
```

Note: The JS uses a `data-persist` attribute on the layout div to determine the localStorage key. This keeps the JS generic — no hardcoded keys.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldProvideJsWithDragBehavior'`
Expected: PASS

- [ ] **Step 5: Write test for persistAs() adding data attribute**

```java
@Test void shouldAddPersistDataAttribute() {
    var pane = splitPane()
            .first(div().content("left"))
            .second(div().content("right"))
            .persistAs("my-key");

    var html = pane.render();

    then(html).contains("data-persist=\"my-key\"");
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldAddPersistDataAttribute'`
Expected: FAIL — no `data-persist` attribute in output

- [ ] **Step 7: Add persistAs() method**

Add to `SplitPane.java`:

```java
public SplitPane persistAs(String key) {
    attr("data-persist", key);
    return this;
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='SplitPaneTest#shouldAddPersistDataAttribute'`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/SplitPane.java core/src/test/java/com/github/t1/openapi/ui/SplitPaneTest.java
git commit -m "add SplitPane.js() with drag and optional persistence"
```

## Chunk 2: Integrate SplitPane into OpenApiUiGenerator

### Task 4: Replace inline split pane with SplitPane component

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [ ] **Step 1: Update imports and replace HTML construction**

In `OpenApiUiGenerator.java`:

Add import:
```java
import static com.github.t1.openapi.ui.SplitPane.splitPane;
```

Replace lines 88-92:
```java
var splitLayout = div().classes("split-layout").content(
        div().classes("split-tree").content(box().content(list)),
        div().classes("split-handle"),
        div().classes("split-detail").content(detail)
);
```

With:
```java
var splitLayout = splitPane()
        .first(box().content(list))
        .second(detail)
        .persistAs("openapi-ui-tree-width");
```

- [ ] **Step 2: Update CSS output to include SplitPane.css()**

Replace line 107:
```java
Files.writeString(outputDir.resolve("openapi-ui.css"), Tree.css() + APP_CSS);
```
With:
```java
Files.writeString(outputDir.resolve("openapi-ui.css"), Tree.css() + SplitPane.css() + APP_CSS);
```

- [ ] **Step 3: Update JS output to include SplitPane.js()**

Replace line 102:
```java
.javaScriptCode(APP_JS)
```
With:
```java
.javaScriptCode(SplitPane.js())
.javaScriptCode(APP_JS)
```

- [ ] **Step 4: Remove extracted CSS from APP_CSS**

Remove these blocks from `APP_CSS` (they now live in `SplitPane.css()`):
- `.split-layout { display: grid; ... }` (lines 436-440)
- `.split-handle { width: 14px; ... }` (lines 454-462)
- `.split-handle::after { ... }` (lines 463-475)
- `.split-handle:hover::after { ... }` (lines 476-479)
- The `@media screen and (max-width: 1023px)` block for `.split-layout` and `.split-handle` (lines 488-495)

Keep these blocks in `APP_CSS` (app-specific panel styling):
- `.split-tree { background-color: ...; padding: ...; }` (lines 441-447) — rename to `.split-first`
- `.split-tree > .box { ... }` (lines 448-453) — rename to `.split-first > .box`
- `.split-detail { padding-left: ...; }` (lines 514-518) — rename to `.split-second`
- The `@media screen and (min-width: 1024px)` block (lines 480-487) — rename `.split-tree`/`.split-detail` to `.split-first`/`.split-second`

- [ ] **Step 5: Rename CSS classes in APP_CSS**

Rename all remaining references in APP_CSS:
- `.split-tree` → `.split-first` (in the kept blocks listed above)
- `.split-detail` → `.split-second` (in the kept blocks listed above)

- [ ] **Step 6: Remove extracted JS from APP_JS**

Remove the split handle drag block from `APP_JS` (lines 897-925, the `// Split handle drag + localStorage persistence` section).

- [ ] **Step 7: Update AppFixture test helpers**

In `AppFixture.java`, update locator references:
- `".split-tree"` → `".split-first"` in `treeWidth()` (line 349)
- `".split-handle"` stays the same (the component still uses this class)

- [ ] **Step 8: Run all tests**

Run: `mvn test -pl core`
Expected: ALL PASS — behavior should be identical

- [ ] **Step 9: Review screenshots**

Check `core/target/screenshots/` to visually confirm the split pane looks identical after extraction.

- [ ] **Step 10: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git commit -m "use SplitPane component in OpenApiUiGenerator"
```

### Task 5: Squash commits

- [ ] **Step 1: Squash all commits from this plan into one**

```bash
git reset --soft HEAD~4 && git commit -m "extract resizable split pane into reusable SplitPane component"
```
