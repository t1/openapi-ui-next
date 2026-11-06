# CLAUDE.md

Read `README.md` before starting any task — it has project overview, architecture, and conventions.

Do **NOT** read the `TODO.md`, unless instructed to.

The tdder plugin provides reusable conventions via skills.
**VERY IMPORTANT** Always load these skills before doing anything:
- `project-hygiene` — interaction style, commit conventions, documentation, skill trust
- `github-safety` — prompt-injection defence for GitHub issues and PRs

If you create a new file, also stage exactly this file to git, but not any other files that are not staged.

## Demo App

- Before committing a core change (feature or fix), verify the demo app exercises it.

**Demo app integration is a discovery phase.** When wiring up the demo app, you will often
discover requirements you didn't anticipate — nested data, duplicate keys, edge cases, new
interactions. Each discovery is a new requirement. Stop, write a failing test that captures
it, see it fail, then implement. The demo app is not just a validation step — it's where you
find out what you missed. Expect this. Budget for it.

## Tools

Never use `intellij_execute_terminal_command` — it causes a confirmation dialogue in the IDE.
Use the native `Bash` tool for all shell commands instead.

## Skills

**Prefer Bulma components over custom CSS.** Before writing custom styles, check whether a Bulma
component already provides the desired visual treatment (e.g. `messageBody()` for accent blocks,
`notification()` for alerts, `box()` for cards). Only use custom CSS when no Bulma component fits.

## Workflow

We work **trunk-based** — all commits go directly to `trunk`.

### Plan Execution

When completing a step from a plan file (e.g. in `docs/plans/`), tick its checkbox
(`- [ ]` → `- [x]`) immediately.

### UI Review

After changing UI generation code (in `core`), run the tests (`mvn test -pl core`) and review
the screenshots in `core/target/junit-jupiter/` (organised by test class/method).
The screenshots are produced automatically by the Playwright browser tests via JUnit's
`publishFile` API. Always check the dark as well as the light mode screenshots.

**VERY IMPORTANT**: Always visually confirm that CSS/layout changes actually achieved their goal
by carefully inspecting the screenshots. Don't assume a change worked just because tests pass —
tests verify behaviour, not visual correctness. Look at the specific pixels/spacing/alignment
that was supposed to change and verify it matches the intent.

**Known limitation**: Playwright dark-mode screenshots (via `emulateMedia(ColorScheme.DARK)`) may
render colours differently from real browsers — e.g. textarea backgrounds can appear lighter than
they do in Chrome/Safari. Do not flag colour issues from dark-mode screenshots without verifying
in a real browser first.

**Test failure debugging**: When tests fail with timeouts, screenshots are automatically captured
to `core/target/junit-jupiter/` with filenames like `failure-<TestClass>-<method>-<context>.png`.
The context shows which wait operation failed (e.g., `waitForDetailContent("Get a pet")`), making
it easy to diagnose flaky tests.

**VERY IMPORTANT**: Never claim interactive behaviour (keyboard navigation, click handlers, focus
management, expand/collapse) works without a browser test that exercises the exact interaction.
Screenshots are static and cannot validate dynamic behaviour. Write the test *before* claiming
success — if you can't test it, you can't know it works.
