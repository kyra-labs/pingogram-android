-- Summary of a trace recorded with tools/perfetto/media-viewer.sh (docs/PERFORMANCE.md §9, "Opening and closing
-- media"). Run with Perfetto's trace processor: trace_processor_shell -q tools/perfetto/media-viewer.sql
-- media-viewer.perfetto-trace, or paste each query into the "Query (SQL)" page of https://ui.perfetto.dev.
--
-- The app's sections: openMedia (the tap until the viewer drew the photo's page), overlayEnter (the frames of the
-- container transform in), closeMedia (back until the viewer is gone) and overlayExit (the transform out). A frame is
-- a Choreographer#doFrame slice on the app's main thread, drawn by a DrawFrames slice on its RenderThread; the frame
-- timeline says which were presented late.

-- The app's sections: how many, and how long each kind took (ms), median and worst. An opening is one tap to one
-- drawn page; a transform's section holds every frame of it.
SELECT
  slice.name AS section,
  COUNT(*) AS times,
  ROUND(MIN(slice.dur) / 1e6, 1) AS min_ms,
  ROUND(AVG(slice.dur) / 1e6, 1) AS avg_ms,
  ROUND(MAX(slice.dur) / 1e6, 1) AS max_ms
FROM slice
WHERE slice.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
GROUP BY slice.name
ORDER BY slice.name;

-- Each section, in order: its length, the main thread's frames inside it (count, their longest and 90th percentile,
-- ms) and the RenderThread's draws inside it (count, longest, ms). A frame over the display's budget (8.3 ms at 120 Hz,
-- 16.7 ms at 60 Hz) on either thread is a dropped frame of the transform.
SELECT
  s.id,
  s.name AS section,
  ROUND(s.dur / 1e6, 1) AS section_ms,
  (SELECT COUNT(*) FROM slice AS f JOIN thread_track AS tt ON f.track_id = tt.id JOIN thread USING (utid)
   JOIN process USING (upid)
   WHERE process.name LIKE 'dev.kyralabs.pingogram%' AND thread.is_main_thread
     AND f.name LIKE 'Choreographer#doFrame%' AND f.depth = 0
     AND f.ts < s.ts + s.dur AND f.ts + f.dur > s.ts) AS main_frames,
  ROUND((SELECT MAX(f.dur) FROM slice AS f JOIN thread_track AS tt ON f.track_id = tt.id JOIN thread USING (utid)
   JOIN process USING (upid)
   WHERE process.name LIKE 'dev.kyralabs.pingogram%' AND thread.is_main_thread
     AND f.name LIKE 'Choreographer#doFrame%' AND f.depth = 0
     AND f.ts < s.ts + s.dur AND f.ts + f.dur > s.ts) / 1e6, 1) AS main_frame_max_ms,
  ROUND((SELECT MIN(ranked.dur) FROM (
     SELECT f.dur, PERCENT_RANK() OVER (ORDER BY f.dur) AS rank
     FROM slice AS f JOIN thread_track AS tt ON f.track_id = tt.id JOIN thread USING (utid) JOIN process USING (upid)
     WHERE process.name LIKE 'dev.kyralabs.pingogram%' AND thread.is_main_thread
       AND f.name LIKE 'Choreographer#doFrame%' AND f.depth = 0
       AND f.ts < s.ts + s.dur AND f.ts + f.dur > s.ts) AS ranked WHERE ranked.rank >= 0.9) / 1e6, 1)
    AS main_frame_p90_ms,
  (SELECT COUNT(*) FROM slice AS d JOIN thread_track AS tt ON d.track_id = tt.id JOIN thread USING (utid)
   JOIN process USING (upid)
   WHERE process.name LIKE 'dev.kyralabs.pingogram%' AND thread.name = 'RenderThread'
     AND d.name IN ('DrawFrames', 'DrawFrame') AND d.depth = 0
     AND d.ts < s.ts + s.dur AND d.ts + d.dur > s.ts) AS render_frames,
  ROUND((SELECT MAX(d.dur) FROM slice AS d JOIN thread_track AS tt ON d.track_id = tt.id JOIN thread USING (utid)
   JOIN process USING (upid)
   WHERE process.name LIKE 'dev.kyralabs.pingogram%' AND thread.name = 'RenderThread'
     AND d.name IN ('DrawFrames', 'DrawFrame') AND d.depth = 0
     AND d.ts < s.ts + s.dur AND d.ts + d.dur > s.ts) / 1e6, 1) AS render_frame_max_ms
FROM slice AS s
WHERE s.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
ORDER BY s.ts;

