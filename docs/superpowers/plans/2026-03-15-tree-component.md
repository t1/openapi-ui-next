# Tree Component Extraction Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the inline tree HTML generation from `OpenApiUiGenerator` into a standalone, generic `Tree` class following bulma-java component patterns.

**Architecture:** `Tree` extends `AbstractElement<Tree>` (not `BulmaElement` since it lives in openapi-ui, not bulma-java). It provides `item()` for leaf nodes and `node()` for collapsible branches with sibling content. CSS and JS are encapsulated as static `css()` and `js()` methods (class-level constants, not instance-dependent). `OpenApiUiGenerator.renderNode()` is replaced by calls to the Tree API. The first item added to the root tree automatically gets `aria-selected="true"` and the root gets `autofocus` (matching current behavior).

**Tech Stack:** Java, bulma-java/html-java, JUnit 5

**Skills:** @tdder:tdd, @tdder:java, @bulma-java

---

## Chunk 1: Create Tree class with TDD

### Task 1: Empty tree renders root `<ul role="tree">`

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- Create: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write the failing test**

```java
package com.github.t1.openapi.ui;

import org.junit.jupiter.api.Test;

import static com.github.t1.openapi.ui.Tree.tree;
import static org.assertj.core.api.BDDAssertions.then;

class TreeTest {
    @Test void shouldRenderEmptyTree() {
        var html = tree().render();

        then(html).isEqualTo("<ul role=\"tree\" tabindex=\"0\" autofocus></ul>");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='TreeTest#shouldRenderEmptyTree'`
Expected: FAIL — `Tree` class does not exist

- [x] **Step 3: Write minimal implementation**

