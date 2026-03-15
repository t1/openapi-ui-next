package com.github.t1.openapi.ui;

import com.github.t1.htmljava.AbstractElement;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;

import java.util.function.Consumer;

import static com.github.t1.htmljava.HtmlBasics.li;
import static com.github.t1.htmljava.HtmlBasics.span;

public class Tree extends AbstractElement<Tree> {
    private final boolean isRoot;
    private boolean firstItem = true;

    public static Tree tree() { return new Tree("tree").attr("tabindex", "0").attr("autofocus", ""); }

    private Tree(String role) {
        super("ul");
        attr("role", role);
        isRoot = "tree".equals(role);
    }

    public Tree item(String label) { return item(span(label)); }

    public Tree item(Renderable label) {
        var item = li().attr("role", "treeitem").content(label);
        markFirstItem(item);
        content(item);
        return this;
    }

    public Tree node(String label, Consumer<Node> children) { return node(span(label), children); }

    public Tree node(Renderable label, Consumer<Node> children) {
        var node = new Node(label);
        children.accept(node);
        var item = node.build();
        markFirstItem(item);
        content(item);
        return this;
    }

    private void markFirstItem(Element item) {
        if (isRoot && firstItem) {
            item.attr("aria-selected", "true");
            firstItem = false;
        }
    }

    public static class Node {
        private final Element item;
        private final Tree subtree;

        private Node(Renderable label) {
            item = li().attr("role", "treeitem").attr("aria-expanded", "true");
            item.content(span("\u25BC").classes("tree-toggle"));
            item.content(label);
            subtree = new Tree("group");
        }

        /** Add content as a sibling on the node's {@code <li>} (before the subtree) */
        public Node content(Renderable content) {
            item.content(content);
            return this;
        }

        public Node item(String label) { subtree.item(label); return this; }

        public Node item(Renderable label) { subtree.item(label); return this; }

        public Node node(String label, Consumer<Node> children) { subtree.node(label, children); return this; }

        public Node node(Renderable label, Consumer<Node> children) { subtree.node(label, children); return this; }

        Element build() {
            item.content(subtree);
            return item;
        }
    }

    public static String css() { return CSS; }

    public static String js() { return JS; }

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
                padding: 4px 0;
                line-height: 1.7;
            }
            [role="treeitem"] > span {
                cursor: pointer;
                padding: 3px 8px;
                border-radius: 4px;
            }
            [role="treeitem"] > span:hover {
                background-color: var(--bulma-scheme-main-ter);
            }
            [role="treeitem"][aria-selected="true"] > span:first-child {
                background-color: var(--bulma-link-light);
            }
            [role="tree"]:focus-visible [role="treeitem"][aria-selected="true"] > span:first-child {
                outline: 2px solid var(--bulma-link);
                outline-offset: 1px;
            }
            .tree-segment {
                font-weight: 600;
                color: var(--bulma-text-strong);
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.9rem;
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

    private static final String JS = """
            document.addEventListener('DOMContentLoaded', function() {
                var tree = document.querySelector('[role="tree"]');
                if (!tree) return;

                function isGroupVisible(el) {
                    while (el && el !== tree) {
                        if (el.parentElement && el.parentElement.getAttribute('aria-expanded') === 'false') return false;
                        el = el.parentElement;
                    }
                    return true;
                }

                function getVisibleItems() {
                    return Array.from(tree.querySelectorAll('[role="treeitem"]')).filter(function(item) {
                        return isGroupVisible(item);
                    });
                }

                function toggleNode(item, expand) {
                    var group = item.querySelector('[role="group"]');
                    if (!group) return;
                    item.setAttribute('aria-expanded', expand ? 'true' : 'false');
                    group.style.display = expand ? '' : 'none';
                }

                tree.addEventListener('click', function(e) {
                    var toggle = e.target.closest('.tree-toggle');
                    if (!toggle) return;
                    var item = toggle.closest('[role="treeitem"]');
                    if (!item) return;
                    var expanded = item.getAttribute('aria-expanded') === 'true';
                    toggleNode(item, !expanded);
                });

                tree.addEventListener('keydown', function(e) {
                    var items = getVisibleItems();
                    var current = tree.querySelector('[aria-selected="true"]');
                    var idx = items.indexOf(current);

                    switch (e.key) {
                        case 'ArrowDown':
                            e.preventDefault();
                            if (idx < items.length - 1) selectItem(items[idx + 1]);
                            break;
                        case 'ArrowUp':
                            e.preventDefault();
                            if (idx > 0) selectItem(items[idx - 1]);
                            break;
                        case 'ArrowRight':
                            e.preventDefault();
                            if (current.getAttribute('aria-expanded') === 'false') {
                                toggleNode(current, true);
                            }
                            break;
                        case 'ArrowLeft':
                            e.preventDefault();
                            if (current.getAttribute('aria-expanded') === 'true') {
                                toggleNode(current, false);
                            } else {
                                var parentGroup = current.closest('[role="group"]');
                                if (parentGroup) {
                                    var parentItem = parentGroup.closest('[role="treeitem"]');
                                    if (parentItem) selectItem(parentItem);
                                }
                            }
                            break;
                        case 'Enter':
                            e.preventDefault();
                            var hxEl = current.querySelector('[hx-get]') || current;
                            if (hxEl.getAttribute('hx-get')) htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail');
                            break;
                        case 'Escape':
                            e.preventDefault();
                            tree.focus();
                            break;
                    }
                });

                function selectItem(item) {
                    tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) {
                        el.removeAttribute('aria-selected');
                    });
                    item.setAttribute('aria-selected', 'true');
                }

                document.addEventListener('keydown', function(e) {
                    if (e.key === 'Escape' && !e.target.closest('[role="tree"]')) {
                        e.preventDefault();
                        tree.focus();
                    }
                });
            });
            """;
}
