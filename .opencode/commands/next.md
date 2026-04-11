---
description: Process the next GitHub issue (or a specific one)
---

If an issue number is given (i.e. `$ARGUMENTS` is not empty), process issue **#$ARGUMENTS** directly using the `process-issue` skill in interactive mode. Start immediately — no user confirmation needed. **STOP READING HERE — ignore everything below.**

---

## Pick Next (only when no issue number is given)

Use the output below to determine the next issue:

!`./process-issues --pick`

The output is tab-separated: `number\ttitle\tstatus` where status is `blocked` or `ready`.

- If the status is `blocked`: the issue was previously blocked by a question, got answered, and is ready to resume. Process it directly using the `process-issue` skill in interactive mode.
- If the status is `ready`: this is a fresh issue that the `process-issues` harness would normally pick up automatically. Show the issue number and title to the user and ask whether they want to proceed before starting. Also check the blocked issues list below — if there is a blocked issue available, offer it as an alternative for interactive brainstorming.

Blocked issues (available for interactive brainstorming):

!`./process-issues --dry-run 2>/dev/null | grep BLOCKED || echo "(none)"`

If there are blocked issues and the next ready issue is shown, present both options to the user:
1. Process the ready issue autonomously (the default)
2. Brainstorm the blocked issue interactively (show its number and title)

Use the `process-issue` skill. Run in interactive mode.
