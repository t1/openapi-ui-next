# Issue Processing Harness — Design Spec

## Goal

A repeatable, semi-autonomous workflow that takes approved GitHub issues, refines them, implements them using TDD and
subagents, validates the result, and commits — then resets context and moves to the next issue.

## Components

### 1. Skill: `process-issue`

A project-local skill (`.claude/skills/process-issue/SKILL.md` or equivalent) that an agent follows within a single
session. It encodes the per-issue workflow.

### 2. Shell script: `process-issues`

A loop script that:

- Finds the next approved, non-blocked issue (lowest number first)
- Spawns a fresh agent session with instructions to load `process-issue` and work on that issue
- Waits for the session to complete
- Checks the outcome and moves to the next issue

---

## Per-Issue Workflow (Skill)

### Phase 1: Load & Assess

1. Load project skills: `project-hygiene`, `github-safety`.
2. Read `README.md` for project context.
3. Fetch issue details via `gh issue view <N>`.
4. Read all issue comments (for prior Q&A from previous blocked runs).
5. Check for existing artifacts in `docs/superpowers/`:
    - If a **plan** exists → skip to Phase 4 (Implement).
    - If a **spec** exists but no plan → skip to Phase 3 (Plan).
    - If neither → continue to Phase 2.

### Phase 2: Refine (if needed)

Classify the issue:

- **Implementation-ready** (clear bug or well-defined feature): Write a short spec directly — no full brainstorming
  cycle. The spec captures: what to change, acceptance criteria, which files are likely involved.
- **Needs brainstorming** (vague, exploratory, multiple approaches): Follow the `brainstorming` skill, but adapted for
  autonomous mode:
    - No visual companion (no human at the terminal).
    - Instead of asking the user questions interactively, the agent makes reasonable decisions and documents them in the
      spec. If a decision is genuinely ambiguous (multiple valid approaches with different tradeoffs), the agent posts a
      question comment on the issue and blocks.

Output: Spec written to `docs/superpowers/specs/YYYY-MM-DD-issue-<N>-<slug>.md`.

### Phase 3: Plan

Follow the `writing-plans` skill to produce a detailed implementation plan from the spec.

Output: Plan written to `docs/superpowers/plans/YYYY-MM-DD-issue-<N>-<slug>.md`.

Commit spec and plan together: `docs: spec and plan for #<N>`.

### Phase 4: Implement

Execute the plan using `subagent-driven-development`:

- One subagent per plan task.
- Each subagent loads: `tdd`, `java`, `maven`, `bulma-java` (as applicable per file type).
- The `clean-code` skill is loaded during refactoring steps.
- The `unfolding-architecture` skill is loaded if the plan involves structural/architectural changes.
- Between tasks, the orchestrating agent reviews for spec compliance and code quality.

If a subagent encounters genuine uncertainty (not a technical problem it can debug, but a requirements/design question):

- It returns the question to the orchestrating agent.
- The orchestrating agent posts a structured comment on the issue and blocks.

### Phase 5: Validate

1. Run `mvn test -pl core` (with `dangerouslyDisableSandbox: true`, 60s timeout).
2. If tests fail:
    - Load `systematic-debugging` skill.
    - Attempt to fix (up to 3 cycles).
    - If still failing, post a question comment and block.
3. Review screenshots in `core/target/screenshots/` (both light and dark mode) using the `frontend-design` plugin.
4. If the change affects UI generation, visually confirm the CSS/layout change achieved its goal.
5. Verify the demo app exercises the change. If it doesn't, extend the demo app.
6. Run full test suite: `mvn test` (all modules, with sandbox disabled, 60s timeout).

### Phase 6: Commit & Close

1. Squash all work into a single commit with a descriptive message referencing the issue: `feat: <description> (#<N>)`
   or `fix: <description> (#<N>)`.
2. Close the issue via `gh issue close <N>`.
3. Do NOT push — leave that for the human to review and push.

---

## Question Protocol

When the agent encounters genuine uncertainty at any phase:

1. Post a structured comment on the issue:
   ```
   ## Question from agent

   **Context:** [what phase, what was being done]
   **Question:** [specific, answerable question]
   **Options:** [if there are discrete choices, list them with tradeoffs]
   **Impact:** [what is blocked by this answer]
   ```
2. Add the `blocked` label to the issue.
3. STOP immediately. Do not guess. Do not continue with assumptions.
4. Leave the working tree clean (no half-done uncommitted work).

### Resuming after an answer

When the shell script picks up an issue that was previously blocked:

- The `blocked` label has been removed (by the human).
- The agent reads the issue comments to find the answer.
- The agent picks up from the checkpoint (spec/plan files indicate where to resume).

---

## Shell Script Design

```
process-issues [--dry-run] [--issue N]
```

### Behavior

1. Query: `gh issue list --label approved --label '!blocked' --state open --sort created --json number,title --limit 1`
    - If `--issue N` is provided, use that specific issue instead.
2. If no issues found, exit.
3. Log: "Processing issue #N: <title>"
4. Spawn a fresh agent session (OpenCode or Claude Code) with the prompt:
   ```
   Load the process-issue skill. Work on issue #<N> for the openapi-ui-next project.
   ```
5. Wait for session to complete.
6. Check outcome:
    - If `blocked` label was added → log "Issue #N blocked, skipping"
    - If issue was closed → log "Issue #N completed"
    - If session ended without either → log "Issue #N: session ended without resolution"
7. Loop back to step 1 (pick next issue).

### `--dry-run` mode

Lists which issues would be processed, in what order, without spawning agent sessions.

---

## Artifact Lifecycle

| Phase     | Artifacts created                                                         |
|-----------|---------------------------------------------------------------------------|
| Spec      | `docs/superpowers/specs/YYYY-MM-DD-issue-<N>-<slug>.md`                   |
| Plan      | `docs/superpowers/plans/YYYY-MM-DD-issue-<N>-<slug>.md`                   |
| Implement | Source code changes, test changes, demo app changes                       |
| Commit    | Single squashed commit: `feat/fix: <desc> (#<N>)`                         |
| Cleanup   | Separate commit removing spec+plan: `docs: remove spec and plan for #<N>` |

Spec and plan files persist after the implementation commit so you can review them. They are cleaned up in a separate
commit after review.

---

## Labels

| Label      | Purpose                                                   |
|------------|-----------------------------------------------------------|
| `approved` | Issue is safe and ready for agent processing              |
| `unsafe`   | Issue has not been reviewed (auto-applied on creation)    |
| `blocked`  | Agent has a question; issue is paused until human answers |

The `blocked` label needs to be created (does not exist yet).

---

## Constraints & Non-Goals

- **No auto-push.** The human reviews commits before pushing to trunk.
- **No PR creation.** Trunk-based workflow; commits go directly to trunk.
- **No brainstorming visual companion.** The workflow runs without a human at the terminal.
- **No interactive Q&A.** Questions go through issue comments, not terminal prompts.
- **One issue at a time.** The script processes issues sequentially (parallel issue processing would risk conflicting
  changes).

---

## Open Questions

None — all design decisions have been made through the brainstorming dialogue.
