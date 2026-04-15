# Pure Spatial Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor keyboard navigation so `findSpatialTarget` is component-agnostic and components handle their own internal keys with `stopPropagation`.

**Architecture:** Three layers: (1) generic `findSpatialTarget` discovers all focusable elements by screen position, (2) global bubble-phase handler calls it for unhandled arrow keys, (3) components handle internal keys with `stopPropagation` and let boundary events bubble.

**Tech Stack:** JavaScript (app.js, Tree.java embedded JS, Toggle.java embedded JS), Java (PathFragmentGenerator, OperationFragmentGenerator, new TabBar component)

**Spec:** `docs/superpowers/specs/2026-04-15-pure-spatial-navigation-design.md`

---

### Task 1: Add `stopPropagation` to Toggle's internal key handler

Toggle.java's keydown handler calls `preventDefault` but not `stopPropagation` for
ArrowLeft/Right/Home/End. Currently the global handler has an early return for toggles
(app.js line 2130), so this doesn't cause double-handling. But the global handler's
exclusion list will be removed later, so toggles must stop their own events first.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/components/Toggle.java:130-148`
- Test: existing browser tests (555 tests)

- [ ] **Step 1: Add `e.stopPropagation()` to Toggle's keydown handler**

In `Toggle.java`, in the JS string, add `e.stopPropagation()` alongside each
`e.preventDefault()` in the keydown handler. All four branches (ArrowRight, ArrowLeft,
Home, End):

```javascript
container.addEventListener('keydown', function(e) {
    var currentEl = container.querySelector('.is-active');
    var current = currentEl ? currentEl.getAttribute('data-toggle-value') : null;
    var idx = current ? values.indexOf(current) : -1;
    if (e.key === 'ArrowRight') {
        e.preventDefault();
        e.stopPropagation();
        var next = Math.min(idx + 1, values.length - 1);
        if (next !== idx) select(values[next]);
    } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        e.stopPropagation();
        var next = Math.max(idx - 1, 0);
        if (next !== idx) select(values[next]);
    } else if (e.key === 'Home') {
        e.preventDefault();
        e.stopPropagation();
        select(values[0]);
    } else if (e.key === 'End') {
        e.preventDefault();
        e.stopPropagation();
        select(values[values.length - 1]);
    }
});
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl core`
Expected: all 555 tests pass (no behavior change — the global handler already ignores
toggles via its exclusion list).

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/components/Toggle.java
git commit -m "add stopPropagation to Toggle keydown handler"
```

---

### Task 2: Add `stopPropagation` to Tree's internal key handling

Tree.java handles all arrow keys with `e.preventDefault()` but no `stopPropagation`.
Internal navigation must call `stopPropagation`. Boundary cases must NOT — the event
must bubble to the global handler.

Currently boundary cases hardcode targets. This task only adds `stopPropagation` to
internal cases; removing the hardcoded targets happens in Task 5.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/components/Tree.java:260-342`
- Test: existing browser tests

- [ ] **Step 1: Add `stopPropagation` to internal arrow key cases**

In Tree.java's keydown handler:

ArrowDown — always internal (including bump at bottom):
```javascript
case 'ArrowDown':
    e.preventDefault();
    e.stopPropagation();
    if (idx < items.length - 1) selectItem(items[idx + 1]);
    else bump(current, 'v');
    break;
```

ArrowUp — internal when not at first item, boundary at first item (keeps hardcoded
targets for now):
```javascript
case 'ArrowUp':
    e.preventDefault();
    if (idx > 0) {
        selectItem(items[idx - 1]);
        e.stopPropagation();
    } else {
        var filterIcon = document.querySelector('.filter-icon');
        if (filterIcon) filterIcon.focus();
        else {
            var viewToggle = document.querySelector('[data-toggle="view"]');
            if (viewToggle) viewToggle.focus();
            else bump(current, 'v');
        }
        e.stopPropagation();
    }
    break;
```

ArrowRight — internal when expanding, boundary when expanded leaf:
```javascript
case 'ArrowRight':
    e.preventDefault();
    if (current.getAttribute('aria-expanded') === 'false') {
        toggleNode(current, true);
        e.stopPropagation();
    } else {
        var firstTabLink = document.querySelector('.tabs li:first-child a');
        if (firstTabLink) {
            firstTabLink.focus();
            var tabHxGet = firstTabLink.getAttribute('hx-get');
            if (tabHxGet) history.replaceState(null, '', '#' + hxGetToRoute(tabHxGet));
        }
        else { focusFirstDetailField(); }
        e.stopPropagation();
    }
    break;
