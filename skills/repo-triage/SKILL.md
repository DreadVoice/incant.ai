---
name: repo-triage
description: Triages a failing build in a checked-out repository by reading the error, locating the responsible file, and applying a minimal fix. Use when a build or test suite is broken.
allowed-tools: Read, Edit, Write, Bash
---

# Repo triage

Sample CODING_AGENT skill. It edits files and runs commands, so it needs an active workspace.

## Procedure

1. Run the build and read the first error, not the last.
2. Open the file it names and confirm the error is real before changing anything.
3. Make the smallest fix that addresses the cause.
4. Re-run the build.
5. Report what broke, what changed, and what is still failing.
