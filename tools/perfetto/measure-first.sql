-- Summary of a trace recorded with tools/perfetto/measure-first.sh (docs/PERFORMANCE.md §9, "Measure first").
-- Run with Perfetto's trace processor: trace_processor_shell -q tools/perfetto/measure-first.sql
-- measure-first.perfetto-trace, or paste each query into the "Query (SQL)" page of https://ui.perfetto.dev.
--
-- The app's sections: passcodeHash (one PBKDF2 hash: setting, changing or entering the passcode), speechPcm (one
-- decoded chunk of a voice note resampled to 16 kHz for the speech recognizer) and qrDecode (one camera frame, or a
-- picked image, searched for a QR code, mirrored and inverted ones included).

-- Each kind of section: how many, and how long each took (ms): shortest, median, 90th percentile and longest, and all
-- of them summed. A passcode entry waits for one passcodeHash; a transcription for the sum of its speechPcm chunks
-- (the recognizer's own time not included); the scanner analyses the next frame only after qrDecode ends.
WITH ranked AS (
  SELECT
    name,
    dur,
    ROW_NUMBER() OVER (PARTITION BY name ORDER BY dur) AS n,
    COUNT(*) OVER (PARTITION BY name) AS times
  FROM slice
  WHERE name IN ('passcodeHash', 'speechPcm', 'qrDecode')
)
SELECT
  name AS section,
  MAX(times) AS times,
  ROUND(MIN(dur) / 1e6, 2) AS min_ms,
  ROUND(MIN(CASE WHEN n > times * 50 / 100 THEN dur END) / 1e6, 2) AS median_ms,
  ROUND(MIN(CASE WHEN n > times * 90 / 100 THEN dur END) / 1e6, 2) AS p90_ms,
  ROUND(MAX(dur) / 1e6, 2) AS max_ms,
  ROUND(SUM(dur) / 1e6, 1) AS total_ms
FROM ranked
GROUP BY name
ORDER BY name;

-- The threads the sections ran on: none should be the main thread (is_main 1), which would freeze the screen for as
-- long as the section.
SELECT
  slice.name AS section,
  thread.name AS thread,
  thread.is_main_thread AS is_main,
  COUNT(*) AS times
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread USING (utid)
WHERE slice.name IN ('passcodeHash', 'speechPcm', 'qrDecode')
GROUP BY slice.name, thread.name, thread.is_main_thread
ORDER BY slice.name, times DESC;

-- How much of each section the thread ran on a core (Running) or waited for one (R, runnable), in ms summed per kind:
-- a section mostly waiting ran beside other work and is no slower by itself.
SELECT
  slice.name AS section,
  thread_state.state,
  ROUND(SUM(MIN(thread_state.ts + thread_state.dur, slice.ts + slice.dur) - MAX(thread_state.ts, slice.ts)) / 1e6, 1)
    AS ms
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread_state ON thread_state.utid = thread_track.utid
  AND thread_state.ts < slice.ts + slice.dur AND thread_state.ts + thread_state.dur > slice.ts
WHERE slice.name IN ('passcodeHash', 'speechPcm', 'qrDecode')
GROUP BY slice.name, thread_state.state
ORDER BY slice.name, ms DESC;