```

ArrowLeft — internal when collapsing or moving to parent, boundary at root:
```javascript
case 'ArrowLeft':
    e.preventDefault();
    if (current.getAttribute('aria-expanded') === 'true') {
        toggleNode(current, false);
        e.stopPropagation();
    } else {
        var parentGroup = current.closest('[role="group"]');
        if (parentGroup) {
            var parentItem = parentGroup.closest('[role="treeitem"]');
            if (parentItem) {
                selectItem(parentItem);
                e.stopPropagation();
            }
        }
    }
    break;
```

Tab, Enter, Escape — add `e.stopPropagation()` after `e.preventDefault()`.

- [ ] **Step 2: Run tests**

Run: `mvn test -pl core`
Expected: all 555 tests pass.

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/components/Tree.java
git commit -m "add stopPropagation to Tree internal key handling"
```

---

### Task 3: Add `stopPropagation` to filter icon and filter panel handlers

The filter icon handler (Enter/Space) and filter panel handler (Escape, ArrowDown,
ArrowUp, Tab) need `stopPropagation` for keys they handle.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:2738-2764`
- Test: existing browser tests

- [ ] **Step 1: Add `stopPropagation` to filter icon and panel handlers**

Filter icon handler — add `e.stopPropagation()`:
```javascript
filterIcon.addEventListener('keydown', function(e) {
    if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        e.stopPropagation();
        var wasOpen = filterPanel.classList.contains('is-active');
        filterPanel.classList.toggle('is-active');
        localStorage.setItem('openapi-ui-tag-filter-open', filterPanel.classList.contains('is-active'));
        if (!wasOpen && filterPanel.classList.contains('is-active')) {
            filterPanel.focus();
        }
    }
});
```

Filter panel handler — add `e.stopPropagation()` to all branches:
```javascript
filterPanel.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        filterPanel.classList.remove('is-active');
        localStorage.setItem('openapi-ui-tag-filter-open', 'false');
        filterIcon.focus();
    } else if (e.key === 'ArrowDown' || (e.key === 'Tab' && !e.shiftKey)) {
        e.preventDefault();
        e.stopPropagation();
        var treeEl = document.querySelector('[role="tree"]');
        if (treeEl) treeEl.focus();
    } else if (e.key === 'ArrowUp' || (e.key === 'Tab' && e.shiftKey)) {
        e.preventDefault();
        e.stopPropagation();
        filterIcon.focus();
    }
});
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl core`
Expected: all 555 tests pass.

- [ ] **Step 3: Commit**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "add stopPropagation to filter icon and panel handlers"
```

---

### Task 4: Make `findSpatialTarget` generic

Replace the hardcoded candidate selector with a generic focusable element query.
Remove all 8 component-specific exceptions.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:2048-2119`
- Test: existing browser tests

- [ ] **Step 1: Replace `findSpatialTarget` with generic implementation**

Replace the entire function:

```javascript
/**
 * Finds the nearest focusable element in the given direction from {@code el}'s
 * screen position, using corridor-based primary-axis distance with euclidean
 * fallback.
 *
 * IMPORTANT: This function must remain component-agnostic. No component-specific
 * selectors, exceptions, or rect overrides. Components that need to act as a
 * single spatial unit must be a single focusable element (tabindex="0" on the
 * container, no tabindex on children). Components handle their own internal keys
 * with stopPropagation; boundary navigation bubbles to the global handler which
 * calls this function.
 */
