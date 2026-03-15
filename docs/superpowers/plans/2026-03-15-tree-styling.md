# Tree Styling Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Polish the tree component's look & feel — better active state, hover, spacing, and summary visibility.

**Architecture:** CSS-only changes in `Tree.java` plus minor Java tweaks in `OpenApiUiGenerator` to add a `tree-param` class for `{...}` segments and wrap summary text in a targetable span.

**Tech Stack:** Java (bulma-java HTML generation), CSS, Bulma variables

**Spec:** `docs/superpowers/specs/2026-03-15-tree-styling-design.md`

**Design note:** The spec says "method-colored left accent," but since selection is at the segment level (not per-operation), and a segment can have multiple methods, a fixed accent color (`var(--bulma-link)`) is used instead. This is cleaner than picking an arbitrary method's color.

---

## Chunk 1: Java Changes

### Task 1: Add `tree-param` CSS class for path parameter segments

Path parameters like `{petId}` should be visually distinct from literal segments.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:124-147`
- Test: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`

@tdder:tdd @tdder:java

- [x] **Step 1: Write a failing test**

In `OpenApiUiGeneratorTest`, add a test that checks the generated HTML assigns `tree-param` to `{...}` segments. Find an existing test fixture with path params (the Pet Store spec has `/pets/{petId}`).

```java
@Test void shouldUseParamClassForPathParameters() {
    var html = Files.readString(outputDir.resolve("index.html"));

    then(html).contains("class=\"tree-param\"");
}
```

- [x] **Step 2: Run the test, verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldUseParamClassForPathParameters'`
Expected: FAIL — `tree-param` class not found in output

- [x] **Step 3: Implement — detect `{...}` segments and apply class**

In `OpenApiUiGenerator.java`, both `addNodes` methods use `span(segment).classes("tree-segment")`. Change to use `tree-param` when the segment matches `{...}`:

```java
private String segmentClass(String segment) {
    return segment.startsWith("{") && segment.endsWith("}") ? "tree-param" : "tree-segment";
}
```

Replace all four occurrences of `.classes("tree-segment")` in `addNodes` methods with `.classes(segmentClass(segment))`.

- [x] **Step 4: Run test, verify it passes**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest`
Expected: all pass

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java \
       core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "add tree-param CSS class for path parameter segments"
```

### Task 2: Wrap summary text in a targetable span

Currently `operationLabel()` adds summary text as a raw text node inside `.tree-op-label`.
Wrap it in `<span class="tree-op-summary">` so CSS can show/hide it.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:164-171`
- Test: `core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java`

@tdder:tdd @tdder:java

- [x] **Step 1: Write a failing test**

```java
@Test void shouldWrapSummaryInSpan() {
    var html = Files.readString(outputDir.resolve("index.html"));

    then(html).contains("class=\"tree-op-summary\"");
}
```

- [x] **Step 2: Run test, verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldWrapSummaryInSpan'`
Expected: FAIL — no `tree-op-summary` class found

- [x] **Step 3: Implement — wrap labelText in span**

In `operationLabel()`, change:
```java
var labelText = operation.getSummary() != null ? " — " + operation.getSummary() : "";
return span().classes("tree-op-label").content(badge).content(labelText)
```

To:
```java
var label = span().classes("tree-op-label").content(badge);
if (operation.getSummary() != null) {
    label.content(span(" — " + operation.getSummary()).classes("tree-op-summary"));
}
return label
```

Keep the `hx-*` attributes on the outer `.tree-op-label` span.

- [x] **Step 4: Run test, verify it passes**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest`
Expected: all pass

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java \
       core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "wrap operation summary in span for CSS targeting"
```

## Chunk 2: CSS Changes

### Task 3: Update Tree CSS for new styling

