-- Summary of a trace recorded with tools/perfetto/startup.sh (docs/PERFORMANCE.md §2, §9).
-- Run with Perfetto's trace processor: trace_processor_shell -q tools/perfetto/startup.sql startup.perfetto-trace
-- or paste each query into the "Query (SQL)" page of https://ui.perfetto.dev.

INCLUDE PERFETTO MODULE android.startup.startups;
INCLUDE PERFETTO MODULE android.startup.time_to_display;

-- Each start: cold or warm, to the first frame (time to initial display) and to reportFullyDrawn (time to full display),
-- in milliseconds.
SELECT
  s.startup_id,
  s.startup_type,
  ROUND(s.dur / 1e6, 1) AS to_first_frame_ms,
  ROUND((SELECT ttfd.time_to_full_display FROM android_startup_time_to_display AS ttfd
         WHERE ttfd.startup_id = s.startup_id) / 1e6, 1) AS to_fully_drawn_ms
FROM android_startups AS s
WHERE s.package LIKE 'dev.kyralabs.pingogram%'
ORDER BY s.ts;

-- The app's own sections (docs/PERFORMANCE.md §2): how long each took per start, median and worst.
SELECT
  slice.name AS section,
  COUNT(*) AS times,
  ROUND(MIN(slice.dur) / 1e6, 1) AS min_ms,
  ROUND(AVG(slice.dur) / 1e6, 1) AS avg_ms,
  ROUND(MAX(slice.dur) / 1e6, 1) AS max_ms
FROM slice
WHERE slice.name IN ('app.inject', 'app.tdlibStart', 'app.starters', 'main.onCreate', 'main.awaitSettings', 'openChat')
GROUP BY slice.name
ORDER BY slice.name;

-- What the main thread did during the starts, beyond the app's sections: the longest framework slices, summed over all
-- starts (bindApplication, activityStart, inflate, Choreographer#doFrame, binder transactions, class loading).
SELECT
  slice.name,
  COUNT(*) AS times,
  ROUND(SUM(slice.dur) / 1e6, 1) AS total_ms,
  ROUND(MAX(slice.dur) / 1e6, 1) AS max_ms
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
  AND thread.is_main_thread
  AND slice.depth <= 1
GROUP BY slice.name
ORDER BY total_ms DESC
LIMIT 30;

-- The main thread's time per state during each start (running, runnable, sleeping, waiting for IO), in ms.
SELECT
  s.startup_id,
  s.startup_type,
  thread_state.state,
  ROUND(SUM(thread_state.dur) / 1e6, 1) AS ms
FROM android_startups AS s
JOIN process ON process.name LIKE 'dev.kyralabs.pingogram%'
JOIN thread ON thread.upid = process.upid AND thread.is_main_thread
JOIN thread_state ON thread_state.utid = thread.utid
  AND thread_state.ts >= s.ts AND thread_state.ts < s.ts + s.dur
WHERE s.package LIKE 'dev.kyralabs.pingogram%'
GROUP BY s.startup_id, thread_state.state
ORDER BY s.startup_id, ms DESC;

-- The emoji font androidx.emoji2 loads after the first frame (docs/PERFORMANCE.md §2, "Still open"): when it ran, on
-- which thread, and how long it took. Compose lays out every paragraph again once it is loaded; the frames right after
-- "EmojiCompat.MetadataRepo.create" show that cost.
SELECT
  thread.name AS thread,
  slice.name,
  ROUND((slice.ts - (SELECT MAX(s.ts) FROM android_startups AS s
                     WHERE s.ts <= slice.ts AND s.package LIKE 'dev.kyralabs.pingogram%')) / 1e6, 1) AS after_start_ms,
  ROUND(slice.dur / 1e6, 1) AS ms
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread USING (utid)
WHERE slice.name LIKE 'EmojiCompat%'
ORDER BY slice.ts;
