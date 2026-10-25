#!/usr/bin/env python3
"""Runs the queries of a .sql file of tools/perfetto/ on a trace and prints their results as text tables.

    python3 tools/perfetto/summarize.py TRACE [SQL] > summary.txt

TRACE is a trace recorded by startup.sh / startup.ps1 (or video-call.sh); SQL defaults to startup.sql beside this
script. A trace of a few starts is hundreds of MB, too large to send; its summary is a few KB of numbers (times,
section names, thread states), with nothing of what the app showed.

It needs Perfetto's Python package (`pip install perfetto`), whose trace processor downloads Perfetto's
trace_processor_shell once, from Google, the first time it runs. The trace is read on this computer only.
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def statements(path):
    """The statements of [path] with the comment lines just above each, as (title, sql) pairs."""
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    result = []
    title, body = [], []
    for line in lines:
        stripped = line.strip()
        if not body and stripped.startswith("--"):
            title.append(stripped.lstrip("- ").rstrip())
            continue
        if not body and not stripped:
            continue
        body.append(line)
        if stripped.endswith(";"):
            result.append((" ".join(t for t in title if t), "\n".join(body)))
            title, body = [], []
    if body:
        result.append((" ".join(title), "\n".join(body)))
    return result


def table(columns, rows):
    """[rows] under [columns], each column as wide as its widest value."""
    cells = [[("" if v is None else str(v)) for v in row] for row in rows]
    widths = [max([len(c)] + [len(r[i]) for r in cells]) for i, c in enumerate(columns)]
    lines = ["  ".join(c.ljust(w) for c, w in zip(columns, widths))]
    lines.append("  ".join("-" * w for w in widths))
    lines.extend("  ".join(v.ljust(w) for v, w in zip(row, widths)) for row in cells)
    return "\n".join(lines)


def main(argv):
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 2
    trace = argv[0]
    sql = argv[1] if len(argv) > 1 else os.path.join(HERE, "startup.sql")
    try:
        from perfetto.trace_processor import TraceProcessor
    except ImportError:
        print("Perfetto's Python package is missing: pip install perfetto", file=sys.stderr)
        return 2

    # UTF-8 whatever the console's code page (Windows), as the queries' titles have "§" and "µs".
    sys.stdout.reconfigure(encoding="utf-8")
    tp = TraceProcessor(trace=trace)
    try:
        for title, statement in statements(sql):
            if statement.lstrip().upper().startswith("INCLUDE"):
                tp.query(statement)
                continue
            print("## " + (title or "Query"))
            try:
                result = tp.query(statement)
                columns = list(result.column_names)
                rows = [[getattr(row, c) for c in columns] for row in result]
                print(table(columns, rows) if rows else "(no rows)")
            except Exception as error:  # A query the trace has no data for; the others still run.
                print("(failed: %s)" % str(error).splitlines()[0])
            print()
    finally:
        tp.close()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
