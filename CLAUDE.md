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
- Squash related commits into one before finishing a task (e.g. a plan's worth of work
  becomes a single commit).
- Before committing a core change (feature or fix), verify the demo app exercises it.

## Maven Central

Use `central.sonatype.com` for Maven artifact searches, **not** `search.maven.org` (obsolete).

## Shell Commands

- Always quote Maven `-Dtest` values containing `#` (method selectors), e.g.:
  `mvn test -Dtest='MyTest#myMethod'` — unquoted `#` is parsed as a shell comment.
- Run `mvn test` with `dangerouslyDisableSandbox: true` when the run includes Playwright
  browser tests (BrowserTest) — Chromium hangs inside the Claude Code sandbox. This applies
  to the full suite (`mvn test -pl core`) and any `-Dtest` selection that includes BrowserTest.
- Use a 1-minute timeout (`timeout: 60000`) for `mvn test` — the full suite takes ~30 s.
  If it times out, something is wrong — investigate rather than retry.

## Skills

Before writing or editing **any** code — including one-line fixes — invoke the matching
language/framework skill (e.g. `tdder:java` for `.java` files). No exception for "quick" edits.
Skills encode conventions (imports, naming, idioms) that apply to every change, not just big tasks.

**Never look into library source code** (e.g. bulma-java JARs) when a skill covers that library.
The skill is the authoritative reference. If the skill is missing something, report it, so the
skill can be updated — don't work around it by reading source.

**Prefer Bulma components over custom CSS.** Before writing custom styles, check whether a Bulma
component already provides the desired visual treatment (e.g. `messageBody()` for accent blocks,
`notification()` for alerts, `box()` for cards). Only use custom CSS when no Bulma component fits.

## TDD

All behavioral changes — no matter how small — **must** be test-first. Write a failing test,
see it fail, then implement. No exceptions for "obvious" or "trivial" changes. If you catch
yourself thinking "this is too simple for a test," that's exactly when you need one.

Invoke `tdder:tdd` before any feature or bugfix work.

## Workflow

We work **trunk-based** — all commits go directly to `trunk`. Skip the
`superpowers:finishing-a-development-branch` skill; it's for feature-branch workflows.

### Plan Execution

When executing a plan: if a technology or dependency from the plan doesn't work as expected,
STOP and discuss with the user. Do not substitute alternative libraries, frameworks, or architectural
approaches. The plan's tech choices are constraints, not suggestions.

When completing a step from a plan file (e.g. in `docs/superpowers/plans/`), tick its checkbox
(`- [ ]` → `- [x]`) immediately.

### Demo App

The demo app should grow alongside the core: if we change the core (feature or fix), extend the
demo app so that it exercises the change E2E. A fix that only manifests with specific data (e.g.
deep nesting, many methods) needs demo data that triggers it.

### Brainstorm Visual Companion

Always use the visual companion for brainstorming — no need to ask for consent.

Start the brainstorm server for visual mockups during design discussions:

```bash
/Users/rdohna/.claude/plugins/cache/claude-plugins-official/superpowers/5.0.0/lib/brainstorm-server/start-server.sh --project-dir /Users/rdohna/workspace/t1/openapi-ui-next
```

### UI Review

After changing UI generation code (in `core`), run the tests (`mvn test -pl core`) and review
the screenshots in `core/target/screenshots/` using the `frontend-design` plugin for design
and UX quality. The screenshots are produced automatically by the Playwright browser tests.

**VERY IMPORTANT**: Always visually confirm that CSS/layout changes actually achieved their goal
by carefully inspecting the screenshots. Don't assume a change worked just because tests pass —
tests verify behavior, not visual correctness. Look at the specific pixels/spacing/alignment
that was supposed to change and verify it matches the intent.

**VERY IMPORTANT**: Never claim interactive behavior (keyboard navigation, click handlers, focus
management, expand/collapse) works without a browser test that exercises the exact interaction.
Screenshots are static and cannot validate dynamic behavior. Write the test *before* claiming
success — if you can't test it, you can't know it works.
