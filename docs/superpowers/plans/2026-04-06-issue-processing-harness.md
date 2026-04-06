# Issue Processing Harness — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a hybrid skill + shell script system that autonomously processes approved GitHub issues through refinement, planning, TDD implementation, and validation.

**Architecture:** A project-local skill (`process-issue`) defines the per-issue workflow. A shell script (`process-issues`) loops over approved issues, spawning fresh `opencode run` sessions per issue. Questions are communicated via GitHub issue comments + a `blocked` label.

**Tech Stack:** Shell (zsh), OpenCode CLI (`opencode run`), GitHub CLI (`gh`), existing superpowers/tdder skills.

---

## File Structure

| File | Responsibility |
|------|---------------|
| `.opencode/skills/process-issue/SKILL.md` | Per-issue workflow skill — loaded by the agent at session start |
| `process-issues` | Shell script — loops over issues, spawns agent sessions, handles outcomes |

---

### Task 1: Create the `blocked` GitHub label

**Files:**
- None (GitHub API only)

- [ ] **Step 1: Create the label**

```bash
gh label create blocked --description "Agent has a question; issue paused until human answers" --color "FBCA04"
```

Run: `gh label create blocked --description "Agent has a question; issue paused until human answers" --color "FBCA04"`
Expected: Label created successfully (or "already exists" if re-run).

- [ ] **Step 2: Verify the label exists**

Run: `gh label list | grep blocked`
Expected: `blocked  Agent has a question; issue paused until human answers  #FBCA04`

---

### Task 2: Create the `process-issue` skill

**Files:**
- Create: `.opencode/skills/process-issue/SKILL.md`

- [ ] **Step 1: Create the skill directory**

```bash
mkdir -p .opencode/skills/process-issue
```

- [ ] **Step 2: Write the SKILL.md**

Create `.opencode/skills/process-issue/SKILL.md` with the following content:

````markdown
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
````

- [ ] **Step 3: Verify the skill is discoverable**

Run: `opencode run "List all available skills" --format json 2>&1 | grep -o 'process-issue' | head -1`
Expected: `process-issue` appears in the output.

- [ ] **Step 4: Stage the skill file**

```bash
git add -f .opencode/skills/process-issue/SKILL.md
```

---

### Task 3: Create the `process-issues` shell script

**Files:**
- Create: `process-issues`

- [ ] **Step 1: Write the script**

Create `process-issues` with the following content:

```zsh
#!/usr/bin/env zsh
# process-issues — loop over approved GitHub issues and process them with OpenCode.
#
# Usage:
#   ./process-issues [--dry-run] [--issue N]
#
# Options:
#   --dry-run   List issues that would be processed without spawning agents.
#   --issue N   Process only issue #N (skip the automatic selection).

set -euo pipefail

DRY_RUN=false
SPECIFIC_ISSUE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --issue)
            SPECIFIC_ISSUE="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1" >&2
            echo "Usage: $0 [--dry-run] [--issue N]" >&2
            exit 1
            ;;
    esac
done

# Update plugins before starting (same as ./oc wrapper).
bun update tdder superpowers --cwd ~/.cache/opencode --silent 2>/dev/null || true

pick_next_issue() {
    if [[ -n "$SPECIFIC_ISSUE" ]]; then
        # Verify the specific issue is approved and not blocked.
        local issue_json
        issue_json=$(gh issue view "$SPECIFIC_ISSUE" --json number,title,labels 2>/dev/null) || {
            echo "Issue #$SPECIFIC_ISSUE not found." >&2
            return 1
        }
        local labels
        labels=$(echo "$issue_json" | jq -r '[.labels[].name] | join(",")')
        if [[ "$labels" != *"approved"* ]]; then
            echo "Issue #$SPECIFIC_ISSUE is not labeled 'approved'. Skipping." >&2
            return 1
        fi
        if [[ "$labels" == *"blocked"* ]]; then
            echo "Issue #$SPECIFIC_ISSUE is labeled 'blocked'. Answer the question first." >&2
            return 1
        fi
        echo "$issue_json" | jq -r '"\(.number)\t\(.title)"'
        return 0
    fi

    # Pick the oldest approved, non-blocked, open issue.
    local issues
    issues=$(gh issue list \
        --label approved \
        --state open \
        --sort created \
        --json number,title,labels \
        --limit 50)

    # Filter out issues with the 'blocked' label.
    echo "$issues" | jq -r '
        [.[] | select(.labels | map(.name) | index("blocked") | not)]
        | sort_by(.number)
        | first
        | "\(.number)\t\(.title)"
    ' 2>/dev/null || return 1
}

process_one_issue() {
    local issue_number="$1"
    local issue_title="$2"

    echo "=== Processing issue #${issue_number}: ${issue_title} ==="

    if [[ "$DRY_RUN" == "true" ]]; then
        echo "  [dry-run] Would spawn: opencode run for issue #${issue_number}"
        return 0
    fi

    # Spawn a fresh OpenCode session.
    opencode run \
        "Load the process-issue skill. Work on issue #${issue_number} for the openapi-ui-next project." \
        --title "Issue #${issue_number}: ${issue_title}" \
        2>&1 | tee "/tmp/process-issue-${issue_number}.log"

    local exit_code=${pipestatus[1]}

    # Check outcome.
    local labels
    labels=$(gh issue view "$issue_number" --json labels --jq '[.labels[].name] | join(",")' 2>/dev/null || echo "")

    if [[ "$labels" == *"blocked"* ]]; then
        echo "  -> Issue #${issue_number} is BLOCKED. Check the issue for a question."
        return 0
    fi

    local state
    state=$(gh issue view "$issue_number" --json state --jq '.state' 2>/dev/null || echo "OPEN")

    if [[ "$state" == "CLOSED" ]]; then
        echo "  -> Issue #${issue_number} COMPLETED."
        return 0
    fi

    if [[ $exit_code -ne 0 ]]; then
        echo "  -> Issue #${issue_number}: session exited with code ${exit_code}." >&2
        return 1
    fi

    echo "  -> Issue #${issue_number}: session ended without closing the issue."
    return 0
}

# Main loop.
if [[ -n "$SPECIFIC_ISSUE" ]]; then
    # Process just the one specified issue.
    line=$(pick_next_issue) || exit 1
    issue_number=$(echo "$line" | cut -f1)
    issue_title=$(echo "$line" | cut -f2-)
    process_one_issue "$issue_number" "$issue_title"
else
    # Loop through all eligible issues.
    while true; do
        line=$(pick_next_issue) || {
            echo "No more eligible issues."
            break
        }
        # Guard against jq returning "null" when no issues match.
        if [[ "$line" == "null"* || -z "$line" ]]; then
            echo "No more eligible issues."
            break
        fi
        issue_number=$(echo "$line" | cut -f1)
        issue_title=$(echo "$line" | cut -f2-)
        process_one_issue "$issue_number" "$issue_title" || {
            echo "Stopping due to error on issue #${issue_number}."
            break
        }
    done
fi

echo "Done."
```