function findSpatialTarget(el, direction) {
    const rect = el.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;
    const candidateSelector =
        'input:not(:disabled), select:not(:disabled), textarea:not(:disabled),'
        + ' button:not(:disabled), a[href], [tabindex="0"]';
    const candidates = Array.from(document.querySelectorAll(candidateSelector)).filter(function(c) {
        if (c === el) return false;
        if (!c.offsetParent) return false;
        return true;
    });

    let best = null;
    let bestDist = Infinity;
    let bestCorridor = false;

    candidates.forEach(function(c) {
        const candidateRect = c.getBoundingClientRect();
        const candidateCenterX = candidateRect.left + candidateRect.width / 2;
        const candidateCenterY = candidateRect.top + candidateRect.height / 2;

        if (direction === 'down' && candidateCenterY <= centerY) return;
        if (direction === 'up' && candidateCenterY >= centerY) return;
        if (direction === 'right' && candidateCenterX <= centerX) return;
        if (direction === 'left' && candidateCenterX >= centerX) return;

        let corridor;
        if (direction === 'down' || direction === 'up') {
            corridor = rect.right > candidateRect.left && candidateRect.right > rect.left;
        } else {
            corridor = rect.bottom > candidateRect.top && candidateRect.bottom > rect.top;
        }

        let dist;
        if (corridor) {
            dist = (direction === 'down' || direction === 'up')
                ? Math.abs(candidateCenterY - centerY)
                : Math.abs(candidateCenterX - centerX);
        } else {
            dist = Math.sqrt(
                (candidateCenterX - centerX) * (candidateCenterX - centerX)
                + (candidateCenterY - centerY) * (candidateCenterY - centerY));
        }

        if (corridor && !bestCorridor) {
            best = c;
            bestDist = dist;
            bestCorridor = true;
        } else if (corridor === bestCorridor && dist < bestDist) {
            best = c;
            bestDist = dist;
        }
    });

    return best;
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -pl core`
Expected: all 555 tests pass. Components already have `stopPropagation` (Tasks 1-3).
Some tests may fail if spatial nav now finds unexpected candidates (e.g. individual
tab links that are still `tabindex="0"`). Note any failures — they are fixed in
Tasks 7-8 when tab containers become single focusable units.

- [ ] **Step 3: Commit (if all tests pass; otherwise defer to after Tasks 7-8)**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "make findSpatialTarget generic — remove all component-specific exceptions"
```

---

### Task 5: Remove hardcoded boundary targets from Tree

At boundaries, the tree does nothing — no `stopPropagation`, no `preventDefault`. The
event bubbles to the global handler which calls `findSpatialTarget`.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/components/Tree.java:267-349`
- Test: existing browser tests

- [ ] **Step 1: Change ArrowUp boundary to bubble**

```javascript
case 'ArrowUp':
    if (idx > 0) {
        e.preventDefault();
        e.stopPropagation();
        selectItem(items[idx - 1]);
    }
    // boundary: let event bubble to global spatial nav
    break;
```

- [ ] **Step 2: Change ArrowDown boundary to bubble**

```javascript
case 'ArrowDown':
    if (idx < items.length - 1) {
        e.preventDefault();
        e.stopPropagation();
        selectItem(items[idx + 1]);
    }
    // boundary: let event bubble to global spatial nav (bump applied by global handler)
    break;
```

- [ ] **Step 3: Change ArrowRight boundary to bubble**

```javascript
case 'ArrowRight':
    if (current.getAttribute('aria-expanded') === 'false') {
        e.preventDefault();
        e.stopPropagation();
        toggleNode(current, true);
    }
    // expanded/leaf: let event bubble to global spatial nav
    break;
```

Remove `focusFirstDetailField()` helper (lines 344-349) — no longer called.

- [ ] **Step 4: Change ArrowLeft boundary to bubble**

```javascript
case 'ArrowLeft':
    if (current.getAttribute('aria-expanded') === 'true') {
        e.preventDefault();
        e.stopPropagation();
        toggleNode(current, false);
    } else {
        var parentGroup = current.closest('[role="group"]');
        if (parentGroup) {
            var parentItem = parentGroup.closest('[role="treeitem"]');
            if (parentItem) {
                e.preventDefault();
                e.stopPropagation();
                selectItem(parentItem);
            }
        }
    }
    // root-level collapsed: let event bubble
    break;
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl core`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/components/Tree.java
git commit -m "remove hardcoded boundary targets from Tree — delegate to spatial nav"
```

---

### Task 6: Remove hardcoded targets from filter panel and view toggle

ArrowUp/Down in filter panel and view toggle should bubble to spatial nav.
Tab/Shift+Tab remain custom (sequential navigation).

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:1142-1166`
  (view toggle handler)
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:2751-2764`
  (filter panel handler)
- Test: existing browser tests

- [ ] **Step 1: Simplify view toggle handler — keep only Tab**

```javascript
viewToggle.addEventListener('keydown', function(e) {
    if (e.key === 'Tab') {
        e.preventDefault();
        e.stopPropagation();
        if (e.shiftKey) {
            const modeToggle = document.querySelector('[data-toggle="mode"]');
            if (modeToggle) modeToggle.focus();
        } else {
            const filterIcon = document.querySelector('.filter-icon');
            if (filterIcon) {
                filterIcon.focus();
            } else {
                const tree = document.querySelector('[role="tree"]');
                if (tree) tree.focus();
            }
        }
    }
    // ArrowUp/ArrowDown: let bubble to spatial nav
});
```

- [ ] **Step 2: Simplify filter panel handler — keep only Escape and Tab**

```javascript
filterPanel.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        filterPanel.classList.remove('is-active');
        localStorage.setItem('openapi-ui-tag-filter-open', 'false');
        filterIcon.focus();
    } else if (e.key === 'Tab') {
        e.preventDefault();
        e.stopPropagation();
        if (e.shiftKey) {
            filterIcon.focus();
        } else {
            var treeEl = document.querySelector('[role="tree"]');
            if (treeEl) treeEl.focus();
        }
    }
    // ArrowUp/ArrowDown: let bubble to spatial nav
});
```

- [ ] **Step 3: Run tests**

Run: `mvn test -pl core`
Expected: all tests pass.

- [ ] **Step 4: Commit**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "remove hardcoded boundary targets from view toggle and filter panel"
```

---

### Task 7: Create TabBar component

Extract a shared tab bar builder used by both method tabs and status tabs. Like Toggle,
it's a Java class that produces HTML + CSS + JS. The container gets `tabindex="0"`,
children do not. ArrowLeft/Right switches active tab with `stopPropagation`. ArrowUp/Down
bubbles to spatial nav.

The TabBar dispatches a `tabchange` CustomEvent with `{ detail: { value: <value> } }`
when the active tab changes, so callers can react (HTMX load, panel toggle, etc.).

**Files:**
- Create: `core/src/main/java/com/github/t1/openapi/ui/components/TabBar.java`
- Test: `core/src/test/java/com/github/t1/openapi/ui/components/TabBarTest.java`

- [ ] **Step 1: Write TabBarTest**

```java
package com.github.t1.openapi.ui.components;

import org.junit.jupiter.api.Test;

import static com.github.t1.openapi.ui.components.TabBar.tabBar;
import static org.assertj.core.api.BDDAssertions.then;

class TabBarTest {
    @Test void shouldRenderTabBarWithActiveItem() {
        var bar = tabBar("test-tabs")
                .activeTab("200", "200")
                .tab("404", "404");

        var html = bar.render();

        then(html).contains("tabindex=\"0\"");
        then(html).contains("class=\"tab-bar\"");
        then(html).contains("data-tab-bar=\"test-tabs\"");
        then(html).contains("is-active");
        then(html).doesNotContain("tabindex", org.assertj.core.api.Assertions.atIndex(1));
    }

    @Test void shouldNotSetTabindexOnChildren() {
        var bar = tabBar("test").activeTab("a", "A").tab("b", "B");

        var html = bar.render();

        // tabindex appears exactly once (on the container)
        then(html.split("tabindex").length - 1).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest=TabBarTest`
Expected: FAIL — `TabBar` class does not exist.

- [ ] **Step 3: Implement TabBar**

```java
package com.github.t1.openapi.ui.components;

import com.github.t1.htmljava.AbstractElement;
import com.github.t1.htmljava.Element;

import static com.github.t1.htmljava.HtmlBasics.span;

/// A tab bar that emits a `tabchange` CustomEvent when the active tab changes.
/// ArrowLeft/Right switches tabs internally (with stopPropagation).
/// ArrowUp/Down bubbles to spatial navigation.
public class TabBar extends AbstractElement<TabBar> {

    public static TabBar tabBar(String name) {return new TabBar(name);}

    private TabBar(String name) {
        super("div");
        classes("tab-bar");
        attr("data-tab-bar", name);
        attr("tabindex", "0");
    }

    public TabBar tab(String value, String label) {return tab(value, label, false);}

    public TabBar activeTab(String value, String label) {return tab(value, label, true);}

    private TabBar tab(String value, String label, boolean active) {
        var item = span(label).attr("data-tab-value", value);
        if (active) item.classes("is-active");
        content(item);
        return this;
    }

    public static String js() {return JS;}

    private static final String JS = """
            document.addEventListener('keydown', function(e) {
                var tabBar = e.target.closest('[data-tab-bar]');
                if (!tabBar) return;
                if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
                e.preventDefault();
                e.stopPropagation();
                var items = Array.from(tabBar.querySelectorAll('[data-tab-value]'));
                var activeIndex = items.findIndex(function(t) { return t.classList.contains('is-active'); });
                var next = e.key === 'ArrowRight'
                    ? Math.min(activeIndex + 1, items.length - 1)
                    : Math.max(activeIndex - 1, 0);
                if (next !== activeIndex) {
                    items.forEach(function(t) { t.classList.remove('is-active'); });
                    items[next].classList.add('is-active');
                    tabBar.dispatchEvent(new CustomEvent('tabchange', { detail: { value: items[next].getAttribute('data-tab-value') } }));
                }
            });
            """;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest=TabBarTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/components/TabBar.java
git add core/src/test/java/com/github/t1/openapi/ui/components/TabBarTest.java
git commit -m "add TabBar component for keyboard-navigable tab bars"
```

---

### Task 8: Convert method tabs to use TabBar

Replace the ad-hoc tab creation in PathFragmentGenerator with the TabBar component.
Update the focusin and activation logic in app.js.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/PathFragmentGenerator.java`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
  (focusin handler, add tabchange listener)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java`
  (include TabBar JS)
- Test: existing browser tests

- [ ] **Step 1: Include TabBar JS in generated output**

In `OpenApiUiGenerator.java`, find where Toggle.js() and Tree.js() are included in the
page script, and add `TabBar.js()` alongside them.

- [ ] **Step 2: Replace PathFragmentGenerator tab creation**

Currently `PathFragmentGenerator.java` builds tabs manually with `<ul>/<li>/<a>`. The
method tabs still need `<a>` elements for HTMX loading (hx-get), so the TabBar's
children need to be `<a>` elements, not `<span>`. Adjust: instead of using TabBar's
`tab()`/`activeTab()` methods, build the tab bar container with its attributes and
add custom children:

Actually, the method tabs need HTMX attributes on each tab link, which TabBar doesn't
support. Use TabBar for the container setup (tabindex, data-tab-bar) but add children
manually:

```java
var tabBar = element("div").classes("tab-bar").attr("data-tab-bar", "method-tabs").attr("tabindex", "0");
var first = true;
for (var opEntry : operations.entrySet()) {
    var method = opEntry.getKey();
    var tab = element("a").content(method.name())
            .attr("data-tab-value", method.name())
            .attr("data-method", method.name())
            .attr("hx-get", path + "/" + method.name() + ".html")
            .attr("hx-target", "#method-content")
            .attr("hx-swap", "innerHTML");
    if (first) tab.classes("is-active");
    tabBar.content(tab);
    first = false;
}
```

This uses the same `data-tab-value` attribute as TabBar so the JS handler works.
The container is a `div.tab-bar[data-tab-bar]` with `tabindex="0"`.

Remove the old `<ul>/<li>` structure and the `<div class="tabs">` wrapper.

- [ ] **Step 3: Add tabchange listener for method tabs**

In app.js, add a listener for the `tabchange` event on method tab bars. This replaces
the old focusin handler:

```javascript
document.addEventListener('tabchange', function(e) {
    var tabBar = e.target.closest('[data-tab-bar="method-tabs"]');
    if (!tabBar) return;
    var value = e.detail.value;
    var tab = tabBar.querySelector('[data-tab-value="' + value + '"]');
    if (!tab) return;
    clearPreviousResponse();
    var hxGet = tab.getAttribute('hx-get');
    if (hxGet) {
        htmx.ajax('GET', hxGet, tab.getAttribute('hx-target'));
        history.replaceState(null, '', '#' + hxGetToRoute(hxGet));
    }
});
```

Also add a focusin handler so the active tab's content loads when the tab bar gets
focus (e.g. via spatial nav from the tree):

```javascript
document.addEventListener('focusin', function(e) {
    var tabBar = e.target.closest('[data-tab-bar="method-tabs"]');
    if (!tabBar) return;
    var activeTab = tabBar.querySelector('.is-active');
    if (!activeTab) return;
    var hxGet = activeTab.getAttribute('hx-get');
    if (hxGet) {
        htmx.ajax('GET', hxGet, activeTab.getAttribute('hx-target'));
        history.replaceState(null, '', '#' + hxGetToRoute(hxGet));
    }
});
```

Remove the old focusin handler for `.tabs a` (lines 2275-2288).

- [ ] **Step 4: Update Tree Tab handler and global handler**

In Tree.java, update Tab handler to focus `[data-tab-bar]` instead of `.tabs .is-active a`:
```javascript
} else {
    var tabBar = document.querySelector('[data-tab-bar]');
    if (tabBar) tabBar.focus();
    else {
        var detail = document.getElementById('detail');
        if (detail) {
            var first = detail.querySelector('input, select, textarea, button[type=submit]');
            if (first) first.focus();
        }
    }
}
```

Update Tree Enter handler similarly — focus `[data-tab-bar]` instead of `.tabs .is-active a`.

In the global handler, update the Tab-from-tabs check: change `el.closest('.tabs')`
to `el.closest('[data-tab-bar]')`.

- [ ] **Step 5: Update CSS for method tabs**

The old method tabs used Bulma's `.tabs` class for styling. The new `tab-bar` needs
equivalent CSS. Add to app.css or inline:

```css
.tab-bar {
    display: flex;
    gap: 0;
    border-bottom: 1px solid var(--bulma-border);
}
.tab-bar > [data-tab-value] {
    padding: 0.5em 1em;
    cursor: pointer;
    border-bottom: 2px solid transparent;
    color: var(--bulma-text);
    text-decoration: none;
}
.tab-bar > [data-tab-value].is-active {
    border-bottom-color: var(--bulma-link);
    color: var(--bulma-link);
}
.tab-bar > [data-tab-value]:hover {
    border-bottom-color: var(--bulma-text-strong);
}
```

Actually, the existing Bulma `.tabs` styling is better. Keep using the Bulma `tabs`
class on the container for visual styling, but add `data-tab-bar` and `tabindex` for
keyboard behavior. This avoids duplicating Bulma styles:

```java
var tabBar = element("div").classes("tabs").attr("data-tab-bar", "method-tabs").attr("tabindex", "0");
var tabList = element("ul");
// ... add li/a items to tabList ...
tabBar.content(tabList);
```

This keeps Bulma styling while adding tab-bar keyboard behavior. Update TabBar.js()
to handle this structure (items are `a` elements inside `li` elements, active class
on `li`).

Adjust the TabBar JS to work with both flat children and nested li/a structures. Use
a more generic approach: find items by `[data-tab-value]` regardless of nesting:

The TabBar.js already does this — it queries `[data-tab-value]` within the container.
The active class management needs to handle both the item and its parent `li`:

Update TabBar.js:
```javascript
if (next !== activeIndex) {
    items.forEach(function(t) {
        t.classList.remove('is-active');
        var li = t.closest('li');
        if (li) li.classList.remove('is-active');
    });
    items[next].classList.add('is-active');
    var li = items[next].closest('li');
    if (li) li.classList.add('is-active');
    tabBar.dispatchEvent(new CustomEvent('tabchange', { detail: { value: items[next].getAttribute('data-tab-value') } }));
}
```

- [ ] **Step 6: Run tests**

Run: `mvn test -pl core`
Expected: all tests pass.

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/generator/PathFragmentGenerator.java
git add core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git add core/src/main/java/com/github/t1/openapi/ui/components/TabBar.java
git add core/src/main/java/com/github/t1/openapi/ui/components/Tree.java
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "convert method tabs to TabBar component"
```

---

### Task 9: Convert status tabs to use TabBar

Replace the ad-hoc status tab creation in OperationFragmentGenerator.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java:827-835`
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`
- Test: existing browser tests

- [ ] **Step 1: Replace statusCodeTabs with TabBar**

In `OperationFragmentGenerator.java`, replace `statusCodeTabs`:

```java
private Element statusCodeTabs(List<String> statusCodes) {
    var tabs = element("div").classes("schema-status-tabs")
            .attr("data-tab-bar", "status-tabs").attr("tabindex", "0");
    for (var code : statusCodes) {
        var tab = span(code).classes("schema-status-tab")
                .attr("data-tab-value", code).attr("data-status", code);
        if (statusCodes.getFirst().equals(code)) tab.classes("is-active");
        tabs.content(tab);
    }
    return tabs;
}
```

Key changes: add `data-tab-bar="status-tabs"` and `tabindex="0"` on the container.
Remove `tabindex="0"` from individual spans.

- [ ] **Step 2: Add tabchange listener for status tabs**

In app.js, add a listener:

```javascript
document.addEventListener('tabchange', function(e) {
    var tabBar = e.target.closest('[data-tab-bar="status-tabs"]');
    if (!tabBar) return;
    var value = e.detail.value;
    var tab = tabBar.querySelector('[data-tab-value="' + value + '"]');
    if (!tab) return;
    activateStatusTab(tab);
});
```

Update `activateStatusTab` to not call `tab.focus()` (the container stays focused):

```javascript
function activateStatusTab(tab) {
    const tabs = tab.closest('.schema-status-tabs');
    tabs.querySelectorAll('.schema-status-tab').forEach(function(t) { t.classList.remove('is-active'); });
    tab.classList.add('is-active');
    const box = tab.closest('.schema-box');
    box.querySelectorAll('.schema-status-panel').forEach(function(p) { p.style.display = 'none'; });
    const panel = box.querySelector('.schema-status-panel[data-status="' + tab.textContent + '"]');
    if (panel) panel.style.display = '';
}
```

- [ ] **Step 3: Remove status-tab-specific code from global handler**

Remove from the global handler:
1. The Tab/Shift+Tab on status tab block (lines 2136-2157)
2. The Shift+Tab into status tabs block (lines 2187-2211)

Remove from the mode toggle handler (lines 432-446) the status tab scanning logic.
Simplify to:
```javascript
modeContainer.addEventListener('keydown', function(e) {
    if (e.key === 'Enter') {
        e.preventDefault();
        e.stopPropagation();
        if (modeSendButton) modeSendButton.click();
    }
});
```

Remove the old focusin handler for status tabs (lines 2290-2294).

- [ ] **Step 4: Run tests**

Run: `mvn test -pl core`
Expected: all tests pass.

- [ ] **Step 5: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "convert status tabs to TabBar component"
```

---

### Task 10: Switch global handler to bubble phase and clean up

Remove the exclusion list, switch to bubble phase, move Escape to detail pane.

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js:2122-2269`
- Test: existing browser tests

- [ ] **Step 1: Remove exclusion list**

Remove from the global handler (lines 2126-2133):
```javascript
// REMOVE all of these:
if (el.closest('[role="tree"]')) return;
if (el.matches('[data-toggle="mode"]') && (...)) return;
if (el.closest('.toggle') && !el.matches('[data-toggle="mode"]')) return;
var serverSel = el.closest('#server-selector');
if (serverSel && serverSel.classList.contains('is-active')) return;
```

- [ ] **Step 2: Switch to bubble phase**

Change `}, true);` at the end of the global handler to `});`.

- [ ] **Step 3: Move Escape to detail pane**

Remove the Escape handler from the global handler. Add a new handler:

```javascript
// Escape from detail area: return focus to tree
document.addEventListener('keydown', function(e) {
    if (e.key !== 'Escape') return;
    var el = document.activeElement;
    if (!el) return;
    if (!el.closest('#method-content') && !el.closest('#detail') && !el.closest('.tabs') && !el.closest('[data-tab-bar]')) return;
    e.preventDefault();
    var tree = document.querySelector('[role="tree"]');
    if (tree) tree.focus();
});
```

- [ ] **Step 4: Add JSDoc to global handler**

```javascript
/**
 * Global spatial navigation handler (bubble phase).
 *
 * Components handle their own internal keys (ArrowLeft/Right for toggles and
 * tab bars, ArrowUp/Down within the tree, etc.) and call stopPropagation.
 * This handler only fires for keys that no component claimed — typically
 * arrow keys at component boundaries, or arrow keys on plain form fields.
 *
 * Also handles Enter/Space activation (not spatial nav, but co-located).
 */
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl core`
Expected: all 555 tests pass.

- [ ] **Step 6: Commit**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "switch global handler to bubble phase, remove exclusion list"
```

---

### Task 11: Final verification and squash

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl core`
Expected: all 555 tests pass, 0 failures.

- [ ] **Step 2: Review screenshots**

Check screenshots in `core/target/junit-jupiter/` for any visual regressions.

- [ ] **Step 3: Squash commits**

Squash all commits from Tasks 1-10 into a single commit:
```
git rebase -i HEAD~10
```
Message: `refactor spatial navigation to be component-agnostic`

- [ ] **Step 4: Verify squashed commit**

Run: `mvn test -pl core`
Expected: all tests pass.
