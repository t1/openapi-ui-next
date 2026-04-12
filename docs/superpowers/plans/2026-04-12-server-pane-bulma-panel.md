# Server & Headers Panes — Bulma Panel Restyle

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom-CSS server selector and global headers panels with Bulma `panel()` components, and add proper styling for template server presets.

**Architecture:** Both panels become Bulma panels with panel-heading (collapsible toggle) and panel-blocks (content rows). Template servers get one panel-block per template, containing a sub-heading + all preset rows + add-preset button. CSS is minimal on top of Bulma. JS selectors update but behavior stays identical.

**Tech Stack:** bulma-java (`panel()`, panel-block), existing app.css/app.js, Playwright browser tests.

---

### Task 1: Convert server selector Java generation to Bulma panel

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java:153-277`

- [ ] **Step 1: Add panel import**

Add to the imports section of `OpenApiUiGenerator.java`:

```java
import static com.github.t1.bulmajava.components.Panel.panel;
```

- [ ] **Step 2: Rewrite `serverSelector()` — no-servers case**

Replace lines 153-163 (the `servers == null || servers.isEmpty()` branch):

```java
    private static Element serverSelector(OpenAPI openApi) {
        var servers = openApi.getServers();
        if (servers == null || servers.isEmpty()) {
            var panel = panel().id("server-selector").classes("is-collapsed");
            panel.content(
                    element("button").attr("type", "button").classes("panel-heading", "server-toggle")
                            .content(span("▶ Server"), span("(resolved from origin)").classes("server-url")));
            var block = div().classes("panel-block");
            block.content(div().id("server-override"));
            block.content(element("button").attr("type", "button").classes("custom-url-add")
                    .content("+ Add custom URL"));
            panel.content(block);
            return panel;
        }
```

- [ ] **Step 3: Rewrite `serverSelector()` — servers-present case**

Replace lines 166-263 (the main body building fixed servers, template groups, and footer). The panel-heading must be the first child so it renders at the top. Use `panel()` (no heading) and add children in order:

```java
        var firstServerUrl = resolveFirstServerUrl(servers);
        var panelHeading = element("button").attr("type", "button").classes("panel-heading", "server-toggle")
                .content(span("▶ Server"), span(firstServerUrl).classes("server-url"));

        var panel = panel().id("server-selector").classes("is-collapsed");
        panel.content(panelHeading);

        for (var i = 0; i < servers.size(); i++) {
            var server = servers.get(i);
            var url = server.getUrl();
            var description = server.getDescription();
            var isFirstServer = (i == 0);

            if (server.getVariables() != null && !server.getVariables().isEmpty()) {
                // Template server — one panel-block containing heading + all presets
                var templateBlock = div().classes("panel-block", "template-group");

                var heading = div().classes("template-group-heading");
                heading.content(span(url).classes("template-url"));
                if (description != null && !description.isEmpty()) {
                    heading.content(span(" — "), span(description).classes("server-description"));
                }
                templateBlock.content(heading);

                // Check if all variables have defaults
                var allVariablesHaveDefaults = server.getVariables().values().stream()
                        .allMatch(v -> v.getDefaultValue() != null);

                if (allVariablesHaveDefaults) {
                    var resolvedUrl = resolveTemplateVariables(url, server.getVariables());
                    var presetId = "server-" + i + "-preset-0";
                    var radio = element("input").attr("type", "radio").attr("name", "server").attr("id", presetId)
                            .attr("value", resolvedUrl);
                    if (isFirstServer) {
                        radio.attr("checked", "");
                    }
                    var label = element("label").attr("for", presetId).classes("template-preset-label");
                    label.content(radio, span(resolvedUrl));
                    templateBlock.content(label);

                    // Add "+ Add preset" button with variable metadata
                    var addPresetBtn = element("button").attr("type", "button")
                            .classes("template-preset-add")
                            .attr("data-server-index", String.valueOf(i))
                            .attr("data-url-template", url);
                    var variablesJson = new StringBuilder("[");
                    var first = true;
                    for (var entry : server.getVariables().entrySet()) {
                        if (!first) variablesJson.append(",");
                        first = false;
                        var variable = entry.getValue();
                        variablesJson.append("{")
                                .append("\"name\":\"").append(entry.getKey()).append("\",")
                                .append("\"default\":\"").append(variable.getDefaultValue() != null ? variable.getDefaultValue() : "").append("\"");
                        if (variable.getEnumeration() != null && !variable.getEnumeration().isEmpty()) {
                            variablesJson.append(",\"enum\":[");
                            variablesJson.append(String.join(",", variable.getEnumeration().stream()
                                    .map(v -> "\"" + v + "\"").toList()));
                            variablesJson.append("]");
                        }
                        if (variable.getDescription() != null) {
                            variablesJson.append(",\"description\":\"").append(variable.getDescription()).append("\"");
                        }
                        variablesJson.append("}");
                    }
                    variablesJson.append("]");
                    addPresetBtn.attr("data-variables", variablesJson.toString());
                    addPresetBtn.content("+ Add preset");
                    templateBlock.content(addPresetBtn);
                }

                panel.content(templateBlock);
            } else {
                // Non-template server — panel-block with radio
                var radioId = "server-" + i;
                var radio = element("input").attr("type", "radio").attr("name", "server").attr("id", radioId)
                        .attr("value", url);
                if (isFirstServer) {
                    radio.attr("checked", "");
                }
                var label = element("label").attr("for", radioId).classes("panel-block", "server-row");
                label.content(radio, span(url));
                if (description != null && !description.isEmpty()) {
                    label.content(span(" — "), span(description).classes("server-description"));
                }
                panel.content(label);
            }
        }

        // Footer: override slot + custom URL button
        var overrideBlock = div().classes("panel-block").id("server-override");
        panel.content(overrideBlock);
        var customUrlBlock = div().classes("panel-block", "custom-url-block");
        customUrlBlock.content(element("button").attr("type", "button").classes("custom-url-add")
                .content("+ Add custom URL"));
        panel.content(customUrlBlock);

        return panel;
    }
