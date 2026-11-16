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

-- The RenderThread's texture uploads, by size: how many, in how many frames, how long, and the three slices they ran
-- under (outermost first). An upload under "flush commands" in nearly every frame, of the size of something on screen,
-- is a clip mask Skia rasterizes on the CPU, for a draw that crosses a clip Android could not hand the GPU as a
-- rectangle or a round rect (a path: the chat list's sheet, rounded at the top only, was one until ContentSheet's
-- SheetShape), or a bitmap drawn without a cached texture. Compare the sizes with the floating folder bar (about
-- 1390 x 195 px on a 1440 px wide phone) and the list (1440 px wide, the sheet's height).
SELECT
  TRIM(SUBSTR(upload.name, INSTR(upload.name, ')') + 1)) AS size,
  -- The frame's own slice is left out: some phones name it with the frame's vsync id.
  COALESCE(CASE WHEN p3.depth > 0 THEN p3.name || ' > ' END, '')
    || COALESCE(CASE WHEN p2.depth > 0 THEN p2.name || ' > ' END, '')
    || COALESCE(CASE WHEN p1.depth > 0 THEN p1.name END, '(the frame)') AS under,
  COUNT(*) AS uploads,
  COUNT(DISTINCT frame.id) AS frames,
  ROUND(SUM(upload.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(upload.dur) / 1e6, 2) AS max_ms
FROM slice AS upload
JOIN thread_track ON upload.track_id = thread_track.id
JOIN thread USING (utid)
JOIN process USING (upid)
LEFT JOIN slice AS p1 ON p1.id = upload.parent_id
LEFT JOIN slice AS p2 ON p2.id = p1.parent_id
LEFT JOIN slice AS p3 ON p3.id = p2.parent_id
LEFT JOIN slice AS frame ON frame.track_id = upload.track_id AND frame.depth = 0
  AND upload.ts >= frame.ts AND upload.ts < frame.ts + frame.dur
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.name = 'RenderThread'
  AND upload.name GLOB 'Texture upload*'
GROUP BY size, under
ORDER BY uploads DESC
LIMIT 20;

-- What the main thread's frames were for: each Choreographer#doFrame by the kinds of work under it. Compose names its
-- frame clock's dispatch "Recomposer:animation" (a fling, a scroll animation, any withFrameNanos) and the recomposer's
-- loop of that frame "Recomposer:recompose", whether or not anything was invalid; a composition that did recompose
-- applies its changes under "Compose:applyChanges". So a frame of a fling carries both Recomposer slices without having
-- recomposed anything, and counts as "animation" here; "recompose" means a composition changed. Frames with a traversal
-- and nothing else were asked for by an invalidated view or layer (a draw that keeps invalidating itself, or a state
-- written in layout or draw). Compare the counts between two builds over the same journey.
WITH frame AS (
  SELECT slice.id, slice.ts, slice.dur, slice.track_id
  FROM slice
  JOIN thread_track ON slice.track_id = thread_track.id
  JOIN thread USING (utid)
  JOIN process USING (upid)
  WHERE process.name LIKE 'dev.kyralabs.pingogram%'
    AND thread.is_main_thread
    AND slice.depth = 0
    AND slice.name LIKE 'Choreographer#doFrame%'
),
kinds AS (
  SELECT
    frame.id,
    frame.dur,
    MAX(COALESCE(child.name = 'input', 0)) AS input,
    MAX(COALESCE(child.name = 'animation' OR child.name = 'Recomposer:animation', 0)) AS animation,
    MAX(COALESCE(child.name = 'traversal', 0)) AS traversal,
    MAX(COALESCE(child.name = 'Compose:applyChanges', 0)) AS recompose,
    MAX(COALESCE(child.name GLOB '*prefetch*', 0)) AS prefetch
  FROM frame
  LEFT JOIN slice AS child ON child.track_id = frame.track_id AND child.depth BETWEEN 1 AND 8
    AND child.ts >= frame.ts AND child.ts < frame.ts + frame.dur
  GROUP BY frame.id
)
SELECT
  CASE
    WHEN input AND recompose THEN 'input, recompose'
    WHEN input THEN 'input'
    WHEN recompose THEN 'recompose'
    WHEN animation THEN 'animation (a fling, no recomposition)'
    WHEN prefetch THEN 'prefetch only'
    WHEN traversal THEN 'invalidated draw or layout only'
    ELSE 'other'
  END AS frame_for,
  COUNT(*) AS frames,
  ROUND(AVG(dur) / 1e6, 2) AS avg_ms,
  ROUND(MAX(dur) / 1e6, 2) AS max_ms
FROM kinds
GROUP BY frame_for
ORDER BY frames DESC;

-- Compose's own slices inside the main thread's frames, summed: how many frames carry each and how long they take.
-- "Compose:applyChanges" in many frames, with the JVM's ChatListRecompositionTest finding nothing recomposed while the
-- list flings, points at something outside the chat list screen (the app's shell) or at what the test leaves out
-- (pictures loading into rows); "Recomposer:animation" alone is the fling itself.
SELECT
  child.name AS slice,
  COUNT(DISTINCT frame.id) AS frames,
  COUNT(*) AS times,
  ROUND(SUM(child.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(child.dur) / 1e6, 2) AS max_ms
FROM slice AS frame
JOIN thread_track ON frame.track_id = thread_track.id
JOIN thread USING (utid)
JOIN process USING (upid)
JOIN slice AS child ON child.track_id = frame.track_id AND child.depth > 0
  AND child.ts >= frame.ts AND child.ts < frame.ts + frame.dur
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.is_main_thread
  AND frame.depth = 0
  AND frame.name LIKE 'Choreographer#doFrame%'
  AND (child.name GLOB 'Recomposer:*' OR child.name GLOB 'Compose:*' OR child.name GLOB 'compose:*')
GROUP BY child.name
ORDER BY frames DESC;

-- The cache window's long frames (docs/PERFORMANCE.md §9, "Chat list scrolling"): every
-- "compose:lazy:cache_window:keepAroundItems" over 2 ms, which composes, inside the frame's measure pass, the rows of
-- the cache window (rememberScrollingListState: half a screen ahead, one behind) that no prefetch composed yet. When it
-- ran (ms after the trace's first frame: the start of the journey, a fling, its turn back), how long, and what ran
-- directly inside it, by slice name: one "compose:lazy:prefetch…" or "Compose:recompose…" per row composed tells how
-- many rows it took, and their own children what each cost (text layouts, images).
WITH first_frame AS (
  SELECT MIN(slice.ts) AS ts
  FROM slice
  JOIN thread_track ON slice.track_id = thread_track.id
  JOIN thread USING (utid)
  JOIN process USING (upid)
  WHERE process.name LIKE 'dev.kyralabs.pingogram%'
    AND thread.is_main_thread
    AND slice.depth = 0
    AND slice.name LIKE 'Choreographer#doFrame%'
),
spikes AS (
  SELECT slice.id, slice.ts, slice.dur, slice.track_id, slice.depth
  FROM slice
  JOIN thread_track ON slice.track_id = thread_track.id
  JOIN thread USING (utid)
  JOIN process USING (upid)
  WHERE process.name LIKE 'dev.kyralabs.pingogram%'
    AND slice.name = 'compose:lazy:cache_window:keepAroundItems'
    AND slice.dur > 2e6
)
SELECT
  ROUND((spikes.ts - first_frame.ts) / 1e6) AS at_ms,
  ROUND(spikes.dur / 1e6, 1) AS keep_around_ms,
  COALESCE(child.name, '(nothing traced inside)') AS inside,
  COUNT(child.id) AS times,
  ROUND(SUM(child.dur) / 1e6, 2) AS total_ms,
  ROUND(MAX(child.dur) / 1e6, 2) AS max_ms
FROM spikes
CROSS JOIN first_frame
LEFT JOIN slice AS child ON child.track_id = spikes.track_id AND child.depth = spikes.depth + 1
  AND child.ts >= spikes.ts AND child.ts < spikes.ts + spikes.dur
GROUP BY spikes.id, child.name
ORDER BY spikes.ts, total_ms DESC;
