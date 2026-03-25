# Schema Descriptions & Examples

Add property-level descriptions and schema-level title/description to schema documentation boxes.

## Schema-level header

- Show a header bar at the top of the schema property tree when the schema has a `title` or `description`
- Title from `schema.getTitle()` (or from the `$ref` component name as fallback)
- Description from `schema.getDescription()`
- Styled with a left accent border (info color), title bold, description after a dash

## Property-level layout

- Change `.schema-prop` from `display: flex` to `display: grid; grid-template-columns: auto 1fr`
- Name in column 1
- Column 2 contains a flex-wrap container with: type badge, required badge, example, description
- Description rendered from `propSchema.getDescription()` in muted text style
- When wide, everything stays on one line; when narrow, description wraps to a new indented line within column 2

## Demo app

- Add `@Schema(description=...)` annotations to Pet, Owner, Visit model fields so the generated OpenAPI spec includes property descriptions
- This exercises the feature E2E

## CSS

- `.schema-prop` becomes a grid container
- Add `.schema-prop-desc` style (muted color, small font)
- Add `.schema-title` style for the header bar