```

- [ ] **Step 4: Delete `addServerBodyFooter()` method**

Remove the `addServerBodyFooter()` method (lines 273-277) — its logic is now inlined into `serverSelector()`.

- [ ] **Step 5: Add helper `resolveFirstServerUrl()`**

The existing code uses `servers.getFirst().getUrl()` directly. For template servers, it should resolve variables. Add:

```java
    private static String resolveFirstServerUrl(List<org.eclipse.microprofile.openapi.models.servers.Server> servers) {
        var server = servers.getFirst();
        var url = server.getUrl();
        if (server.getVariables() != null && !server.getVariables().isEmpty()) {
            return resolveTemplateVariables(url, server.getVariables());
        }
        return url;
    }
```

- [ ] **Step 6: Build and verify compilation**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git commit -m "refactor: convert server selector to Bulma panel component"
```

---

### Task 2: Convert global headers Java generation to Bulma panel

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java:279-287`

- [ ] **Step 1: Rewrite `globalHeaders()` method**

Replace the current implementation:

```java
    private static Element globalHeaders() {
        var panelHeading = element("button").attr("type", "button").classes("panel-heading", "global-headers-toggle")
                .content(span("Global Headers"), span("0").classes("global-headers-count"));
        var body = div().classes("panel-block", "global-headers-body")
                .content(element("button").attr("type", "button").classes("custom-header-add")
                        .content("+ Add global header"));
        return panel().id("global-headers").classes("is-collapsed")
                .content(panelHeading, body);
    }
```

- [ ] **Step 2: Build and verify**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add core/src/main/java/com/github/t1/openapi/ui/generator/OpenApiUiGenerator.java
git commit -m "refactor: convert global headers to Bulma panel component"
```

