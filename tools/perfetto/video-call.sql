-- Summary of a trace recorded with tools/perfetto/video-call.sh (docs/PERFORMANCE.md §9, "Video calls").
-- Run with Perfetto's trace processor: trace_processor_shell -q tools/perfetto/video-call.sql video-call.perfetto-trace
-- or paste each query into the "Query (SQL)" page of https://ui.perfetto.dev.

-- The trace's length in seconds.
SELECT ROUND((MAX(ts + dur) - MIN(ts)) / 1e9, 1) AS seconds FROM sched;

-- Pingogram's CPU time per thread, as a share of one core over the trace (100% = one core all the time).
SELECT
  thread.name AS thread,
  ROUND(SUM(sched.dur) / 1e9, 1) AS cpu_seconds,
  ROUND(100.0 * SUM(sched.dur) / (SELECT MAX(ts + dur) - MIN(ts) FROM sched), 1) AS percent_of_one_core
FROM sched
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
GROUP BY thread.name
ORDER BY cpu_seconds DESC
LIMIT 25;

-- Pingogram's CPU time in all, the same way.
SELECT
  ROUND(100.0 * SUM(sched.dur) / (SELECT MAX(ts + dur) - MIN(ts) FROM sched), 1) AS percent_of_one_core
FROM sched
JOIN thread USING (utid)
JOIN process USING (upid)
WHERE process.name LIKE 'dev.kyralabs.pingogram%';

-- Pingogram's memory, sampled once a second (MB).
SELECT
  track.name AS counter,
  ROUND(MIN(counter.value) / 1048576, 1) AS min_mb,
  ROUND(AVG(counter.value) / 1048576, 1) AS avg_mb,
  ROUND(MAX(counter.value) / 1048576, 1) AS max_mb
FROM counter
JOIN process_counter_track AS track ON counter.track_id = track.id
JOIN process USING (upid)
WHERE process.name LIKE 'dev.kyralabs.pingogram%'
  AND track.name IN ('mem.rss', 'mem.rss.anon', 'mem.rss.file', 'mem.swap')
GROUP BY track.name;

-- The frame path's sections: how often, how long (µs), and their 90th percentile.
SELECT
  name,
  COUNT(*) AS count,
  ROUND(COUNT(*) / ((SELECT MAX(ts + dur) - MIN(ts) FROM sched) / 1e9), 1) AS per_second,
  ROUND(AVG(dur) / 1e3) AS avg_us,
  ROUND((
    SELECT MIN(ranked.dur) FROM (
      SELECT dur, PERCENT_RANK() OVER (ORDER BY dur) AS rank FROM slice AS inner_slice
      WHERE inner_slice.name = slice.name
    ) AS ranked WHERE ranked.rank >= 0.9
  ) / 1e3) AS p90_us,
  ROUND(MAX(dur) / 1e3) AS max_us
FROM slice
WHERE name IN ('callFrames', 'callFrameCopy', 'callEncrypt', 'callDecrypt')
GROUP BY name;

-- The renderers' frame rates (the counters Remote.fps and Self.fps, set once a second while frames come).
SELECT
  track.name AS counter,
  ROUND(MIN(counter.value)) AS min_fps,
  ROUND(AVG(counter.value), 1) AS avg_fps,
  ROUND(MAX(counter.value)) AS max_fps
FROM counter
JOIN process_counter_track AS track ON counter.track_id = track.id
WHERE track.name LIKE '%.fps'
GROUP BY track.name;

-- The battery's current draw (mA, negative while discharging), the whole phone.
SELECT ROUND(AVG(counter.value) / 1000) AS avg_ma
FROM counter
JOIN counter_track AS track ON counter.track_id = track.id
WHERE track.name = 'batt.current_ua';
