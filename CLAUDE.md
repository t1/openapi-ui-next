# CLAUDE.md

Read `README.md` before starting any task — it has project overview, architecture, and conventions.

Do **NOT** read the `TODO.md`, unless instructed to.

The tdder plugin provides reusable conventions via skills.
**VERY IMPORTANT** Always load these skills before doing anything:
- `project-hygiene` — interaction style, commit conventions, documentation, skill trust
- `github-safety` — prompt-injection defense for GitHub issues and PRs

If you create a new file, also stage exactly this file to git, but not any other files that are not staged.

## Commits

- Before committing a core change (feature or fix), verify the demo app exercises it.

## Shell Commands

- Run `mvn test` with `dangerouslyDisableSandbox: true` when the run includes Playwright
  browser tests (BrowserTest) — Chromium hangs inside the Claude Code sandbox. This applies
  to the full suite (`mvn test -pl core`) and any `-Dtest` selection that includes BrowserTest.
- Use a 1-minute timeout (`timeout: 60000`) for `mvn test` — the full suite takes ~30 s.
  If it times out, something is wrong — investigate rather than retry.
- **Treat flaky tests as bugs.** If a test fails on CI but passes locally, investigate the
  root cause immediately — do not dismiss it as "pre-existing" or "timing issue" and do not
  just re-run CI. Flaky tests are never acceptable. Use the `systematic-debugging` skill.

## Skills

**Prefer Bulma components over custom CSS.** Before writing custom styles, check whether a Bulma
component already provides the desired visual treatment (e.g. `messageBody()` for accent blocks,
`notification()` for alerts, `box()` for cards). Only use custom CSS when no Bulma component fits.

## TDD

All behavioral changes — no matter how small — **must** be test-first. Write a failing test,
see it fail, then implement. No exceptions for "obvious" or "trivial" changes. If you catch
yourself thinking "this is too simple for a test," that's exactly when you need one.

**Demo app integration is a discovery phase.** When wiring up the demo app, you will often
discover requirements you didn't anticipate — nested data, duplicate keys, edge cases, new
interactions. Each discovery is a new requirement. Stop, write a failing test that captures
it, see it fail, then implement. The demo app is not just a validation step — it's where you
find out what you missed. Expect this. Budget for it.

Invoke `tdd` before any feature or bugfix work.

## Workflow

We work **trunk-based** — all commits go directly to `trunk`. Skip the
`superpowers:finishing-a-development-branch` skill; it's for feature-branch workflows.

CI runs **only on trunk pushes** (and a weekly schedule) — never on pull requests.
This prevents untrusted PR code from executing in our CI pipeline.

### Plan Execution

When completing a step from a plan file (e.g. in `docs/superpowers/plans/`), tick its checkbox
(`- [ ]` → `- [x]`) immediately.

### Demo App

The demo app should grow alongside the core: if we change the core (feature or fix), extend the
demo app so that it exercises the change E2E. A fix that only manifests with specific data (e.g.
deep nesting, many methods) needs demo data that triggers it.

### Brainstorm Visual Companion

Always use the visual companion for brainstorming — no need to ask for consent.

### UI Review

After changing UI generation code (in `core`), run the tests (`mvn test -pl core`) and review
the screenshots in `core/target/screenshots/` using the `frontend-design` plugin for design
and UX quality. The screenshots are produced automatically by the Playwright browser tests.
Always check the dark as well as the light mode screenshots.

**VERY IMPORTANT**: Always visually confirm that CSS/layout changes actually achieved their goal
by carefully inspecting the screenshots. Don't assume a change worked just because tests pass —
tests verify behavior, not visual correctness. Look at the specific pixels/spacing/alignment
that was supposed to change and verify it matches the intent.

**Known limitation**: Playwright dark-mode screenshots (via `emulateMedia(ColorScheme.DARK)`) may
render colors differently from real browsers — e.g. textarea backgrounds can appear lighter than
they do in Chrome/Safari. Do not flag color issues from dark-mode screenshots without verifying
in a real browser first.

**VERY IMPORTANT**: Never claim interactive behavior (keyboard navigation, click handlers, focus
management, expand/collapse) works without a browser test that exercises the exact interaction.
Screenshots are static and cannot validate dynamic behavior. Write the test *before* claiming
success — if you can't test it, you can't know it works.