---

### Task 3: Update CSS — remove old styles, add panel and template styles

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css`

- [ ] **Step 1: Update focus styles selector**

Replace line 8 (`.global-headers-toggle:focus {`) to also cover panel-heading:

```css
.desc-toggle:focus,
.response-headers-toggle:focus,
.schema-toggle:focus,
.schema-nested-toggle:focus,
#server-selector > .panel-heading:focus,
#global-headers > .panel-heading:focus {
```

- [ ] **Step 2: Remove old server selector CSS**

Delete the entire block from `/* server selector panel */` through `.server-description` (lines 562-600):

```css
/* server selector panel */
#server-selector { ... }
.server-toggle { ... }
.server-url { ... }
.server-body { ... }
#server-selector.is-collapsed .server-body { ... }
.server-radio-label { ... }
.server-description { ... }
```

- [ ] **Step 3: Remove old global headers CSS**

Delete the entire block from `/* global headers panel */` through `.global-headers.is-collapsed .global-headers-body` (lines 601-632):

```css
.global-headers { ... }
.global-headers-toggle { ... }
.global-headers-count { ... }
.global-headers-body { ... }
.global-headers.is-collapsed .global-headers-body { ... }
```

- [ ] **Step 4: Add new panel and template CSS**

Add in place of the removed blocks:

```css
/* server selector & global headers panels (Bulma panel overrides) */
#server-selector,
#global-headers {
    margin-bottom: 1rem;
}
#server-selector > .panel-heading,
#global-headers > .panel-heading {
    cursor: pointer;
    font-size: 0.8125rem;
    display: flex;
    align-items: center;
    gap: 0.5rem;
}
.server-url {
    font-family: var(--mono-font);
    font-size: 0.75rem;
    font-weight: 400;
    color: var(--bulma-text-weak);
    margin-left: auto;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}
#server-selector.is-collapsed > .panel-block,
#global-headers.is-collapsed > .panel-block {
    display: none;
}
.server-row {
    cursor: pointer;
}
.server-description {
    color: var(--bulma-text-light);
    font-size: 0.875rem;
}
.global-headers-count {
    background: var(--bulma-scheme-main-ter);
    padding: 0 0.5rem;
    border-radius: 10px;
    font-size: 0.6875rem;
}
/* template server groups */
.template-group {
    flex-direction: column;
    align-items: stretch;
    padding: 0;
}
.template-group-heading {
    font-size: 0.75rem;
    color: var(--bulma-text-weak);
    padding: 0.4em 0.75em;
    background: var(--bulma-scheme-main-bis);
    border-bottom: 1px solid var(--bulma-border-weak);
    display: flex;
    align-items: baseline;
    gap: 0.4rem;
}
.template-url {
    font-family: var(--mono-font);
    font-weight: 600;
    color: var(--bulma-link);
}
.template-preset-label {
    display: flex;
    align-items: center;
    padding: 0.375em 0.75em;
    gap: 0.5rem;
    cursor: pointer;
}
.template-preset-label:hover {
    background: var(--bulma-scheme-main-bis);
}
.template-preset-delete {
    background: none;
    border: none;
    color: var(--bulma-danger);
    cursor: pointer;
    font-size: 1.1rem;
    line-height: 1;
    padding: 0 0.25rem;
    margin-left: auto;
    opacity: 0;
    transition: opacity 0.15s;
}
.template-preset-label:hover .template-preset-delete {
    opacity: 0.6;
}
.template-preset-delete:hover {
    opacity: 1;
}
.template-preset-add {
    background: none;
    border: 1px dashed var(--bulma-border);
    border-radius: 4px;
    color: var(--bulma-text-weak);
    cursor: pointer;
    font-size: 0.75rem;
    padding: 0.2em 0.6em;
    margin: 0.3em 0.75em;
    display: inline-block;
}
.template-preset-add:hover {
    border-color: var(--bulma-link);
    color: var(--bulma-link);
}
.template-preset-form {
    padding: 0.6em 0.75em;
    background: var(--bulma-scheme-main-bis);
    border-top: 1px solid var(--bulma-border-weak);
}
/* custom URL block separator */
.custom-url-block {
    border-top: 1px solid var(--bulma-border);
}
```

- [ ] **Step 5: Build and verify**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.css
git commit -m "refactor: replace custom panel CSS with Bulma panel styles and template preset styling"
```

