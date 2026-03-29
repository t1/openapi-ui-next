package com.github.t1.openapi.ui.components;

import com.github.t1.htmljava.AbstractElement;
import com.github.t1.htmljava.Element;
import com.github.t1.htmljava.Renderable;

import java.util.function.Consumer;

import static com.github.t1.htmljava.HtmlBasics.li;
import static com.github.t1.htmljava.HtmlBasics.span;

public class Tree extends AbstractElement<Tree> implements TreeContainer {
    private final boolean isRoot;

    public static Tree tree() {return new Tree("tree").attr("tabindex", "0").attr("autofocus", "");}

    private Tree(String role) {
        super("ul");
        attr("role", role);
        isRoot = "tree".equals(role);
    }

    public Tree item(String label) {return item(span(label));}

    public Tree item(Renderable label) {
        var item = li().attr("role", "treeitem").content(span().classes("tree-label").content(label));
        markFirstItem(item);
        content(item);
        return this;
    }

    public Tree item(Renderable label, Consumer<Element> extra) {
        var treeLabel = span().classes("tree-label").content(label);
        extra.accept(treeLabel);
        var item = li().attr("role", "treeitem").content(treeLabel);
        markFirstItem(item);
        content(item);
        return this;
    }

    public Tree node(String label, Consumer<Node> children) {return node(span(label), children);}

    public Tree node(Renderable label, Consumer<Node> children) {
        var node = new Node(label);
        children.accept(node);
        var item = node.build();
        markFirstItem(item);
        content(item);
        return this;
    }

    private void markFirstItem(Element item) {
        if (isRoot && content() == null) {
            item.attr("aria-selected", "true");
        }
    }

    public static class Node implements TreeContainer {
        private final Element item;
        private final Tree subtree;

        private final Element treeLabel;

        private Node(Renderable label) {
            item = li().attr("role", "treeitem").attr("aria-expanded", "false");
            treeLabel = span().classes("tree-label");
            treeLabel.content(span("\u25BC").classes("tree-toggle"));
            treeLabel.content(label);
            item.content(treeLabel);
            subtree = new Tree("group");
        }

        /** Add content as a sibling on the node's {@code <li>} (before the subtree) */
        public Node content(Renderable content) {
            treeLabel.content(content);
            return this;
        }

        public Node item(String label) {
            subtree.item(label);
            return this;
        }

        public Node item(Renderable label) {
            subtree.item(label);
            return this;
        }

        public Node item(Renderable label, Consumer<Element> extra) {
            subtree.item(label, extra);
            return this;
        }

        public Node node(String label, Consumer<Node> children) {
            subtree.node(label, children);
            return this;
        }

        public Node node(Renderable label, Consumer<Node> children) {
            subtree.node(label, children);
            return this;
        }

        Element build() {
            item.content(subtree);
            return item;
        }
    }

    public static String css() {return CSS;}

