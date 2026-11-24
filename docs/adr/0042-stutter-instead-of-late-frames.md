# 0042. Stutter instead of late frames

- Status: proposed (for the owner to accept or decline)
- Concerns the conversation scroll budget of `docs/PERFORMANCE.md` §1 ("jank rate < 1%") and
  `benchmark/budgets.json` (`ConversationScrollBenchmark.*`, `frameOverrunMs.latePercent` ≤ 1).

## Context

The conversation scroll budget counts a frame as janky when it is late at all: `frameOverrunMs` > 0, the share of such
frames at most 1% (`tools/benchmark/compare.py`'s `latePercent`). No release has met it since 2.3.0: on the owner's
S24 Ultra at 120 Hz, 2.4.0 measured 52–57% late frames, most of them Buffer Stuffing in the trace (owner, 2026-11-23).
Earlier runs show the same split: at v0.5.0 the conversation had 59% late frames and 0.05% stutter (§9).

Most of those late frames are Buffer Stuffing in the frame timeline: when the app produces frames ahead of the display
(the render thread queues a buffer while SurfaceFlinger still holds two), `FrameTimeline` reports each of them as late
against its expected present time, though none is dropped and the display never repeats a frame. Perfetto names that
jank type `Buffer Stuffing`; it is a scheduling artefact of a pipeline that is full, not work the app could take out.
At 120 Hz a frame has 8.3 ms, so any queueing shows up as overrun on most frames.

`latePercent` therefore no longer tells a smooth scroll from a stuttering one: a change that makes the conversation
visibly smoother can leave it at 55%, and one that adds a dropped frame every second barely moves it. The other lists
already report the measure that does: stutter, the share of frames more than 16 ms late, which the §9 history records
alongside (0.36% chat list, 0.05% conversation at v0.5.0).

## Decision (proposed)

1. **The conversation's budget becomes a stutter budget.** "Jank rate < 1%" is replaced by: at most 0.5% of frames
   more than 16 ms late (`frameOverrunMs` > 16), the frames a user sees as a hitch at 60 and 120 Hz alike (two frames
   at 120 Hz, one at 60 Hz).
2. **The chat list gets the same stutter budget**, next to its P90 and its zero frames over 32 ms, so both lists are
   held to the same measure.
3. **Late frames stay reported, not budgeted.** `compare.py` keeps printing `latePercent` for the record (§9), without
   failing on it.
4. **How it is measured.** `compare.py` adds a `stutterPercent` statistic of `frameOverrunMs` (share of samples
   > 16 ms), and `benchmark/budgets.json` replaces `ConversationScrollBenchmark.*` `frameOverrunMs.latePercent: 1` with
   `frameOverrunMs.stutterPercent: 0.5` and adds the same to `ChatListScrollBenchmark.*`. These two files change only
   once this is accepted, in their own pull request (`budgets.json` is the owner's).

## Consequences

- The budget follows what a user sees: dropped or repeated frames. Buffer Stuffing no longer fails a release.
- 0.5% is above what v0.5.0 measured for both lists (0.36%, 0.05%) and below the 1% the conversation was meant to
  stay under; a regression that drops a frame every two seconds of scrolling at 120 Hz (about 0.4%) still shows in the
  10% regression check.
- A frame 10 ms late is no longer counted. The P90 CPU budgets (8 and 10 ms) still catch work that grows within a
  frame before it starts to drop frames.
- Alternatives not taken: keeping `latePercent` with a higher limit (it would still move with the display's queue,
  not with the app's work); Perfetto's own jank types (needs trace processing in `compare.py` for each run, and the
  benchmark's `FrameTimingMetric` already gives `frameOverrunMs`).