---

### Task 4: Update JavaScript selectors

**Files:**
- Modify: `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js`

- [ ] **Step 1: Update server toggle selector**

At line 634, change:

```javascript
        const serverToggle = serverSelector.querySelector('.server-toggle');
```

to:

```javascript
        const serverToggle = serverSelector.querySelector('.panel-heading.server-toggle');
```

This is a minor refinement — the existing `.server-toggle` class is kept on the panel-heading, so it still works. But the more precise selector is cleaner.

- [ ] **Step 2: Update `restoreCustomUrls()` — parent selector**

At line 587, change:

```javascript
            const serverBody = addBtn.closest('.server-body');
```

to:

```javascript
            const serverBody = addBtn.closest('.panel-block');
```

The custom-url-add button now lives inside a `.panel-block.custom-url-block` instead of `.server-body`.

Wait — actually custom URLs are inserted before the add button, and they need to be inside the same parent. But the custom URL rows sit alongside the add button within the custom-url-block panel-block. Let me reconsider the structure.

Actually, custom URL rows are inserted as siblings of the add button inside the `.custom-url-block` panel-block. That's fine — `insertBefore(row, addBtn)` works within the same parent element. The parent changes from `.server-body` to the `.custom-url-block` panel-block. So the `closest()` call should find the parent containing the add button.

But wait — in the current design, custom URLs and the "+ Add custom URL" button all sit inside `.server-body`. After the change, they're inside `.panel-block.custom-url-block`. The `addBtn.closest('.server-body')` call won't find anything. We need:

```javascript
            const serverBody = addBtn.parentElement;
```

Or more precisely, `addBtn.closest('.custom-url-block')`.

- [ ] **Step 3: Update `restoreTemplatePresets()` — parent selector**

At line 612, change:

```javascript
                const serverBody = addBtn.closest('.server-body');
```

to:

```javascript
                const serverBody = addBtn.closest('.template-group');
```

Template presets are now inserted before the add button inside the `.template-group` panel-block.

- [ ] **Step 4: Update click handler — custom URL add**

At line 645, change:

```javascript
                const serverBody = addBtn.closest('.server-body');
```

to:

```javascript
                const serverBody = addBtn.closest('.custom-url-block');
```

- [ ] **Step 5: Update click handler — template preset add**

At line 664, change:

```javascript
                const serverBody = addPresetBtn.closest('.server-body');
```

to:

```javascript
                const serverBody = addPresetBtn.closest('.template-group');
```

- [ ] **Step 6: Update click handler — template preset delete**

At line 719, change:

```javascript
                const serverBody = label.closest('.server-body');
```

to:

```javascript
                const serverBody = label.closest('.template-group');
```

- [ ] **Step 7: Update global headers toggle selector**

At line 790, change:

```javascript
        globalHeadersPanel.querySelector('.global-headers-toggle').addEventListener('click', function() {
```

to:

```javascript
        globalHeadersPanel.querySelector('.panel-heading.global-headers-toggle').addEventListener('click', function() {
```

- [ ] **Step 8: Update global headers body selector**

At line 796, change:

```javascript
                const body = globalHeadersPanel.querySelector('.global-headers-body');
```

to:

```javascript
                const body = globalHeadersPanel.querySelector('.panel-block.global-headers-body');
```

- [ ] **Step 9: Commit**