Replace the CSS in `Tree.java` with the new design: row-level hover, accent-bar selection,
path parameter styling, and summary visibility.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java:96-138` (the `CSS` constant)
- Test: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

@tdder:tdd @tdder:java

- [x] **Step 1: Write failing tests for new CSS content**

Update the existing `shouldProvideCss` test and add new assertions:

```java
@Test void shouldProvideCss() {
    then(Tree.css())
            .contains("[role=\"tree\"]")
            .contains(".tree-toggle")
            .contains(".tree-segment")
            .contains(".tree-param")
            .contains("transition")
            .doesNotContain(".tree-op-label");
}
```

- [x] **Step 2: Run test, verify it fails**

Run: `mvn test -pl core -Dtest='TreeTest#shouldProvideCss'`
Expected: FAIL — `.tree-param` not found in CSS

- [x] **Step 3: Implement — replace the CSS constant**

Replace the `CSS` string in `Tree.java` with:

```java
private static final String CSS = """
        [role="tree"] {
            list-style: none;
            margin: 0;
            padding: 0;
        }
        [role="group"] {
            list-style: none;
            margin: 0;
            padding: 0 0 0 1.25rem;
            border-left: 2px solid var(--bulma-border);
            margin-left: 0.5rem;
        }
        [role="treeitem"] {
            padding: 6px 8px;
            margin: 1px 0;
            line-height: 1.7;
            border-radius: 4px;
            cursor: pointer;
            transition: background 0.15s;
        }
        [role="treeitem"]:hover:not([aria-selected="true"]) {
            background-color: var(--bulma-scheme-main-ter);
        }
        [role="treeitem"][aria-selected="true"] {
            background: linear-gradient(90deg, var(--bulma-link) 3px, var(--bulma-link-light) 3px);
            padding-left: 12px;
        }
        [role="tree"]:focus-visible [role="treeitem"][aria-selected="true"] {
            outline: 2px solid var(--bulma-link);
            outline-offset: 1px;
        }
        .tree-segment {
            font-weight: 700;
            color: var(--bulma-text-strong);
            font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
            font-size: 0.9rem;
        }
        .tree-param {
            font-weight: 600;
            color: #7c5cbf;
            font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
            font-size: 0.85rem;
            font-style: italic;
        }
        .tree-toggle {
            display: inline-block;
            cursor: pointer;
            font-size: 0.65rem;
            width: 1rem;
            text-align: center;
            transition: transform 0.15s ease;
            user-select: none;
            vertical-align: middle;
            color: var(--bulma-text-weak);
        }
        [role="treeitem"][aria-expanded="false"] > .tree-toggle {
            transform: rotate(-90deg);
        }
        """;
```

Key changes from old CSS:
- `[role="treeitem"]`: padding `6px 8px` (was `4px 0`), added `border-radius`, `cursor`, `transition`
- Hover: targets `[role="treeitem"]:hover` (was `> span:hover`)
- Selected: gradient on `<li>` (was `background-color` on `> span:first-child`)
- Focus: targets `<li>` (was `> span:first-child`)
- Removed: `[role="treeitem"] > span` rules (cursor/padding now on `<li>`)
- Added: `.tree-param` class
- `.tree-segment`: font-weight `700` (was `600`)

- [x] **Step 4: Run test, verify it passes**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: all pass

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java \
       core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "restyle tree: accent selection, hover, param styling"
```

### Task 4: Add summary visibility CSS to APP_CSS

The `.tree-op-summary` rule belongs in `APP_CSS` (not `Tree.css()`) because it's
app-specific — the generic Tree component doesn't know about operation summaries.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java:250-258`

@tdder:tdd @tdder:java

- [x] **Step 1: Write a failing test**

```java
@Test void shouldHideSummaryOnNonSelectedItems() {
    var html = Files.readString(outputDir.resolve("openapi-ui.css"));

    then(html).contains(".tree-op-summary");
}
```

- [x] **Step 2: Run test, verify it fails**

Run: `mvn test -pl core -Dtest='OpenApiUiGeneratorTest#shouldHideSummaryOnNonSelectedItems'`
Expected: FAIL — `.tree-op-summary` not in CSS

- [x] **Step 3: Implement — add summary visibility rules**

In `APP_CSS`, add after the existing `.tree-op-label` block:

```java
.tree-op-summary {
    display: none;
}
[aria-selected="true"] .tree-op-summary {
    display: inline;
}
```

- [x] **Step 4: Run test, verify it passes**

Run: `mvn test -pl core -Dtest=OpenApiUiGeneratorTest`
Expected: all pass

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java \
       core/src/test/java/com/github/t1/openapi/ui/OpenApiUiGeneratorTest.java
git commit -m "show operation summaries only on selected tree item"
```

### Task 5: Visual verification

Run all tests including browser tests and review screenshots.

**Files:**
- Read: `core/target/screenshots/*.png`

- [x] **Step 1: Run all tests**

Run: `mvn test -pl core`
Expected: all tests pass

- [x] **Step 2: Review screenshots**

Check screenshots in `core/target/screenshots/` using the `frontend-design` plugin:
- `layout-desktop.png` — verify tree styling in context
- `tree-expanded.png` — verify expanded tree with selection, param styling, badges

Verify:
- Selected item has blue left accent bar + tinted background
- Non-selected items have no background
- `{petId}` appears in purple italic
- Summaries visible only on the selected row
- Overall spacing feels less cramped

- [x] **Step 3: Commit any screenshot-driven adjustments**

If visual review reveals issues, fix and re-run tests before committing.
