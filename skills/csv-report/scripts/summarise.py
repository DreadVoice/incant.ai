import csv
import sys
from collections import defaultdict


def summarise(path):
    with open(path, newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))

    if not rows:
        return "# CSV report\n\nNo rows.\n"

    columns = rows[0].keys()
    values = defaultdict(list)
    for row in rows:
        for column in columns:
            values[column].append(row[column])

    lines = ["# CSV report", "", f"Rows: {len(rows)}", "", "| column | nulls | distinct |", "|---|---|---|"]
    for column in columns:
        cells = values[column]
        nulls = sum(1 for cell in cells if cell is None or cell.strip() == "")
        lines.append(f"| {column} | {nulls} | {len(set(cells))} |")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: summarise.py <input.csv>", file=sys.stderr)
        raise SystemExit(2)
    print(summarise(sys.argv[1]), end="")