```
git add core/src/main/resources/com/github/t1/openapi/ui/generator/app.js
git commit -m "refactor: update JS selectors for Bulma panel structure"
```

---

### Task 5: Update test fixture selectors

**Files:**
- Modify: `core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java`

- [ ] **Step 1: Update `clickServerToggle()`**

At line 717, change:

```java
        page.locator("#server-selector .server-toggle").click();
```

to:

```java
        page.locator("#server-selector > .panel-heading").click();
```

- [ ] **Step 2: Update `serverDescription()`**

At line 707, the `.server-description` class is kept, so this selector still works. No change needed.

- [ ] **Step 3: Update `templateServerUrlPattern()`**

At line 751, change:

```java
        var heading = page.locator("#server-selector .template-server-heading").nth(index);
```

to:

```java
        var heading = page.locator("#server-selector .template-group-heading").nth(index);
```

- [ ] **Step 4: Update `isGlobalHeadersCollapsed()`**

At line 1148, the `#global-headers.is-collapsed` selector still works. No change needed.

- [ ] **Step 5: Update `clickGlobalHeadersToggle()`**

At line 1152, change:

```java
        page.locator(".global-headers-toggle").click();
```

to:

```java
        page.locator("#global-headers > .panel-heading").click();
```

- [ ] **Step 6: Update `globalHeadersBody()` selector**

At line 796 (of AppFixture), the `.global-headers-body` class is kept on the panel-block, so selectors like `#global-headers .global-headers-body` still work. No change needed.

- [ ] **Step 7: Build and verify**

Run: `mvn compile -pl core -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```
git add core/src/test/java/com/github/t1/openapi/ui/generator/AppFixture.java
git commit -m "refactor: update test fixture selectors for Bulma panel structure"
```

---

### Task 6: Run full test suite and fix any failures

**Files:**
- Potentially modify: `AppFixture.java`, `BrowserTest.java`, `OpenApiUiGeneratorTest.java`, `app.css`, `app.js`, `OpenApiUiGenerator.java`

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl core`
Expected: All tests pass. If failures occur, they are likely selector mismatches — fix them iteratively.

- [ ] **Step 2: Fix any unit test assertion failures**

The `OpenApiUiGeneratorTest` tests assert on raw HTML strings. The `#server-selector` wrapper element changes from `<div>` to Bulma's panel output. Update assertions to match the new HTML structure.

For example, `shouldIncludeServerOverrideOobForPerOperationServers` asserts `contains("<div hx-swap-oob=\"innerHTML:#server-override\">")` — this should still pass since the OOB fragment generator didn't change.

- [ ] **Step 3: Fix any browser test failures**

If any browser tests fail due to selector changes not caught in Task 5, update the selectors in `AppFixture.java` or `BrowserTest.java`.

- [ ] **Step 4: Review screenshots**

Check `core/target/screenshots/` for any visual regressions — especially dark mode screenshots.

- [ ] **Step 5: Commit**

```
git add -A
git commit -m "fix: resolve test failures from Bulma panel restyle"
```

---

### Task 7: Squash commits and update demo app

**Files:**
- Potentially modify: demo app spec files if needed

- [ ] **Step 1: Verify demo app exercises template servers**

Check if the demo app's OpenAPI spec includes template servers. If not, no demo change needed — the server pane restyle is visible on any spec with servers.

- [ ] **Step 2: Squash commits**

Squash all commits from Tasks 1-6 into a single commit:

```
git rebase -i HEAD~N  # where N is the number of commits
```

Use commit message:
```
restyle server and headers panels as Bulma panels

Replace custom collapsible CSS with Bulma panel() components.
Add template preset styling: grouped blocks, link-colored headings,
hover-reveal delete buttons, styled add-preset and form.
```

- [ ] **Step 3: Run final verification**

Run: `mvn test -pl core`
Expected: All tests pass.
