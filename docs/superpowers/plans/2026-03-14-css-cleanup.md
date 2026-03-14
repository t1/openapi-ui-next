# CSS Cleanup Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace hardcoded CSS colors with Bulma components and CSS variables so dark mode works automatically.

**Architecture:** Replace method badges with Bulma `Tag`, detail pane with Bulma `Box`, and all hardcoded HSL colors with Bulma CSS variables. Keep custom CSS only for structural rules (tree layout, spacing).

**Tech Stack:** bulma-java 1.0a17 (`Tag`, `Box`), Bulma 1.0.4 CSS variables

**Spec:** `docs/superpowers/specs/2026-03-14-css-cleanup-design.md`

---

### Task 1: Replace method badges with Bulma Tag

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`
- Modify: `core/src/test/java/com/github/t1/openapi/ui/AppFixture.java`

- [x] **Step 1: Update imports in OpenApiUiGenerator**

Add to imports:

```java
import static com.github.t1.bulmajava.basic.Color.*;
import static com.github.t1.bulmajava.basic.Size.MEDIUM;
import static com.github.t1.bulmajava.elements.Tag.tag;
```

Remove unused Color import if it was only `PRIMARY` (keep `PRIMARY` — it's used for buttons).

- [x] **Step 2: Add method-to-color mapping**

Add a helper method:

```java
private static Color methodColor(PathItem.HttpMethod method) {
    return switch (method) {
        case GET -> SUCCESS;
        case POST -> LINK;
        case PUT -> WARNING;
        case DELETE -> DANGER;
        case PATCH -> PRIMARY;
        default -> INFO;
    };
}
```

- [x] **Step 3: Replace badge creation in renderNode (tree badges)**

In `renderNode()` (~line 197), replace:

```java
var badge = span(method.name()).classes("method-badge", "method-" + method.name().toLowerCase());
```

with:

```java
var badge = tag(method.name()).is(methodColor(method));
```

- [x] **Step 4: Replace badge creation in generateFragments (detail badges)**

In `generateFragments()` (~line 112), replace:

```java
var headingBadge = span(method.name()).classes("method-badge", "detail-badge",
        "method-" + method.name().toLowerCase());
```

with:

```java
var headingBadge = tag(method.name()).is(methodColor(method), MEDIUM);
```

- [x] **Step 5: Update AppFixture.hasMethodBadge selector**

In `AppFixture.java` line 184-186, replace:

```java
boolean hasMethodBadge(String method) {
    return page.locator("[role='tree'] .method-badge.method-" + method.toLowerCase()).isVisible();
}
```

with:

```java
boolean hasMethodBadge(String method) {
    return page.locator("[role='tree'] .tag:text('" + method + "')").isVisible();
}
```

- [x] **Step 6: Delete method badge CSS**

In `CUSTOM_CSS`, delete these lines (287-308):

```
/* --- Method badges --- */
.method-badge { ... }
.method-badge.detail-badge { ... }
.method-get { ... }
.method-post { ... }
.method-put { ... }
.method-delete { ... }
.method-patch { ... }
```

- [x] **Step 7: Run tests**

Run: `mvn test -pl core`

Expected: All tests pass. Review screenshots in `core/target/screenshots/`.

- [x] **Step 8: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java core/src/test/java/com/github/t1/openapi/ui/AppFixture.java
git commit -m "replace method badges with Bulma Tag component"
```

---

### Task 2: Replace detail pane with Bulma Box

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java`

- [x] **Step 1: Add Box import**

```java
import static com.github.t1.bulmajava.elements.Box.box;
```

- [x] **Step 2: Replace div with box in generate()**

In `generate()` (~line 62), replace:

```java
var detail = div().id("detail").attr("tabindex", "0");
```

with:

```java
var detail = box().id("detail").attr("tabindex", "0");
```

- [x] **Step 3: Delete #detail color/border/shadow CSS**

In `CUSTOM_CSS`, replace the `#detail` block (lines 327-332):

```
#detail {
    background-color: white;
    border-radius: 8px;
    padding: 1.5rem;
    box-shadow: 0 1px 3px hsla(220, 20%, 20%, 0.06);
    border: 1px solid hsl(220, 15%, 92%);
}
```

with nothing — delete it entirely. The Bulma `.box` class provides background, border-radius, padding, shadow, and border with dark mode support.

- [x] **Step 4: Run tests**

Run: `mvn test -pl core`

Expected: All tests pass. The `#detail` CSS selectors in AppFixture still work because the id is preserved.

