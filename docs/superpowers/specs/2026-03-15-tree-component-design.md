# Tree Component Design

## Goal

Extract the custom tree UI (currently inline in `OpenApiUiGenerator`) into a standalone, generic `Tree` class in `core`, designed like bulma-java components.

## Location

`com.github.t1.openapi.ui.Tree` in `core/src/main/java/`.

## API

```java
public class Tree extends AbstractElement<Tree> {
    // Factory
    public static Tree tree() { ... }

    // Add a leaf item with a label
    public Tree item(String label) { ... }
    public Tree item(Renderable label) { ... }

    // Add a collapsible node with a label and children
    public Tree node(String label, Consumer<Node> children) { ... }
    public Tree node(Renderable label, Consumer<Node> children) { ... }

    // Encapsulated CSS and JS (static — class-level constants)
    public static String css() { ... }
    public static String js() { ... }

    // Inner class for building nodes with sibling content + subtree
    public static class Node {
        public Node content(Renderable sibling) { ... }  // adds to <li> before subtree
        public Node item(String label) { ... }            // adds to subtree
        public Node item(Renderable label) { ... }        // adds to subtree
        public Node node(String label, Consumer<Node> children) { ... }
        public Node node(Renderable label, Consumer<Node> children) { ... }
    }
}
```

Follows bulma-java conventions:
- Extends `AbstractElement<Tree>` for fluent builder and `.render()`
- Static factory method `tree()`
- HTML uses `<ul role="tree">`, `<li role="treeitem">`, ARIA attributes, toggle indicator
- Root tree gets `autofocus` and `tabindex="0"`
- First item automatically gets `aria-selected="true"`

## Encapsulation

- **CSS** (~55 lines): tree indentation, hover/selection, toggle animation. Generic tree styling only — app-specific styles (e.g. `.tree-op-label`) stay in the generator.
- **JS** (~85 lines): keyboard navigation (arrow keys, enter, escape), click-to-toggle, global escape handler. App-specific JS (mode toggle, send button, htmx) stays in the generator.
- Accessible via `Tree.css()` and `Tree.js()` (static methods).

## Usage in OpenApiUiGenerator

The generator builds the tree by calling `tree()`, then `.node()` / `.item()` for each path segment and operation. OpenAPI-specific content (HTTP method badges, HTMX attributes) is passed as `Renderable` labels or via `Node.content()` for sibling content on branch nodes.

## What Stays in OpenApiUiGenerator

- `PathNode` data structure and path-splitting logic
- OpenAPI-specific rendering (method badges, `hx-get` attributes, `.tree-op-label` CSS)
- Page layout, other CSS, HTMX setup, mode toggle JS, send button JS
