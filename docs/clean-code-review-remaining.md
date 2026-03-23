# Clean Code Review — Remaining Findings

All findings relate to `OpenApiUiGenerator` being too large (1100+ lines, SRP violation).

## #7 — `generate()` too long (~90 lines)

Mixes parsing, model building, layout assembly, and file writing in one method.
Extract named steps: `parseSpec()`, `buildPageLayout()`, `writeOutput()`.

## #8 — `buildMethodFragmentContent` too long (~125 lines)

Builds 6 distinct sections (header, description, params, request body, response schema, send button) in one method.
Extract per-section helpers.

## #9 — `APP_JS` ~400 lines of inline JavaScript

Mixes mode toggle, view toggle, tab navigation, field navigation, send button handling, and error retry.
Split by concern into separate constants or move to `.js` resource files.

## #10 — `APP_CSS` ~180 lines of inline CSS

Mixes layout, detail pane, animations, tags, and description styles.
Group by feature or move to a resource file.

## #12 — `OpenApiUiGenerator` violates SRP

The class handles: spec parsing, path tree model, HTML page layout, fragment generation,
tag tree generation, JSON skeleton generation, CSS/JS aggregation, and webjar resource copying.
Extract at least: a `FragmentGenerator` and move CSS/JS to resource files or a dedicated class.

## #14 — `buildMethodFragmentContent` has 3 parameters

`(HttpMethod method, Operation operation, String fullPath)` — these always travel together.
Introduce an `OperationContext` record to bundle them.

## #19 — Inconsistent formatting in `OpenApiUiGeneratorTest`

Broken indentation at line 112, missing blank lines between some test methods.