    public static String js() {return JS;}

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
            }
            [role="treeitem"] {
                padding: 6px 0 6px 8px;
                margin: 1px 0;
                line-height: 1.7;
                border-radius: 4px;
                cursor: pointer;
                transition: background 0.15s;
            }
            [role="treeitem"]:hover:not([aria-selected="true"]) {
                background-color: var(--bulma-scheme-main-ter);
            }
            .tree-label {
                display: flex;
                align-items: center;
                width: 100%;
                padding: 4px 4px 4px 12px;
            }
            .tree-label > span:not(.tree-toggle) {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                flex: 1;
            }
            [role="treeitem"][aria-selected="true"] > .tree-label {
                background: linear-gradient(90deg, var(--bulma-link) 3px, color-mix(in srgb, var(--bulma-link) 14%, transparent) 3px);
                border-radius: 4px;
            }
            [role="tree"]:focus {
                outline: none;
            }
            [role="tree"]:focus [role="treeitem"][aria-selected="true"] > .tree-label {
                box-shadow: inset 0 0 0 2px var(--bulma-link);
            }
            .tree-segment {
                font-weight: 700;
                color: var(--bulma-text-strong);
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.9rem;
            }
            .tree-param {
                font-weight: 400;
                color: var(--bulma-text-weak);
                font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
                font-size: 0.85rem;
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
            [role="treeitem"][aria-expanded="false"] > .tree-label > .tree-toggle {
                transform: rotate(-90deg);
            }
            [role="treeitem"][aria-expanded="false"] > [role="group"] {
                display: none;
            }
            """;

    private static final String JS = """
            function hxGetToRoute(hxGet) {
                return hxGet.replace(/\\/index\\.html$/, '').replace(/\\.html$/, '');
            }
            function bump(el, dir) {
                var cls = dir === 'h' ? 'bump-h' : 'bump-v';
                el.classList.remove(cls);
                void el.offsetWidth;
                el.classList.add(cls);
                setTimeout(function() { el.classList.remove(cls); }, 250);
            }
            document.addEventListener('DOMContentLoaded', function() {
                var treeContainer = document.getElementById('tree-container');
            
                function getTree() {
                    return document.querySelector('[role="tree"]');
                }
            
                function isGroupVisible(el) {
                    var tree = getTree();
                    while (el && el !== tree) {
                        if (el.parentElement && el.parentElement.getAttribute('aria-expanded') === 'false') return false;
                        el = el.parentElement;
                    }
                    return true;
                }
            
                function getVisibleItems() {
                    var tree = getTree();
                    if (!tree) return [];
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
            
                // Delegate click from tree-container (survives HTMX swaps)
                var clickTarget = treeContainer || document;
                clickTarget.addEventListener('click', function(e) {
                    var tree = getTree();
                    if (!tree || !tree.contains(e.target)) return;
                    var toggle = e.target.closest('.tree-toggle');
                    if (toggle) {
                        var item = toggle.closest('[role="treeitem"]');
                        if (item) {
                            var expanded = item.getAttribute('aria-expanded') === 'true';
                            toggleNode(item, !expanded);
                        }
                        return;
                    }
                    var item = e.target.closest('[role="treeitem"]');
                    if (item) {
                        selectItem(item);
                        tree.focus();
                    }
                });
            
                // Delegate keydown from tree-container
                clickTarget.addEventListener('keydown', function(e) {
                    var tree = getTree();
                    if (!tree || !tree.contains(e.target)) return;
                    var items = getVisibleItems();
                    var current = tree.querySelector('[aria-selected="true"]');
                    var idx = items.indexOf(current);
            
                    switch (e.key) {
                        case 'ArrowDown':
                            e.preventDefault();
                            if (idx < items.length - 1) selectItem(items[idx + 1]);
                            else bump(current, 'v');
                            break;
                        case 'ArrowUp':
                            e.preventDefault();
                            if (idx > 0) selectItem(items[idx - 1]);
                            else {
                                var viewToggle = document.querySelector('[data-toggle="view"]');
                                if (viewToggle) viewToggle.focus();
                                else bump(current, 'v');
                            }
                            break;
                        case 'ArrowRight':
                            e.preventDefault();
                            if (current.getAttribute('aria-expanded') === 'false') {
                                toggleNode(current, true);
                            } else {
                                var firstTabLink = document.querySelector('.tabs li:first-child a');
                                if (firstTabLink) {
                                    firstTabLink.focus();
                                    var tabHxGet = firstTabLink.getAttribute('hx-get');
                                    if (tabHxGet) history.replaceState(null, '', '#' + hxGetToRoute(tabHxGet));
                                }
                                else { focusFirstDetailField(); }
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
                        case 'Tab':
                            e.preventDefault();
                            if (e.shiftKey) {
                                var viewToggle = document.querySelector('[data-toggle="view"]');
                                if (viewToggle) viewToggle.focus();
                                else {
                                    var modeToggle = document.querySelector('[data-toggle="mode"]');
                                    if (modeToggle) modeToggle.focus();
                                }
                            } else {
                                var activeTabLink = document.querySelector('.tabs .is-active a');
                                if (activeTabLink) activeTabLink.focus();
                                else focusFirstDetailField();
                            }
                            break;
                        case 'Enter':
                            e.preventDefault();
                            var activeTabLink = document.querySelector('.tabs .is-active a');
                            if (activeTabLink) {
                                activeTabLink.focus();
                            } else {
                                var hxEl = current.querySelector('[hx-get]') || current;
                                if (hxEl.getAttribute('hx-get')) htmx.ajax('GET', hxEl.getAttribute('hx-get'), '#detail');
                            }
                            break;
                        case 'Escape':
                            e.preventDefault();
                            tree.focus();
                            break;
                    }
                });
            
                function focusFirstDetailField() {
                    var detail = document.getElementById('detail');
                    if (!detail) return;
                    var first = detail.querySelector('input, select, textarea, button[type=submit]');
                    if (first) first.focus();
                }
            
                function selectItem(item) {
                    var tree = getTree();
                    if (tree) {
                        tree.querySelectorAll('[aria-selected="true"]').forEach(function(el) {
                            el.removeAttribute('aria-selected');
                        });
                    }
                    item.setAttribute('aria-selected', 'true');
                    var hxEl = item.querySelector('[hx-get]') || item;
                    var hxGet = hxEl.getAttribute('hx-get');
                    if (hxGet) {
                        var route = hxGetToRoute(hxGet);
                        var tag = hxEl.getAttribute('data-tag');
                        var hash = tag ? '#[' + tag + ']' + route : '#' + route;
                        htmx.ajax('GET', hxGet, {target: '#detail', swap: 'innerHTML'});
                        history.pushState(null, '', hash);
                    }
                }

                function expandParentsOf(item) {
                    var parent = item.parentElement;
                    while (parent) {
                        var parentItem = parent.closest('[role="treeitem"]');
                        if (parentItem && parentItem.getAttribute('aria-expanded') === 'false') {
                            toggleNode(parentItem, true);
                        }
                        parent = parentItem ? parentItem.parentElement : null;
                    }
                }

                // Expose tree API for app.js (hash navigation)
                function attachTreeApi() {
                    var tree = getTree();
                    if (!tree || tree._selectItem) return;
                    tree._selectItem = selectItem;
                    tree._expandParentsOf = expandParentsOf;
                    tree.addEventListener('focus', function() {
                        if (window._hashNavPending) return;
                        var t = getTree();
                        var selected = t ? t.querySelector('[aria-selected="true"]') : null;
                        if (!selected) return;
                        var hxEl = selected.querySelector('[hx-get]') || selected;
                        var hxGet = hxEl.getAttribute('hx-get');
                        if (!hxGet) return;
                        var tag = hxEl.getAttribute('data-tag');
                        var route = hxGetToRoute(hxGet);
                        var hash = tag ? '#[' + tag + ']' + route : '#' + route;
                        history.replaceState(null, '', hash);
                    });
                }
                attachTreeApi();
                document.body.addEventListener('htmx:afterSwap', function(e) {
                    if (e.detail.target && e.detail.target.id === 'tree-container') attachTreeApi();
                });

                document.addEventListener('keydown', function(e) {
                    if (e.key === 'Escape' && !e.target.closest('[role="tree"]')) {
                        e.preventDefault();
                        var tree = getTree();
                        if (tree) tree.focus();
                    }
                });
            });
            """;
}
