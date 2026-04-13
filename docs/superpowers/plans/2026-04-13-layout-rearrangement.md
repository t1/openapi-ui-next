# Layout Rearrangement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Server selector from a full-width panel to a Bulma dropdown in the hero top-right, and move the Mode selector down near the Send button.

**Architecture:** The Server selector changes from a `panel()` to a `dropdown()` in the detail-header. The Mode selector moves from the detail-header into each operation form's response-area (near Send). Both changes touch `OpenApiUiGenerator.java` (page layout + server HTML), `OperationFragmentGenerator.java` (mode selector placement), `app.css`, `app.js`, `AppFixture.java`, and `BrowserTest.java`.

**Tech Stack:** Java (bulma-java library), CSS, JavaScript, Playwright (browser tests)

---

### Task 1: Move Mode Selector from Detail Header to Operation Form

The mode selector currently lives in the detail-header (hero area). Move it into each operation fragment's response-area, next to the Send button.

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java:91-113` (remove modeSelector from detailHeader, pass it to fragment generation)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java:161-183` (add modeSelector to response-area)
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/PathFragmentGenerator.java` (pass modeSelector through)
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css` (adjust mode-selector-container positioning)

**Note:** The mode selector is created once from `OpenApiUiGenerator.modeSelector()` and currently placed in the detail-header. Since it needs to appear in each operation fragment, it must move to the page-level layout area that is always visible, not inside the per-operation fragments. The simplest approach: keep generating it once in `pageLayout()`, but place it inside the split-layout's second pane (the detail area) rather than the detail-header. It stays always visible since it's outside the htmx-swapped `#detail` div.

Actually, re-reading the code: the mode selector is placed in `.detail-header` which is above the split-layout. The `#detail` div is inside the split-layout's second pane and gets swapped via htmx. So the mode selector needs to move to a position that is:
1. Always visible (not inside `#detail`)
2. Near the Send button visually

The best approach: place it below the `#detail` div but inside the split-layout's second pane, so it's always visible in the operation area. Or: place it inside the split-second wrapper, after `#detail`. The JS already finds it via `document.querySelector('[data-toggle="mode"]')`, so the position doesn't matter for functionality.

- [ ] **Step 1: Write a failing test**

In `BrowserTest.java`, in a suitable existing nested class (e.g. `GivenAppWithOneGet`), add a test that the mode selector is inside the detail pane area, not the detail-header:

```java
@Test void shouldShowModeSelectorInDetailPane() {
    then(app.isModeSelectorInDetailPane()).isTrue();
}
```

Add to `AppFixture.java`:

```java
boolean isModeSelectorInDetailPane() {
    return page.locator(".split-second .mode-selector-container").count() > 0;
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldShowModeSelectorInDetailPane'`
Expected: FAIL — mode selector is currently in `.detail-header`, not `.split-second`

- [ ] **Step 3: Move mode selector in pageLayout()**

In `OpenApiUiGenerator.java`, modify `pageLayout()` (line ~91-113):

Change from:
```java
var detailHeader = div().classes("detail-header").content(title(pageTitle), modeSelector);
var splitLayout = splitPane()
        .first(box().content(viewToggle, treeContainer))
        .second(detail)
        .persistAs("openapi-ui-tree-width");
```

To:
```java
var detailHeader = div().classes("detail-header").content(title(pageTitle));
var detailPane = div().classes("detail-pane").content(detail, modeSelector);
var splitLayout = splitPane()
        .first(box().content(viewToggle, treeContainer))
        .second(detailPane)
        .persistAs("openapi-ui-tree-width");
```

Update the `AppFixture` method `isModeSelectorInDetailPane()` if the CSS class changes.

- [ ] **Step 4: Add CSS for the new position**

In `app.css`, add positioning for the mode selector in the detail pane. It should appear at the bottom of the detail area, visually near where the Send button appears:

```css
.detail-pane {
    display: flex;
    flex-direction: column;
}
.detail-pane .mode-selector-container {
    align-self: flex-end;
    margin-top: 0.5rem;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldShowModeSelectorInDetailPane'`
Expected: PASS

- [ ] **Step 6: Run all tests to check for regressions**

Run: `mvn test -pl core`
Expected: All tests pass (except the pre-existing `shouldFocusFirstPillWhenOpeningPanel` flake)

