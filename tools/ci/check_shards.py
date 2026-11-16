#!/usr/bin/env python3
"""The groups of modules CI checks in parallel jobs (ci.yml's `check` matrix).

    python3 tools/ci/check_shards.py [--github-output FILE]

Reads tools/ci/check-shards.json and settings.gradle.kts, fails when a project is in no group, in two groups, or not
in settings.gradle.kts, and prints the matrix as JSON: each group's name and its Gradle tasks (`:feature:chat:check`,
`:check` for the root, whose own check runs build-logic's). With --github-output it also writes `shards=<json>` to that
file, for the job's outputs. The standard library only.
"""

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))


def included_projects(settings_text):
    """The projects settings.gradle.kts includes, as Gradle paths, plus the root (":")."""
    return {":"} | set(re.findall(r'^\s*include\(\s*"(:[^"]+)"\s*\)', settings_text, re.MULTILINE))


def check_task(project):
    """The `check` task of [project]: `:check` for the root."""
    return ":check" if project == ":" else project + ":check"


def matrix(shards, projects):
    """[shards] as the CI matrix, or a ValueError naming what does not add up against [projects]."""
    seen = {}
    problems = []
    for shard in shards:
        for project in shard["projects"]:
            if project in seen:
                problems.append("%s is in both %s and %s" % (project, seen[project], shard["name"]))
            seen[project] = shard["name"]
    for project in sorted(projects - seen.keys()):
        problems.append("%s is in no group of tools/ci/check-shards.json" % project)
    for project in sorted(seen.keys() - projects):
        problems.append("%s is not a project of settings.gradle.kts" % project)
    if problems:
        raise ValueError("; ".join(problems))
    return [{"name": s["name"], "tasks": " ".join(check_task(p) for p in s["projects"])} for s in shards]


def main(argv):
    with open(os.path.join(HERE, "check-shards.json"), encoding="utf-8") as f:
        shards = json.load(f)["shards"]
    with open(os.path.join(ROOT, "settings.gradle.kts"), encoding="utf-8") as f:
        projects = included_projects(f.read())
    try:
        result = json.dumps(matrix(shards, projects))
    except ValueError as error:
        print("check-shards: %s" % error, file=sys.stderr)
        return 1
    print(result)
    if len(argv) == 2 and argv[0] == "--github-output":
        with open(argv[1], "a", encoding="utf-8") as f:
            f.write("shards=%s\n" % result)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