- [x] **Step 5: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "replace detail pane div with Bulma Box component"
```

---

### Task 3: Replace hardcoded colors with Bulma CSS variables

**Files:**
- Modify: `core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java` (CUSTOM_CSS constant)

- [ ] **Step 1: Delete body background**

Delete:

```css
body {
    background-color: hsl(220, 15%, 97%);
}
```

- [ ] **Step 2: Replace sidebar colors**

Replace:

```css
.columns.is-desktop > .column.is-one-third {
    background-color: hsl(220, 18%, 95%);
    border-right: 1px solid hsl(220, 15%, 88%);
    padding: 1.25rem 1.5rem;
}
```

with:

```css
.columns.is-desktop > .column.is-one-third {
    background-color: var(--bulma-scheme-main-bis);
    border-right: 1px solid var(--bulma-border);
    padding: 1.25rem 1.5rem;
}
```

- [ ] **Step 3: Replace tree component colors**

Replace tree group border:

```css
border-left: 2px solid hsl(220, 15%, 87%);
```

with:

```css
border-left: 2px solid var(--bulma-border);
```

Replace hover background:

```css
[role="treeitem"] > span:hover {
    background-color: hsl(220, 20%, 91%);
}
```

with:

```css
[role="treeitem"] > span:hover {
    background-color: var(--bulma-scheme-main-ter);
}
```

Replace selected background:

```css
[role="treeitem"][aria-selected="true"] > span:first-child {
    background-color: hsl(217, 71%, 93%);
}
```

with:

```css
[role="treeitem"][aria-selected="true"] > span:first-child {
    background-color: var(--bulma-link-light);
}
```

Replace focus outline:

```css
outline: 2px solid hsl(217, 71%, 53%);
```

with:

```css
outline: 2px solid var(--bulma-link);
```

Replace tree segment text color:

```css
color: hsl(220, 15%, 25%);
```

with:

```css
color: var(--bulma-text-strong);
```

Replace tree toggle icon color:

```css
color: hsl(220, 10%, 55%);
```

with:

```css
color: var(--bulma-text-weak);
```

Replace tree operation label color:

```css
color: hsl(220, 10%, 45%);
```

with:

```css
color: var(--bulma-text-weak);
```

- [ ] **Step 4: Replace detail header colors**

Replace header border:

```css
border-bottom: 2px solid hsl(220, 15%, 90%);
```

with:

```css
border-bottom: 2px solid var(--bulma-border);
```

Delete the `.detail-header .title` color override entirely:

```css
.detail-header .title {
    margin-bottom: 0;
    color: hsl(220, 20%, 20%);
}
```

becomes:

```css
.detail-header .title {
    margin-bottom: 0;
}
```

- [ ] **Step 5: Replace detail content colors**

Replace endpoint path color:

```css
color: hsl(220, 15%, 30%);
```

with:

```css
color: var(--bulma-text-strong);
```

Replace op summary color:

```css
color: hsl(220, 10%, 45%);
```

with:

```css
color: var(--bulma-text-weak);
```

Replace pre block styling:

```css
#detail pre {
    border: 1px solid hsl(220, 15%, 90%);
    border-radius: 6px;
    padding: 1rem 1.25rem;
    background-color: hsl(220, 18%, 97%);
    font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
    font-size: 0.875rem;
    margin-top: 1rem;
    margin-bottom: 1rem;
}
```

with:

```css
#detail pre {
    border: 1px solid var(--bulma-border);
    border-radius: 6px;
    padding: 1rem 1.25rem;
    background-color: var(--bulma-scheme-main-bis);
    font-family: 'SFMono-Regular', 'Menlo', 'Consolas', monospace;
    font-size: 0.875rem;
    margin-top: 1rem;
    margin-bottom: 1rem;
}
```

- [ ] **Step 6: Run tests**

Run: `mvn test -pl core`

Expected: All tests pass. Review screenshots in `core/target/screenshots/` for visual correctness.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/com/github/t1/openapi/ui/OpenApiUiGenerator.java
git commit -m "replace hardcoded CSS colors with Bulma variables"
```

---

### Task 4: Final review

- [ ] **Step 1: Verify no hardcoded HSL values remain**

Search the CUSTOM_CSS for any remaining `hsl(` — there should be none.

- [ ] **Step 2: Run full build**

Run: `mvn verify`

Expected: All modules build and tests pass.

- [ ] **Step 3: Review screenshots**

Review all screenshots in `core/target/screenshots/` for visual quality using the `frontend-design` skill.

- [ ] **Step 4: Update docs if needed**

Check if README or other docs reference the old CSS classes (e.g., `method-badge`). Update if found.
