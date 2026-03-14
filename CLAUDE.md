# CLAUDE.md

Read `README.md` before starting any task — it has project overview, architecture, and conventions.

Do **NOT** read the `TODO.md`, unless instructed to.

Interaction Style **VERY IMPORTANT**:

* Be very critical and honest to what I say. I always can be wrong and it's not impolite to say so.
* When I ask a question, it's just a question, not a suggestion. Don't start working, think about it.
* **NEVER use local/private auto memory.** Store all learnings and conventions in this file
  (or other project files), so they are shared with everyone working on the project.
* If you find that you did something wrong, don't apologize, find a solution; use the `/learn` command.

Don't forget to update the documentation when you change the code. **VERY IMPORTANT**

If you create a new file, also stage exactly this file to git, but not any other files that are not staged.

## Commits

- Keep commit messages short (single line, no body).
- Never add a `Co-Authored-By` trailer.

## Shell Commands

- Always quote Maven `-Dtest` values containing `#` (method selectors), e.g.:
  `mvn test -Dtest='MyTest#myMethod'` — unquoted `#` is parsed as a shell comment.

## Skills

Before writing or editing **any** code — including one-line fixes — invoke the matching
language/framework skill (e.g. `tdder:java` for `.java` files). No exception for "quick" edits.
Skills encode conventions (imports, naming, idioms) that apply to every change, not just big tasks.

## Workflow

We work **trunk-based** — all commits go directly to `trunk`. Skip the
`superpowers:finishing-a-development-branch` skill; it's for feature-branch workflows.

## Plan Execution

When executing a plan: if a technology or dependency from the plan doesn't work as expected,
STOP and discuss with the user. Do not substitute alternative libraries, frameworks, or architectural
approaches. The plan's tech choices are constraints, not suggestions.

When completing a step from a plan file (e.g. in `docs/superpowers/plans/`), tick its checkbox
(`- [ ]` → `- [x]`) immediately.

## UI Review

After changing UI generation code (in `core`), run the tests (`mvn test -pl core`) and review
the screenshots in `core/target/screenshots/` using the `frontend-design` plugin for design
and UX quality. The screenshots are produced automatically by the Playwright browser tests.
