---
description: Process the next GitHub issue (or a specific one)
---

If an issue number is given ($ARGUMENTS), process it directly using the `process-issue` skill in autonomous mode.

If no issue number is given, use the output below to determine the next issue:

!`./process-issues --pick`

The output is tab-separated: `number\ttitle\tstatus` where status is `blocked` or `ready`.

- If the status is `blocked`: the issue was previously blocked by a question, got answered, and is ready to resume. Process it directly using the `process-issue` skill in autonomous mode.
- If the status is `ready`: this is a fresh issue that the `process-issues` harness would normally pick up automatically. Show the issue number and title to the user and ask whether they want to proceed before starting.

Use the `process-issue` skill. Run in autonomous mode.