-- The frames SurfaceFlinger presented late while a section ran (the frame timeline): how many of the app's frames in
-- each kind of section were late, and by how much at most, with the kind of jank the system blamed (the app's or the
-- system's). An empty result means every frame was on time, or the phone's Android has no frame timeline (before 12).
SELECT
  s.name AS section,
  COUNT(*) AS frames,
  SUM(CASE WHEN f.jank_type != 'None' THEN 1 ELSE 0 END) AS late_frames,
  ROUND(MAX(f.dur) / 1e6, 1) AS longest_frame_ms,
  GROUP_CONCAT(DISTINCT CASE WHEN f.jank_type != 'None' THEN f.jank_type END) AS jank_types
FROM slice AS s
JOIN actual_frame_timeline_slice AS f ON f.ts < s.ts + s.dur AND f.ts + f.dur > s.ts
JOIN process ON f.upid = process.upid
WHERE s.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
  AND process.name LIKE 'dev.kyralabs.pingogram%'
GROUP BY s.name
ORDER BY s.name;

-- What the main thread did inside the sections, beyond drawing frames: the longest slices (Compose's recompose,
-- measure and draw, the window relayouts that system bar and window flag changes cause, semantics for accessibility
-- services, binder calls), summed over every section of each kind. relayoutWindow or
-- checkForSemanticsChanges here means a window relayout or an accessibility service in the transform's frames.
SELECT
  s.name AS section,
  f.name AS slice,
  COUNT(*) AS times,
  ROUND(SUM(f.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(f.dur) / 1e6, 1) AS max_ms
FROM slice AS s
JOIN slice AS f ON f.ts < s.ts + s.dur AND f.ts + f.dur > s.ts
JOIN thread_track AS tt ON f.track_id = tt.id
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE s.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
  AND process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.is_main_thread
  AND f.depth BETWEEN 1 AND 3
  AND f.name NOT LIKE 'Choreographer#doFrame%'
GROUP BY s.name, f.name
ORDER BY s.name, total_ms DESC
LIMIT 60;

-- What the RenderThread did inside the sections: its longest slices (syncFrameState, DrawFrames, flush commands,
-- waiting for the GPU), summed per kind of section. Time in "waitForGpuCompletion", "queueBuffer" or "eglSwapBuffers"
-- is the GPU being slower than the frame, which offscreen layers (alpha fades, blur effects) cause.
SELECT
  s.name AS section,
  d.name AS slice,
  COUNT(*) AS times,
  ROUND(SUM(d.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(d.dur) / 1e6, 1) AS max_ms
FROM slice AS s
JOIN slice AS d ON d.ts < s.ts + s.dur AND d.ts + d.dur > s.ts
JOIN thread_track AS tt ON d.track_id = tt.id
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE s.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
  AND process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.name = 'RenderThread'
  AND d.depth BETWEEN 0 AND 2
GROUP BY s.name, d.name
ORDER BY s.name, total_ms DESC
LIMIT 40;

-- The threads of the app that used the CPU inside the sections (ms of CPU per kind of section, summed): the main
-- thread and the RenderThread draw; Coil's and TDLib's threads decode and download the photo; a video's playback
-- thread and MediaCodec threads show for a video.
SELECT
  s.name AS section,
  thread.name AS thread,
  ROUND(SUM(MIN(sched.ts + sched.dur, s.ts + s.dur) - MAX(sched.ts, s.ts)) / 1e6, 1) AS cpu_ms
FROM slice AS s
JOIN sched ON sched.ts < s.ts + s.dur AND sched.ts + sched.dur > s.ts
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE s.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
  AND process.name LIKE 'dev.kyralabs.pingogram%'
GROUP BY s.name, thread.name
HAVING cpu_ms >= 1
ORDER BY s.name, cpu_ms DESC
LIMIT 60;

-- The main thread's state inside the sections (running, runnable, sleeping, waiting for IO), in ms per kind: time
-- "R" (runnable) is waiting for a core, "D" waiting for IO or a lock, "S" sleeping (idle, or blocked on a binder
-- call whose slice the query above names).
SELECT
  s.name AS section,
  thread_state.state,
  ROUND(SUM(MIN(thread_state.ts + thread_state.dur, s.ts + s.dur) - MAX(thread_state.ts, s.ts)) / 1e6, 1) AS ms
FROM slice AS s
JOIN process ON process.name LIKE 'dev.kyralabs.pingogram%'
JOIN thread ON thread.upid = process.upid AND thread.is_main_thread
JOIN thread_state ON thread_state.utid = thread.utid
  AND thread_state.ts < s.ts + s.dur AND thread_state.ts + thread_state.dur > s.ts
WHERE s.name IN ('openMedia', 'overlayEnter', 'closeMedia', 'overlayExit')
GROUP BY s.name, thread_state.state
ORDER BY s.name, ms DESC;