- [ ] **Step 2: Make the script executable**

```bash
chmod +x process-issues
```

- [ ] **Step 3: Stage the script**

```bash
git add process-issues
```

---

### Task 4: Update `.gitignore` for docs/superpowers

The `docs/superpowers/` directory is currently gitignored (we had to use `git add -f`). We should explicitly un-ignore it so specs and plans can be committed normally.

**Files:**
- Modify: `.gitignore` (if it exists and contains the pattern)

- [ ] **Step 1: Check what's ignoring docs/superpowers**

```bash
git check-ignore -v docs/superpowers/specs/2026-04-06-issue-processing-harness-design.md
```

This will show which gitignore rule is causing the ignore.

- [ ] **Step 2: Add an exception**

Based on the output from Step 1, add a negation rule. For example, if the rule is in `.gitignore`:

Add this line to `.gitignore`:
```
!docs/superpowers/
```

If the ignore is in a nested or global gitignore, add the negation at the appropriate level.

- [ ] **Step 3: Verify docs/superpowers is no longer ignored**

```bash
git check-ignore docs/superpowers/specs/test.md
# Should produce no output (not ignored)
```

- [ ] **Step 4: Stage the .gitignore change**

```bash
git add .gitignore
```

---

### Task 5: Commit everything

**Files:**
- All staged files from Tasks 1-4

- [ ] **Step 1: Review staged changes**

```bash
git status
git diff --cached --stat
```

Expected: `.opencode/skills/process-issue/SKILL.md`, `process-issues`, and possibly `.gitignore` are staged.

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: add issue-processing harness (skill + script)"
```

---

### Task 6: Smoke test with `--dry-run`

- [ ] **Step 1: Run the script in dry-run mode**

```bash
./process-issues --dry-run
```

Expected output (approximately):
```
=== Processing issue #4: Add links to responses: ... ===
  [dry-run] Would spawn: opencode run for issue #4
=== Processing issue #5: Apply httpie defaults: ... ===
  [dry-run] Would spawn: opencode run for issue #5
...
Done.
```

The issues should appear in ascending order by number, and none should be `blocked`.

- [ ] **Step 2: Test with a specific issue**

```bash
./process-issues --dry-run --issue 4
```

Expected: Only issue #4 is listed.

- [ ] **Step 3: Test with a non-existent issue**

```bash
./process-issues --dry-run --issue 9999
```

Expected: Error message about issue not found.

---

### Task 7: Live test on a real issue

This is the integration test. Pick a small, well-defined issue to validate the full workflow.

- [ ] **Step 1: Pick a candidate issue**

Issue #8 ("Body textarea doesn't grow in height when content is added or width is reduced") is a well-defined bug — good for a first test.

- [ ] **Step 2: Run the harness**

```bash
./process-issues --issue 8
```

- [ ] **Step 3: Observe the outcome**

Watch for:
- Did the agent load the `process-issue` skill?
- Did it create a spec and plan?
- Did it follow TDD (failing test first)?
- Did tests pass?
- Was a commit created?
- Was the issue closed?

If the agent posted a question (blocked), answer it and re-run.

- [ ] **Step 4: Review the commit**

```bash
git log -1 --stat
git diff HEAD~1
```

Verify the commit is well-structured and the changes are correct.

- [ ] **Step 5: Review the artifacts**

Check that spec and plan files exist in `docs/superpowers/`.

---

### Task 8: Clean up after review

After reviewing the implementation commit and the spec/plan artifacts:

- [ ] **Step 1: Remove spec and plan files**

```bash
git rm -f docs/superpowers/specs/*-issue-8-*.md docs/superpowers/plans/*-issue-8-*.md
git commit -m "docs: remove spec and plan for #8"
```

This is the separate cleanup commit the spec calls for.
