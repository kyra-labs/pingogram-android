import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "benchmark"))

import compare  # noqa: E402


def scroll(overruns):
    """A scrolling benchmark whose frames were [overruns] ms late, in one run."""
    return {"sampledMetrics": {"frameOverrunMs": {"P90": 0.0, "runs": [overruns]}}}


def idle(rss_anon_kb):
    """The idle chat list's memory benchmark, its anonymous RSS [rss_anon_kb] kB at the end of each run."""
    return {"metrics": {"memoryRssAnonLastKb": {"median": rss_anon_kb}, "memoryHeapSizeLastKb": {"median": 20000.0}}}


class CompareTest(unittest.TestCase):
    def test_stutter_counts_the_frames_more_than_16_ms_late(self):
        values = compare.flatten(scroll([-3.0, 0.5, 8.0, 16.0, 16.5, 40.0, -1.0, 2.0]))

        self.assertEqual(25.0, values["frameOverrunMs.stutterPercent"])
        self.assertEqual(75.0, values["frameOverrunMs.latePercent"])

    def test_stutter_over_its_budget_is_a_miss_and_late_frames_are_not(self):
        benchmarks = {"ConversationScrollBenchmark.scroll": compare.flatten(scroll([1.0] * 99 + [20.0]))}
        budgets = {"ConversationScrollBenchmark.*": {"frameOverrunMs.stutterPercent": 0.5}}

        misses = compare.check_budgets(benchmarks, budgets)

        self.assertEqual(
            ["ConversationScrollBenchmark.scroll: frameOverrunMs.stutterPercent is 1.0, budget 0.5"], misses
        )

    def test_stutter_within_its_budget_is_no_miss_however_many_frames_are_late(self):
        benchmarks = {"ChatListScrollBenchmark.scroll": compare.flatten(scroll([1.0] * 999 + [20.0]))}
        budgets = {"ChatListScrollBenchmark.*": {"frameOverrunMs.stutterPercent": 0.5}}

        self.assertEqual([], compare.check_budgets(benchmarks, budgets))

    def test_late_and_stutter_shares_are_printed_and_never_compared_as_times(self):
        benchmarks = {"ChatListScrollBenchmark.scroll": compare.flatten(scroll([1.0, 20.0]))}

        self.assertEqual(
            ["ChatListScrollBenchmark.scroll: 100.0% late, 50.00% stutter (> 16 ms late)"],
            compare.frame_lines(benchmarks),
        )
        self.assertFalse(compare.is_time("frameOverrunMs.stutterPercent"))

    def test_memory_more_than_10_percent_above_the_baseline_is_a_regression(self):
        baseline = {"benchmarks": {"ChatListIdleMemoryBenchmark.idle": compare.flatten(idle(100000.0))}}
        run = {"ChatListIdleMemoryBenchmark.idle": compare.flatten(idle(112000.0))}

        regressions, compared = compare.check_regressions(run, baseline)

        self.assertEqual(
            ["ChatListIdleMemoryBenchmark.idle: memoryRssAnonLastKb.median 100000.0 -> 112000.0 (+12%)"],
            regressions,
        )
        self.assertEqual(2, len(compared))

    def test_memory_within_10_percent_of_the_baseline_is_no_regression(self):
        baseline = {"benchmarks": {"ChatListIdleMemoryBenchmark.idle": compare.flatten(idle(100000.0))}}
        run = {"ChatListIdleMemoryBenchmark.idle": compare.flatten(idle(108000.0))}

        self.assertEqual([], compare.check_regressions(run, baseline)[0])
        self.assertTrue(compare.is_memory("memoryRssAnonLastKb.median"))
        self.assertFalse(compare.is_memory("timeToInitialDisplayMs.median"))

    def test_the_repository_budgets_hold_stutter_not_late_frames(self):
        import json

        with open(compare.DEFAULT_BUDGETS, encoding="utf-8") as f:
            budgets = json.load(f)
        for pattern in ("ConversationScrollBenchmark.*", "ChatListScrollBenchmark.*"):
            self.assertEqual(0.5, budgets[pattern]["frameOverrunMs.stutterPercent"])
            self.assertNotIn("frameOverrunMs.latePercent", budgets[pattern])


if __name__ == "__main__":
    unittest.main()
