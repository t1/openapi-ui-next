# Spec: Body textarea auto-grow (#8)

## Problem

The request body textarea has a fixed height (`rows="6"`) and CSS `resize: vertical`.
It does not grow when:
- Content is typed or pasted (more lines than visible)
- The textarea width is reduced (e.g. by dragging the split pane handle), causing text to wrap

## Solution

Add JavaScript auto-grow behavior to the textarea so its height adjusts automatically to fit
its content. The textarea should:

1. **Grow on input**: resize height whenever content changes (typing, pasting, programmatic fill).
2. **Grow on width change**: resize height when the split pane handle is dragged (content reflows).
3. **Grow on initial load**: resize height when content is restored from cache or set by example select.
4. **Never shrink below a minimum**: keep a sensible minimum height (approximately the current 6 rows).

## Design Decisions

- **CSS-first approach using `field-sizing: content`**: Modern browsers support the CSS property
  `field-sizing: content` which makes textareas auto-size natively. However, browser support is
  still limited (Chrome 123+, no Firefox/Safari as of early 2026). We'll use a JS approach for
  broad compatibility.
- **JS approach**: Set `overflow-y: hidden` and adjust `style.height` based on `scrollHeight`
  on each `input` event. Also observe width changes via `ResizeObserver`.
- **Remove `rows="6"`**: Replace with a CSS `min-height` to maintain the minimum size.
- **Remove `resize: vertical`**: Auto-grow makes manual resize unnecessary and potentially
  conflicting.

## Files Likely Involved

- `core/src/main/resources/com/github/t1/openapi/ui/generator/app.js` — add auto-grow logic
- `core/src/main/resources/com/github/t1/openapi/ui/generator/app.css` — adjust textarea styles
- `core/src/main/java/com/github/t1/openapi/ui/generator/OperationFragmentGenerator.java` — remove `rows="6"`
- `core/src/test/java/com/github/t1/openapi/ui/generator/BrowserTest.java` — add auto-grow tests
- `core/src/test/java/com/github/t1/openapi/ui/generator/OpenApiUiGeneratorTest.java` — update if HTML changes

## Acceptance Criteria

1. Textarea auto-grows when content is typed or pasted.
2. Textarea auto-grows when width is reduced (split pane drag).
3. Textarea auto-grows when content is restored from cache on page load.
4. Textarea auto-grows when example is selected from the example dropdown.
5. Textarea never shrinks below ~6 rows minimum height.
6. Manual `resize: vertical` handle is removed.
7. Existing keyboard navigation in/out of textarea still works.
8. Existing send/curl/httpie functionality unaffected.
