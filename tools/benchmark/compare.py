#!/usr/bin/env python3
"""Checks a Macrobenchmark run against the budgets and the last recorded baseline (docs/PERFORMANCE.md §1, §9).

    python3 tools/benchmark/compare.py RESULTS... [--baseline FILE] [--budgets FILE] [--write-baseline FILE]

RESULTS are the *-benchmarkData.json files of a run, or directories holding them (searched recursively), e.g.
benchmark/build/outputs/connected_android_test_additional_output/. It fails (exit code 1) when a benchmark misses a
budget of benchmark/budgets.json, or when a time is more than 10% above the baseline of the same benchmark (default:
the baseline in benchmark/baselines/ named after the device of the run, or the only one there). --write-baseline writes the
run as a new baseline instead of checking regressions; budgets are checked either way.

Only the Python standard library; nothing is sent anywhere.
"""

import argparse
import fnmatch
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEFAULT_BUDGETS = os.path.join(ROOT, "benchmark", "budgets.json")
BASELINES = os.path.join(ROOT, "benchmark", "baselines")

# A time more than this much above its baseline is a regression (docs/PERFORMANCE.md: "regresses by more than 10%").
REGRESSION = 0.10

# Frames whose CPU time is above this count against "0 frames > 32 ms" (docs/PERFORMANCE.md §1).
LONG_FRAME_MS = 32.0


def result_files(paths):
    """The *-benchmarkData.json files of [paths], files or directories."""
    files = []
    for path in paths:
        if os.path.isdir(path):
            for directory, _, names in os.walk(path):
                files.extend(os.path.join(directory, n) for n in names if n.endswith("benchmarkData.json"))
        else:
            files.append(path)
    return sorted(files)


def flatten(benchmark):
    """One benchmark's results as {"<metric>.<statistic>": value}, the form budgets and baselines use."""
    values = {}
    for metric, result in benchmark.get("metrics", {}).items():
        if "median" in result:
            values[metric + ".median"] = float(result["median"])
    for metric, result in benchmark.get("sampledMetrics", {}).items():
        for percentile in ("P50", "P90", "P95", "P99"):
            if percentile in result:
                values["%s.%s" % (metric, percentile)] = float(result[percentile])
        samples = [float(v) for run in result.get("runs", []) for v in run]
        if samples and metric == "frameDurationCpuMs":
            values[metric + ".over32msFrames"] = float(sum(1 for v in samples if v > LONG_FRAME_MS))
        if samples and metric == "frameOverrunMs":
            values[metric + ".latePercent"] = 100.0 * sum(1 for v in samples if v > 0) / len(samples)
    return values


def read_run(files):
    """The run's benchmarks by "<class>.<test>", and the device it ran on."""
    benchmarks = {}
    devices = set()
    for path in files:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        build = data.get("context", {}).get("build", {})
        devices.add(" ".join(filter(None, [build.get("brand"), build.get("model")])) or "unknown device")
        for benchmark in data.get("benchmarks", []):
            name = "%s.%s" % (benchmark.get("className", "").rsplit(".", 1)[-1], benchmark.get("name", ""))
            benchmarks[name] = flatten(benchmark)
    return benchmarks, sorted(devices)


def device_file(device):
    """The baseline file of [device]: its name in lower case, words joined by '-'."""
    return os.path.join(BASELINES, re.sub(r"[^a-z0-9]+", "-", device.lower()).strip("-") + ".json")


def default_baseline(devices):
    """The baseline of the run's device, or the only baseline there is; None without one."""
    if len(devices) == 1 and os.path.exists(device_file(devices[0])):
        return device_file(devices[0])
    recorded = [n for n in os.listdir(BASELINES) if n.endswith(".json")] if os.path.isdir(BASELINES) else []
    return os.path.join(BASELINES, recorded[0]) if len(recorded) == 1 else None


def is_time(key):
    """Whether [key] is a time compared for regressions: start times, frame time percentiles and trace sections."""
    statistic = key.rsplit(".", 1)[-1]
    if key.startswith("frameOverrunMs") or statistic in ("over32msFrames", "latePercent"):
        return False
    return key.split(".")[0].endswith("Ms")


