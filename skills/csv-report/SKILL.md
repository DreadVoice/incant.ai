---
name: csv-report
description: Summarises a CSV file into a short markdown report with per-column statistics. Use when asked to describe or profile tabular data.
---

# CSV report

Sample DOCUMENT skill. It carries a script, so it needs a sandbox that can run Python.

## Usage

Run `scripts/summarise.py <input.csv>`. It prints a markdown report to stdout: row count,
column names, and per-column type, null count, and distinct count.