- [ ] **Step 7: Review screenshots**

Check `core/target/screenshots/` for visual correctness of the mode selector placement.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "move mode selector from hero to detail pane"
```

---

### Task 2: Replace Server Panel with Bulma Dropdown in Hero

Replace the full-width `panel()` server selector with a Bulma `dropdown()` in the detail-header (top-right, where the mode selector used to be).

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java:91-235` (detailHeader + serverSelector method)
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css` (remove flat-panel server CSS, add dropdown styling)
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` (update server toggle logic from panel expand to dropdown toggle)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java` (update server-related selectors)
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` (update server tests)

This is a large task. Breaking it into sub-steps:

#### 2a: Update HTML generation — no-servers case

- [ ] **Step 1: Write a failing test for server dropdown in header**

In `BrowserTest.java`, in `GivenAppWithOneGet` (which has no servers defined), add:

```java
@Test void shouldShowServerDropdownInHeader() {
    then(app.isServerDropdownInHeader()).isTrue();
}
```

Add to `AppFixture.java`:

```java
boolean isServerDropdownInHeader() {
    return page.locator(".detail-header #server-selector.dropdown").count() > 0;
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldShowServerDropdownInHeader'`
Expected: FAIL — server selector is currently a `panel`, not a `dropdown` in the header

- [ ] **Step 3: Rewrite serverSelector() for the no-servers case**

In `OpenApiUiGenerator.java`, rewrite `serverSelector()` to return a Bulma `dropdown()` instead of a `panel()`. For the no-servers case:

```java
private static Renderable serverSelector(OpenAPI openApi) {
    var servers = openApi.getServers();

    var trigger = button("").classes("server-dropdown-trigger")
            .attr("aria-haspopup", "true").attr("aria-controls", "server-dropdown-menu");
    var menu = div().id("server-dropdown-menu").classes("dropdown-menu").attr("role", "menu");
    var menuContent = div().classes("dropdown-content");
    menu.content(menuContent);

    if (servers == null || servers.isEmpty()) {
        var originUrl = "window.location.origin"; // resolved by JS
        trigger.content(span("").classes("server-url"), span("▾"));
        var item = element("a").classes("dropdown-item", "is-active")
                .attr("data-url", "")
                .content(
                        div().classes("server-item-url"),
                        div().classes("server-item-description").content("resolved from origin"));
        menuContent.content(item);
    } else {
        // ... handled in next sub-step
    }

    menuContent.content(element("hr").classes("dropdown-divider"));
    menuContent.content(element("a").classes("dropdown-item", "custom-url-add").content("+ Add custom URL"));
    menuContent.content(div().id("server-override"));

    return dropdown("server-selector").classes("is-right")
            .content(div().classes("dropdown-trigger").content(trigger), menu);
}
```

Move the server selector into the detail-header in `pageLayout()`:

```java
var detailHeader = div().classes("detail-header").content(title(pageTitle), serverSelector(openApi));
```

