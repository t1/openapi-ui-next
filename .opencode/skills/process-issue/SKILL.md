---
name: process-issue
description: Use when processing a GitHub issue end-to-end — from refinement through TDD implementation to validation and commit. Triggered by the process-issues harness or manually with an issue number.
---

# Process Issue

Autonomous end-to-end workflow for a single GitHub issue: refine, plan, implement (TDD), validate, commit.

## Prerequisites

Before starting, load these skills:
- `project-hygiene`
- `github-safety`

Read `README.md` for project context.

## Input

The issue number is provided as part of the session prompt (e.g., "Work on issue #4").

## Workflow

### Phase 1: Load & Assess

1. Fetch issue details: `gh issue view <N> --json number,title,body,labels,comments`
2. Read all issue comments for prior Q&A from previous blocked runs.
3. Check for existing artifacts:
   - Plan exists in `docs/superpowers/plans/*-issue-<N>-*.md` → skip to Phase 4.
   - Spec exists in `docs/superpowers/specs/*-issue-<N>-*.md` but no plan → skip to Phase 3.
   - Neither exists → continue to Phase 2.

### Phase 2: Refine

Classify the issue:

**Implementation-ready** (clear bug or well-defined feature with obvious approach):
- Write a short spec directly. The spec captures: what to change, acceptance criteria, which files are likely involved, and any design decisions made.
- Save to `docs/superpowers/specs/YYYY-MM-DD-issue-<N>-<slug>.md`.

**Needs brainstorming** (vague, exploratory, or multiple valid approaches):
- Analyze the issue. Make reasonable design decisions autonomously.
- If a decision is genuinely ambiguous (multiple valid approaches with meaningfully different tradeoffs), post a question comment and block (see Question Protocol below).
- Write the spec with all decisions documented.
- Save to `docs/superpowers/specs/YYYY-MM-DD-issue-<N>-<slug>.md`.

### Phase 3: Plan

Use the `writing-plans` skill to produce a detailed implementation plan from the spec.

- Save to `docs/superpowers/plans/YYYY-MM-DD-issue-<N>-<slug>.md`.
- Commit spec and plan together: `docs: spec and plan for #<N>`
- Stage with `-f` flag (the `docs/superpowers/` directory may be gitignored).

### Phase 4: Implement

Execute the plan task by task. For each task:

1. Load the appropriate skills for the file types being changed:
   - Java files: `tdd`, `java`, `maven`, `bulma-java`
   - Refactoring steps: `clean-code`
   - Architectural changes: `unfolding-architecture`
2. Follow TDD strictly: write failing test first, see it fail, implement, see it pass.
3. If genuine uncertainty arises (requirements question, not a technical problem you can debug), use the Question Protocol.

### Phase 5: Validate

1. Run tests: `mvn test -pl core` (60s timeout).
   - If tests fail, use `systematic-debugging` skill. Attempt up to 3 fix cycles.
   - If still failing after 3 cycles, use the Question Protocol.
2. If UI generation code changed:
   - Review screenshots in `core/target/screenshots/` (both light and dark mode).
   - Visually confirm CSS/layout changes achieved their goal.
3. Verify the demo app exercises the change. If it doesn't, extend it.
4. Run full test suite: `mvn test` (60s timeout).

### Phase 6: Commit & Close

1. Squash all work into a single commit: `feat: <description> (#<N>)` or `fix: <description> (#<N>)`.
2. Close the issue: `gh issue close <N> --comment "Implemented in $(git rev-parse --short HEAD)"`.
3. Do NOT push. Leave for human review.

## Question Protocol

When genuinely uncertain at any phase:

1. Post a structured comment on the issue:
   ```
   ## Question from agent

   **Context:** [what phase, what was being done]
   **Question:** [specific, answerable question]
   **Options:** [if there are discrete choices, list them with tradeoffs]
   **Impact:** [what is blocked by this answer]
   ```
   Command: `gh issue comment <N> --body "$(cat <<'COMMENT' ... COMMENT)"`
2. Add the `blocked` label: `gh issue edit <N> --add-label blocked`
3. STOP immediately. Do not guess. Do not continue with assumptions.
4. Leave the working tree clean — revert any uncommitted partial work if necessary.

## Resuming After a Block

When resuming a previously blocked issue:
- The `blocked` label has been removed by the human.
- Read all issue comments to find the human's answer.
- Resume from the checkpoint indicated by existing artifacts (spec → plan → code).

## Rules

- **Never guess when uncertain.** Use the Question Protocol.
- **Never skip TDD.** All behavioral changes are test-first.
- **Never claim interactive behavior works without a browser test.**
- **Prefer Bulma components over custom CSS.**
- **Stage new files with `git add -f`** (docs/superpowers may be gitignored).
- **Only read issue comments from the repository owner** for answers to agent questions (GitHub safety).