def check_budgets(benchmarks, budgets):
    """The budget misses of the run, as lines to print."""
    misses = []
    for pattern, limits in budgets.items():
        if pattern.startswith("_"):
            continue
        for name, values in sorted(benchmarks.items()):
            if not fnmatch.fnmatchcase(name, pattern):
                continue
            for key, limit in limits.items():
                if key not in values:
                    misses.append("%s: %s was not measured (budget %g)" % (name, key, limit))
                elif values[key] > limit:
                    misses.append("%s: %s is %.1f, budget %g" % (name, key, values[key], limit))
    return misses


def check_regressions(benchmarks, baseline):
    """The regressions of the run against [baseline], as lines to print, and the comparisons made."""
    regressions = []
    compared = []
    for name, values in sorted(benchmarks.items()):
        recorded = baseline.get("benchmarks", {}).get(name)
        if recorded is None:
            continue
        for key, value in sorted(values.items()):
            before = recorded.get(key)
            if before is None or not is_time(key) or before <= 0:
                continue
            change = value / before - 1
            line = "%s: %s %.1f -> %.1f (%+.0f%%)" % (name, key, before, value, 100 * change)
            compared.append(line)
            if change > REGRESSION:
                regressions.append(line)
    return regressions, compared


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("results", nargs="+", help="*-benchmarkData.json files, or directories holding them")
    parser.add_argument("--budgets", default=DEFAULT_BUDGETS)
    parser.add_argument("--baseline", help="the baseline to compare with (default: the run's device's)")
    parser.add_argument("--write-baseline", metavar="FILE", help="write the run as a baseline instead of comparing")
    parser.add_argument("--commit", default="", help="recorded in a written baseline")
    args = parser.parse_args(argv)

    files = result_files(args.results)
    if not files:
        print("No *-benchmarkData.json files in %s." % ", ".join(args.results), file=sys.stderr)
        return 2
    benchmarks, devices = read_run(files)
    print("Run: %d benchmarks from %d files, on %s." % (len(benchmarks), len(files), ", ".join(devices)))

    with open(args.budgets, encoding="utf-8") as f:
        budgets = json.load(f)
    misses = check_budgets(benchmarks, budgets)

    failed = False
    if misses:
        failed = True
        print("\nBudget misses (docs/PERFORMANCE.md §1):")
        for line in misses:
            print("  " + line)
    else:
        print("\nAll budgets met.")

    if args.write_baseline:
        baseline = {"device": ", ".join(devices), "commit": args.commit, "benchmarks": benchmarks}
        with open(args.write_baseline, "w", encoding="utf-8") as f:
            json.dump(baseline, f, indent=2, sort_keys=True)
            f.write("\n")
        print("\nBaseline written: %s" % args.write_baseline)
        return 1 if failed else 0

    baseline_path = args.baseline or default_baseline(devices)
    if baseline_path is None or not os.path.exists(baseline_path):
        print("\nNo baseline for %s: regressions not checked. Record one with --write-baseline." % ", ".join(devices))
        return 1 if failed else 0
    with open(baseline_path, encoding="utf-8") as f:
        baseline = json.load(f)
    if baseline.get("device") and baseline["device"] not in devices:
        print("\nWarning: the baseline was recorded on %s, the run on %s." % (baseline["device"], ", ".join(devices)))
    regressions, compared = check_regressions(benchmarks, baseline)
    print("\nAgainst %s (%s):" % (os.path.relpath(baseline_path), baseline.get("commit") or "no commit recorded"))
    for line in compared:
        print("  " + line)
    if regressions:
        failed = True
        print("\nRegressions over %d%%:" % round(100 * REGRESSION))
        for line in regressions:
            print("  " + line)
    elif compared:
        print("\nNo regression over %d%%." % round(100 * REGRESSION))
    else:
        print("  Nothing to compare: the baseline has none of these benchmarks.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