```java
package com.github.t1.openapi.ui;

import com.github.t1.htmljava.AbstractElement;

public class Tree extends AbstractElement<Tree> {
    public static Tree tree() { return new Tree(); }

    private Tree() {
        super("ul");
        attr("role", "tree");
        attr("tabindex", "0");
        attr("autofocus", "");
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='TreeTest#shouldRenderEmptyTree'`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "add empty Tree component with root ul element"
```

### Task 2: Tree renders leaf items

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write the failing test**

```java
@Test void shouldRenderLeafItem() {
    var html = tree().item("hello").render();

    then(html).contains("<li role=\"treeitem\"><span>hello</span></li>");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='TreeTest#shouldRenderLeafItem'`
Expected: FAIL — `item()` method does not exist

- [x] **Step 3: Write minimal implementation**

Add to `Tree.java`:

```java
import static com.github.t1.htmljava.HtmlBasics.*;

public Tree item(String label) {
    content(li().attr("role", "treeitem").content(span(label)));
    return this;
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='TreeTest#shouldRenderLeafItem'`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "add Tree.item() for leaf nodes"
```

### Task 3: Tree renders leaf items with Renderable content

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write the failing test**

```java
@Test void shouldRenderLeafItemWithRenderableContent() {
    var html = tree().item(span("rich")).render();

    then(html).contains("<li role=\"treeitem\"><span>rich</span></li>");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='TreeTest#shouldRenderLeafItemWithRenderableContent'`
Expected: FAIL — no `item(Renderable)` overload

- [x] **Step 3: Write minimal implementation**

Add overload to `Tree.java`:

```java
import com.github.t1.htmljava.Renderable;

public Tree item(Renderable label) {
    content(li().attr("role", "treeitem").content(label));
    return this;
}
```

Change the existing `item(String)` to delegate: `return item(span(label));`

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: PASS (all TreeTest tests)

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "add Tree.item(Renderable) overload"
```

### Task 4: Tree renders collapsible nodes with children

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write the failing test**

```java
@Test void shouldRenderNodeWithChildren() {
    var html = tree().node("parent", node -> node.item("child")).render();

    then(html)
            .contains("aria-expanded=\"true\"")
            .contains("<span class=\"tree-toggle\">\u25BC</span>")
            .contains("<span>parent</span>")
            .contains("<ul role=\"group\">")
            .contains("<li role=\"treeitem\"><span>child</span></li>");
}

@Test void shouldRenderNodeWithSiblingContent() {
    var html = tree().node("seg", node -> {
        node.content(span("badge").classes("op"));
        node.item("child");
    }).render();

    // badge is on the <li>, before the <ul role="group">
    int badgePos = html.indexOf("badge");
    int groupPos = html.indexOf("role=\"group\"");
    then(badgePos).as("sibling content should appear before subtree").isLessThan(groupPos);
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='TreeTest#shouldRenderNodeWithChildren'`
Expected: FAIL — `node()` method does not exist

- [x] **Step 3: Write minimal implementation**

Add to `Tree.java`:

```java
import com.github.t1.htmljava.Element;
import java.util.function.Consumer;

public Tree node(String label, Consumer<Tree> children) {
    return node(span(label), children);
}

public Tree node(Renderable label, Consumer<Node> children) {
    var node = new Node(label);
    children.accept(node);
    content(node.build());
    return this;
}

private Tree(String role) {
    super("ul");
    attr("role", role);
}
```

Add an inner class `Node` that collects sibling content and subtree children separately:

```java
public static class Node {
    private final Element item;
    private final Tree subtree;

    private Node(Renderable label) {
        item = li().attr("role", "treeitem").attr("aria-expanded", "true");
        item.content(span("\u25BC").classes("tree-toggle"));
        item.content(label);
        subtree = new Tree("group");
    }

    /** Add content as a sibling on the node's <li> (before the subtree) */
    public Node content(Renderable content) {
        item.content(content);
        return this;
    }

    /** Add a leaf item to this node's subtree */
    public Node item(String label) { subtree.item(label); return this; }
    public Node item(Renderable label) { subtree.item(label); return this; }

    /** Add a collapsible sub-node to this node's subtree */
    public Node node(String label, Consumer<Node> children) { subtree.node(label, children); return this; }
    public Node node(Renderable label, Consumer<Node> children) { subtree.node(label, children); return this; }

    Element build() {
        item.content(subtree);
        return item;
    }
}
```

Adjust the public `tree()` factory to use: `new Tree("tree").attr("tabindex", "0").attr("autofocus", "");` — keeping the no-arg constructor private via a static factory that calls the `String` constructor.

**Key design point:** The `Node` inner class separates sibling content (operations, badges) from subtree children. `node.content(badge)` adds to the `<li>` directly, while `node.item(...)` / `node.node(...)` add to the nested `<ul role="group">`. This matches the current DOM structure where operations and the subtree are both children of the same `<li>`.

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: PASS (all TreeTest tests)

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "add Tree.node() for collapsible branches"
```

### Task 5: First tree item gets aria-selected

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write the failing test**

```java
@Test void shouldSelectFirstItem() {
    var html = tree()
            .item("first")
            .item("second")
            .render();

    then(html)
            .contains("aria-selected=\"true\"")
            .satisfies(h -> {
                int firstSelected = h.indexOf("aria-selected");
                int firstItem = h.indexOf("first");
                then(firstSelected).as("aria-selected should be on the first item").isLessThan(firstItem + 20);
            });
}

@Test void shouldNotSelectSecondItem() {
    var html = tree()
            .item("first")
            .item("second")
            .render();

    int lastIndex = html.lastIndexOf("aria-selected");
    int firstIndex = html.indexOf("aria-selected");
    then(lastIndex).as("only one item should be selected").isEqualTo(firstIndex);
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: FAIL — no `aria-selected` attribute on any item

- [x] **Step 3: Write minimal implementation**

Add to `Tree.java`:

```java
private boolean isRoot;
private boolean firstItem = true;
```

In the `Tree(String role)` constructor, set `isRoot = "tree".equals(role);`.

Add a helper method that both `item()` and `node()` call after creating the `<li>`:

```java
private void markFirstItem(Element item) {
    if (isRoot && firstItem) {
        item.attr("aria-selected", "true");
        firstItem = false;
    }
}
```

Call `markFirstItem(item)` in `item(Renderable)` on the `<li>` before adding it via `content()`, and in `node(Renderable, Consumer<Node>)` — pass the flag to `Node` so it can set `aria-selected` on its `<li>` in the constructor. Alternatively, set it on `node.build()` result before calling `content()`:

```java
public Tree node(Renderable label, Consumer<Node> children) {
    var node = new Node(label);
    children.accept(node);
    var item = node.build();
    markFirstItem(item);
    content(item);
    return this;
}
```

And in `item(Renderable)`:

```java
public Tree item(Renderable label) {
    var item = li().attr("role", "treeitem").content(label);
    markFirstItem(item);
    content(item);
    return this;
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "auto-select first tree item with aria-selected"
```

### Task 6: CSS and JS encapsulation

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 1: Write the failing test**

```java
@Test void shouldProvideCss() {
    then(Tree.css())
            .contains("[role=\"tree\"]")
            .contains(".tree-toggle")
            .contains(".tree-segment")
            .doesNotContain(".tree-op-label");
}

@Test void shouldProvideJs() {
    then(Tree.js())
            .contains("role=\"tree\"")
            .contains("ArrowDown")
            .contains("ArrowUp");
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: FAIL — `css()` and `js()` do not exist

- [x] **Step 3: Write minimal implementation**

Add `public static String css()` and `public static String js()` to `Tree.java`, returning the tree-specific CSS and JS as string constants. Copy the content from `OpenApiUiGenerator` — do NOT remove it from there yet (that happens in Task 7).

**Tree CSS to include** (generic tree styling — copy from `CUSTOM_CSS`):
- `[role="tree"]` — list-style, margin, padding
- `[role="group"]` — nested list indentation, border-left
- `[role="treeitem"]` — padding, line-height
- `[role="treeitem"] > span` — cursor, padding, border-radius
- `[role="treeitem"] > span:hover` — background
- `[role="treeitem"][aria-selected="true"] > span:first-child` — selection highlight
- `[role="tree"]:focus-visible [role="treeitem"][aria-selected="true"] > span:first-child` — focus outline
- `.tree-segment` — font styling
- `.tree-toggle` — toggle arrow styling
- `[role="treeitem"][aria-expanded="false"] > .tree-toggle` — rotation

**NOT included** (app-specific, stays in generator): `.tree-op-label`

**Tree JS to include** (copy from `TREE_KEYBOARD_JS`):
- `isGroupVisible()`, `getVisibleItems()`, `toggleNode()` helper functions
- Tree click handler (`.tree-toggle` click to expand/collapse)
- Tree keydown handler (ArrowDown/Up/Left/Right, Enter, Escape)
- `selectItem()` helper
- Global Escape key handler (focuses tree from anywhere)

**NOT included** (app-specific, stays in generator): mode toggle JS, send button JS, auto-load first `[hx-get]`, `htmx:afterSwap` handler, `showCopied()`

- [x] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=TreeTest`
Expected: PASS

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "encapsulate tree CSS and JS in Tree class"
```

## Chunk 2: Integrate Tree into OpenApiUiGenerator

### Task 7: Replace renderNode() with Tree API

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [x] **Step 1: Replace `renderNode()` with a new method using Tree**

Replace the `renderNode()` method (lines 187-225) and the `firstTreeItem` field (line 185) with a method that builds a `Tree` using the new API:

```java
import static com.github.t1.openapi.ui.Tree.tree;

private Tree buildTree(PathNode node) {
    var t = tree();
    addNodes(t, node, "");
    return t;
}

private void addNodes(Tree tree, PathNode node, String pathPrefix) {
    for (var entry : node.children.entrySet()) {
        var segment = entry.getKey();
        var child = entry.getValue();
        var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
        if (!child.children.isEmpty()) {
            tree.node(span(segment).classes("tree-segment"), sub -> {
                addOperationItems(sub, child, fullPath);
                addNodes(sub, child, fullPath);
            });
        } else {
            tree.item(span(segment).classes("tree-segment"));
            addOperationItems(tree, child, fullPath);
        }
    }
}

private void addNodes(Tree.Node node, PathNode pathNode, String pathPrefix) {
    for (var entry : pathNode.children.entrySet()) {
        var segment = entry.getKey();
        var child = entry.getValue();
        var fullPath = pathPrefix.isEmpty() ? segment : pathPrefix + "/" + segment;
        if (!child.children.isEmpty()) {
            node.node(span(segment).classes("tree-segment"), sub -> {
                addOperationItems(sub, child, fullPath);
                addNodes(sub, child, fullPath);
            });
        } else {
            node.item(span(segment).classes("tree-segment"));
            addOperationItems(node, child, fullPath);
        }
    }
}

private void addOperationItems(Tree tree, PathNode child, String fullPath) {
    for (var opEntry : child.operations.entrySet()) {
        var method = opEntry.getKey();
        var operation = opEntry.getValue();
        var summary = operation.getSummary();
        var badge = tag(method.name()).is(methodColor(method));
        var labelText = summary != null ? " — " + summary : "";
        tree.item(span().classes("tree-op-label").content(badge).content(labelText)
                .attr("hx-get", fullPath + "/" + method.name() + ".html")
                .attr("hx-target", "#detail")
                .attr("hx-swap", "innerHTML"));
    }
}

private void addOperationItems(Tree.Node node, PathNode child, String fullPath) {
    for (var opEntry : child.operations.entrySet()) {
        var method = opEntry.getKey();
        var operation = opEntry.getValue();
        var summary = operation.getSummary();
        var badge = tag(method.name()).is(methodColor(method));
        var labelText = summary != null ? " — " + summary : "";
        node.content(span().classes("tree-op-label").content(badge).content(labelText)
                .attr("hx-get", fullPath + "/" + method.name() + ".html")
                .attr("hx-target", "#detail")
                .attr("hx-swap", "innerHTML"));
    }
}
```

**Key distinction:** When a path segment has children, `addOperationItems` uses `node.content()` to place operations as siblings on the `<li>` (before the subtree `<ul>`). When a path is a leaf, operations go as separate `tree.item()` calls. This matches the current DOM structure.

- [x] **Step 2: Update CSS and JS references**

In the `generate()` method:
- Replace `TREE_KEYBOARD_JS` with `Tree.js()` (the mode toggle and send button JS stays in `TREE_KEYBOARD_JS`, renamed to something like `APP_JS`)
- Replace the tree portion of `CUSTOM_CSS` with `Tree.css()`, keeping the non-tree CSS in a separate constant

- [x] **Step 3: Remove moved code**

Remove from `OpenApiUiGenerator`:
- The `renderNode()` method
- The `firstTreeItem` field
- The tree-specific CSS from `CUSTOM_CSS`
- The tree-specific JS from `TREE_KEYBOARD_JS`

- [x] **Step 4: Run all tests**

Run: `mvn test -pl core`
Expected: All 30 tests pass — behavior is unchanged

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "use Tree component in OpenApiUiGenerator"
```

### Task 8: Clean code review and final refactor

- [x] **Step 1: Run clean code review**

Use the `clean-code-reviewer` agent on:
- `core/src/main/java/com/github/t1/openapi/ui/Tree.java`
- `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`
- `core/src/test/java/com/github/t1/openapi/ui/TreeTest.java`

- [x] **Step 2: Apply suggestions**

Apply any actionable suggestions from the review.

- [x] **Step 3: Run all tests**

Run: `mvn test -pl core`
Expected: All tests pass

- [x] **Step 4: Review screenshots**

Check `core/target/screenshots/` for visual regressions — the UI should look identical to before.

- [x] **Step 5: Commit if changes were made**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/Tree.java core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/TreeTest.java
git commit -m "refactor Tree component after clean code review"
```