Remove `serverSelector(openApi)` from the `body` content below the detail-header.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithOneGet#shouldShowServerDropdownInHeader'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "replace server panel with dropdown in header (no-servers case)"
```

#### 2b: Update HTML generation — multiple servers case

- [ ] **Step 6: Write a failing test for server items in dropdown**

In `BrowserTest$GivenAppWithMultipleServers`, add:

```java
@Test void shouldShowServerItemsInDropdown() {
    app.clickServerToggle();
    then(app.serverDropdownItemCount()).isGreaterThanOrEqualTo(2);
}
```

Add to `AppFixture.java`:

```java
int serverDropdownItemCount() {
    return (int) page.locator("#server-selector .dropdown-item[data-url]").count();
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithMultipleServers#shouldShowServerItemsInDropdown'`
Expected: FAIL

- [ ] **Step 8: Implement multiple servers in dropdown**

In the `serverSelector()` method, handle the servers-present case. Each server becomes a dropdown-item with URL and description:

```java
trigger.content(span(resolveFirstServerUrl(servers)).classes("server-url"), span("▾"));
for (var i = 0; i < servers.size(); i++) {
    var server = servers.get(i);
    var url = server.getUrl();
    var description = server.getDescription();
    var isFirst = (i == 0);

    if (server.getVariables() != null && !server.getVariables().isEmpty()) {
        // Template server — render as expandable group within dropdown
        var templateGroup = div().classes("dropdown-item", "template-group")
                .attr("data-server-index", String.valueOf(i));
        // ... template server content (URL pattern, presets, add-preset button)
        menuContent.content(templateGroup);
    } else {
        var item = element("a").classes("dropdown-item" + (isFirst ? " is-active" : ""))
                .attr("data-url", url)
                .content(div().classes("server-item-url").content(url));
        if (description != null && !description.isEmpty()) {
            item.content(div().classes("server-item-description").content(description));
        }
        menuContent.content(item);
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `mvn test -pl core -Dtest='BrowserTest$GivenAppWithMultipleServers#shouldShowServerItemsInDropdown'`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "add multiple servers to dropdown"
```

#### 2c: Update JavaScript — dropdown toggle and server selection

- [ ] **Step 11: Write a failing test for dropdown toggle**

```java
@Test void shouldToggleServerDropdown() {
    then(app.isServerDropdownOpen()).isFalse();
    app.clickServerToggle();
    then(app.isServerDropdownOpen()).isTrue();
}
```

Add to `AppFixture.java`:

```java
boolean isServerDropdownOpen() {
    return page.locator("#server-selector.is-active").count() > 0;
}
```

Update `clickServerToggle()` to use the new dropdown trigger:

```java
void clickServerToggle() {
    page.locator("#server-selector .server-dropdown-trigger").click();
}
```

- [ ] **Step 12: Run test to verify it fails**

Expected: FAIL — JS doesn't toggle `is-active` on the dropdown yet

- [ ] **Step 13: Update app.js for dropdown toggle**

Replace the server panel toggle logic with Bulma dropdown toggle:

```javascript
const serverSelector = document.getElementById('server-selector');
if (serverSelector) {
    const trigger = serverSelector.querySelector('.server-dropdown-trigger');
    if (trigger) {
        trigger.addEventListener('click', function(e) {
            e.stopPropagation();
            serverSelector.classList.toggle('is-active');
        });
        // Close dropdown when clicking outside
        document.addEventListener('click', function() {
            serverSelector.classList.remove('is-active');
        });
    }
    // ... keep existing click delegation for custom URLs, template presets, etc.
}
```

- [ ] **Step 14: Run test to verify it passes**

Expected: PASS

- [ ] **Step 15: Update server selection JS**

Update the server selection logic to work with dropdown items instead of radio buttons. When a dropdown item with `data-url` is clicked, update the base URL and mark it as active:

```javascript
serverSelector.addEventListener('click', function(e) {
    var item = e.target.closest('.dropdown-item[data-url]');
    if (item) {
        var url = item.getAttribute('data-url');
        serverSelector.querySelectorAll('.dropdown-item[data-url]').forEach(
            function(el) { el.classList.remove('is-active'); });
        item.classList.add('is-active');
        // Update trigger text
        var triggerUrl = serverSelector.querySelector('.server-dropdown-trigger .server-url');
        if (triggerUrl) triggerUrl.textContent = url || window.location.origin;
        updateBaseUrl(url || window.location.origin);
        serverSelector.classList.remove('is-active'); // close dropdown
        return;
    }
    // ... rest of click delegation
});
```

- [ ] **Step 16: Commit**

```bash
git add -A && git commit -m "add dropdown toggle and server selection JS"
```

#### 2d: Update CSS

- [ ] **Step 17: Update CSS for server dropdown**

Remove the `#server-selector` rules that reference `flat-panel` behavior. Add:

```css
/* server dropdown in header */
#server-selector {
    margin-bottom: 0;
}
#server-selector .server-dropdown-trigger {
    font-family: var(--mono-font);
    font-size: 0.85rem;
}
.server-item-url {
    font-family: var(--mono-font);
    font-size: 0.85rem;
}
.server-item-description {
    font-size: 0.75rem;
    color: var(--bulma-text-weak);
}
```

Keep the `flat-panel` CSS rules — they're still used by Global Headers.

- [ ] **Step 18: Commit**

```bash
git add -A && git commit -m "update CSS for server dropdown"
```

#### 2e: Update remaining tests and AppFixture

- [ ] **Step 19: Update AppFixture methods**

Update all AppFixture methods that reference `#server-selector` with panel-specific selectors to use dropdown-compatible selectors. Key changes:

- `isServerPanelExpanded()` → `isServerDropdownOpen()` (uses `is-active` instead of `is-collapsed`)
- `clickServerToggle()` → click `.server-dropdown-trigger` instead of `.server-toggle`
- `hasRoundedCornersWhenCollapsed("#server-selector")` test → remove or adapt (dropdowns don't have this concern)
- Template server methods: update selectors from `#server-selector .template-group` to work within dropdown content
- Custom URL methods: update selectors similarly

- [ ] **Step 20: Update BrowserTest assertions**

Update tests in:
- `GivenAppWithOneGet`: remove `shouldHaveRoundedCornersWhenServerPanelCollapsed` (not applicable to dropdowns)
- `GivenAppWithMultipleServers`: update to use dropdown interactions instead of panel expand
- `CustomServerUrls`: update `clickServerToggle` calls and assertions
- `GivenAppWithTemplateServers`: update template-related interactions within dropdown
- `PerOperationServers`: update server override tests

- [ ] **Step 21: Run all tests**

Run: `mvn test -pl core`
Expected: All pass

- [ ] **Step 22: Review screenshots**

Check `core/target/screenshots/` — especially dark mode screenshots — for visual correctness.

- [ ] **Step 23: Commit**

```bash
git add -A && git commit -m "update tests for server dropdown"
```

#### 2f: Handle no-servers case — resolved origin URL

- [ ] **Step 24: Write a failing test for resolved origin in dropdown**

```java
@Test void shouldShowResolvedOriginInServerDropdown() {
    app.clickServerToggle();
    then(app.serverDropdownItemUrl(0)).isEqualTo(app.getOrigin());
}
```

Add to `AppFixture`:

```java
String serverDropdownItemUrl(int index) {
    return page.locator("#server-selector .dropdown-item[data-url]").nth(index)
            .getAttribute("data-url");
}

String getOrigin() {
    return (String) page.evaluate("() => window.location.origin");
}
```

- [ ] **Step 25: Implement origin resolution in JS**

On page load, if the server dropdown trigger shows an empty URL, resolve it to `window.location.origin`:

```javascript
// Resolve origin for no-servers case
var triggerUrl = serverSelector.querySelector('.server-dropdown-trigger .server-url');
if (triggerUrl && !triggerUrl.textContent) {
    triggerUrl.textContent = window.location.origin;
}
var originItem = serverSelector.querySelector('.dropdown-item[data-url=""]');
if (originItem) {
    var urlDiv = originItem.querySelector('.server-item-url');
    if (urlDiv && !urlDiv.textContent) {
        urlDiv.textContent = window.location.origin;
        originItem.setAttribute('data-url', window.location.origin);
    }
}
```

- [ ] **Step 26: Run test and verify**

Expected: PASS

- [ ] **Step 27: Commit**

```bash
git add -A && git commit -m "resolve origin URL in server dropdown for no-servers case"
```

---

### Task 3: Clean Up Removed CSS and Dead Code

- [ ] **Step 1: Remove server-panel-specific CSS**

Remove from `app.css`:
- `#server-selector, #global-headers { margin-bottom: 1rem; }` — update to only target `#global-headers`
- `.server-toggle` styles (the button that was inside the panel heading)
- Any `#server-selector`-specific `flat-panel` overrides

Keep: all `flat-panel` rules (still used by Global Headers), all template/custom-URL CSS that still applies.

- [ ] **Step 2: Remove dead Java code**

If any methods in `OpenApiUiGenerator` are no longer called after the refactoring (e.g. panel-heading construction), remove them.

- [ ] **Step 3: Run all tests**

Run: `mvn test -pl core`
Expected: All pass

- [ ] **Step 4: Review screenshots**

Final visual review of all screenshots in `core/target/screenshots/`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "clean up removed server panel CSS and dead code"
```

---

### Task 4: Update Demo App

- [ ] **Step 1: Verify demo app works with changes**

Run the demo app and verify the server dropdown and mode selector work correctly with the real OpenAPI spec.

- [ ] **Step 2: Commit if any demo changes needed**

```bash
git add -A && git commit -m "update demo app for layout rearrangement"
```
