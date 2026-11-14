-- Summary of a trace of the chat list scrolling (docs/PERFORMANCE.md §9, "Chat list scrolling"): one of the traces
-- ChatListScrollBenchmark writes for each iteration, in
-- benchmark/build/outputs/connected_android_test_additional_output/…/ChatListScrollBenchmark_scrollBaselineProfile_iter*.perfetto-trace.
-- Run: python3 tools/perfetto/summarize.py TRACE tools/perfetto/chat-list-scroll.sql > summary.txt, or paste each
-- query into the "Query (SQL)" page of https://ui.perfetto.dev (the trace is read in the browser, not uploaded).
--
-- A frame is a Choreographer#doFrame slice on the app's main thread, drawn by a DrawFrame slice on its RenderThread
-- (some phones name it with the frame's vsync id, "DrawFrames 162342341"). The JVM's ChatListScrollCostTest sees only
-- the main thread's composing and measuring; this shows the drawing beside it.

-- The frames on each thread: how many, and their 50th and 90th percentiles and longest (ms). A frame over the
-- display's budget (8.3 ms at 120 Hz) on either thread is late. More time on the RenderThread than before, with the
-- main thread's frames unchanged, is drawing: what floats over the list (the folder bar's shadow and clip, the
-- navigation bar), or more of the list drawn.
WITH frames AS (
  SELECT
    CASE WHEN thread.is_main_thread THEN 'main' ELSE 'RenderThread' END AS thread,
    slice.dur
  FROM slice
  JOIN thread_track ON slice.track_id = thread_track.id
  JOIN thread USING (utid)
  JOIN process USING (upid)
  WHERE process.name LIKE 'dev.kyralabs.pingogram%'
    AND slice.depth = 0
    AND ((thread.is_main_thread AND slice.name LIKE 'Choreographer#doFrame%')
      OR (thread.name = 'RenderThread' AND slice.name GLOB 'DrawFrame*'))
),
ranked AS (
  SELECT thread, dur, PERCENT_RANK() OVER (PARTITION BY thread ORDER BY dur) AS rank FROM frames
)
SELECT
  thread,
  COUNT(*) AS frames,
  ROUND(MIN(CASE WHEN rank >= 0.5 THEN dur END) / 1e6, 2) AS p50_ms,
  ROUND(MIN(CASE WHEN rank >= 0.9 THEN dur END) / 1e6, 2) AS p90_ms,
  ROUND(MAX(dur) / 1e6, 2) AS max_ms,
  SUM(CASE WHEN dur > 8.3e6 THEN 1 ELSE 0 END) AS over_8_ms,
  SUM(CASE WHEN dur > 32e6 THEN 1 ELSE 0 END) AS over_32_ms
FROM ranked
GROUP BY thread
ORDER BY thread;

-- What the RenderThread spent its time on: its slices under DrawFrame, summed over the trace, longest first. Shadows,
-- clips and layers show as time in "flush commands", "Layer"/"layer" slices (an offscreen layer drawn again),
-- "shader_compile" or "Compile" (a new shader), and waiting for the GPU ("waitForGpuCompletion", "dequeueBuffer").
SELECT
  slice.name AS slice,
  slice.depth,
  COUNT(*) AS times,
  ROUND(SUM(slice.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(slice.dur) / 1e6, 2) AS max_ms
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.name = 'RenderThread'
  AND slice.depth BETWEEN 1 AND 4
GROUP BY slice.name, slice.depth
ORDER BY total_ms DESC
LIMIT 30;

-- The main thread's slices inside its frames, summed: Compose's recompose, prefetch, measure, layout and draw, and the
-- rows it composed. Unchanged here while the RenderThread grew points at drawing.
SELECT
  slice.name AS slice,
  COUNT(*) AS times,
  ROUND(SUM(slice.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(slice.dur) / 1e6, 2) AS max_ms
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.is_main_thread
  AND slice.depth BETWEEN 1 AND 4
GROUP BY slice.name
ORDER BY total_ms DESC
LIMIT 30;

-- The late frames, on either thread, in order: when (ms from the first frame), how long, and the RenderThread's
-- longest slice inside each. A long DrawFrame whose longest slice is a shader compile or a layer is drawing's cost.
WITH late AS (
  SELECT
    slice.id,
    slice.ts,
    slice.dur,
    thread.name AS thread,
    thread.utid
  FROM slice
  JOIN thread_track ON slice.track_id = thread_track.id
  JOIN thread USING (utid)
  JOIN process USING (upid)
  WHERE process.name LIKE 'dev.kyralabs.pingogram%'
    AND slice.depth = 0
    AND slice.dur > 16.6e6
    AND ((thread.is_main_thread AND slice.name LIKE 'Choreographer#doFrame%')
      OR (thread.name = 'RenderThread' AND slice.name GLOB 'DrawFrame*'))
)
SELECT
  ROUND((late.ts - (SELECT MIN(ts) FROM late)) / 1e6, 0) AS at_ms,
  late.thread,
  ROUND(late.dur / 1e6, 1) AS frame_ms,
  (SELECT inner_slice.name FROM descendant_slice(late.id) AS inner_slice
   ORDER BY inner_slice.dur DESC LIMIT 1) AS longest_inside,
  ROUND((SELECT MAX(inner_slice.dur) FROM descendant_slice(late.id) AS inner_slice) / 1e6, 1) AS longest_ms
FROM late
ORDER BY late.ts
LIMIT 40;
