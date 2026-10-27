-- Summary of a trace recorded with tools/perfetto/conference-call.sh or conference-call.ps1 (docs/PERFORMANCE.md §9,
-- "Conference calls"). Run with python3 tools/perfetto/summarize.py TRACE tools/perfetto/conference-call.sql, with
-- trace_processor_shell -q tools/perfetto/conference-call.sql TRACE, or paste each query into https://ui.perfetto.dev.

-- The trace's length in seconds.
SELECT ROUND((MAX(ts + dur) - MIN(ts)) / 1e9, 1) AS seconds FROM sched;

-- Every frame's trip to TDLib and back: how many a second, and how long (ms) at the median, the 90th and 99th
-- percentiles and at most. An audio frame lasts 20 ms; a trip of 100 ms or more is dropped.
SELECT
  name,
  COUNT(*) AS count,
  ROUND(COUNT(*) / ((SELECT MAX(ts + dur) - MIN(ts) FROM sched) / 1e9), 1) AS per_second,
  ROUND((
    SELECT ranked.dur FROM (
      SELECT dur, ROW_NUMBER() OVER (ORDER BY dur) AS n, COUNT(*) OVER () AS total FROM slice AS inner_slice
      WHERE inner_slice.name = slice.name
    ) AS ranked WHERE ranked.n >= ranked.total * 0.5 ORDER BY ranked.n LIMIT 1
  ) / 1e6, 2) AS p50_ms,
  ROUND((
    SELECT ranked.dur FROM (
      SELECT dur, ROW_NUMBER() OVER (ORDER BY dur) AS n, COUNT(*) OVER () AS total FROM slice AS inner_slice
      WHERE inner_slice.name = slice.name
    ) AS ranked WHERE ranked.n >= ranked.total * 0.9 ORDER BY ranked.n LIMIT 1
  ) / 1e6, 2) AS p90_ms,
  ROUND((
    SELECT ranked.dur FROM (
      SELECT dur, ROW_NUMBER() OVER (ORDER BY dur) AS n, COUNT(*) OVER () AS total FROM slice AS inner_slice
      WHERE inner_slice.name = slice.name
    ) AS ranked WHERE ranked.n >= ranked.total * 0.99 ORDER BY ranked.n LIMIT 1
  ) / 1e6, 2) AS p99_ms,
  ROUND(MAX(dur) / 1e6, 2) AS max_ms,
  SUM(dur >= 20e6) AS over_20_ms,
  SUM(dur >= 100e6) AS dropped
FROM slice
WHERE name IN ('callEncrypt', 'callDecrypt')
GROUP BY name;

-- The same by thread: WebRTC's audio and video threads, encoders and decoders.
SELECT
  slice.name,
  thread.name AS thread,
  COUNT(*) AS count,
  ROUND(AVG(slice.dur) / 1e6, 2) AS avg_ms,
  ROUND(MAX(slice.dur) / 1e6, 2) AS max_ms
FROM slice
JOIN thread_track ON slice.track_id = thread_track.id
JOIN thread USING (utid)
WHERE slice.name IN ('callEncrypt', 'callDecrypt')
GROUP BY slice.name, thread.name
ORDER BY count DESC
LIMIT 20;

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

-- The battery's current draw (mA, negative while discharging), the whole phone.
SELECT ROUND(AVG(counter.value) / 1000) AS avg_ma
FROM counter
JOIN counter_track AS track ON counter.track_id = track.id
WHERE track.name = 'batt.current_ua';
