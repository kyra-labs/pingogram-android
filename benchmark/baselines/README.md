# Benchmark baselines

The last recorded results of the benchmarks on a device, which `tools/benchmark/compare.py` checks a new run against
(`docs/PERFORMANCE.md` §9, `docs/RELEASE.md`). One file per device, named after it, written by
`compare.py --write-baseline` from a run on that device after a release, and committed.

A baseline holds, per benchmark (`<class>.<test>`), the medians of the start times (`timeToInitialDisplayMs.median`,
`timeToFullDisplayMs.median`), the frame time percentiles (`frameDurationCpuMs.P50`, `.P90`, `.P99`), the late frames
(`frameOverrunMs.latePercent`, frames with `frameOverrunMs` > 0) and the trace sections' medians (`openChatSumMs.median`,
`app.injectSumMs.median`, …: Macrobenchmark 1.5 reports a section as its sum per iteration and its count, `…Count`;
each section runs once per iteration, so the sum is its time). Only times are compared for regressions; shares of late frames are checked against the
budgets alone (`benchmark/budgets.json`), as they swing more between runs than 10%.
