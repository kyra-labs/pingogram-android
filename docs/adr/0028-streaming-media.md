# 0028 · Streaming videos and songs

Status: accepted (the owner's item 2 of the feature gaps, 2026-10-24: "make videos and songs start playing while TDLib
downloads them")

## Context

The media viewer plays a video once TDLib has downloaded all of it (`FileState.Downloaded`), and a conversation plays
a song or voice message the same way. A video of 200 MB waits minutes before its first frame; Telegram's apps start in
a second or two.

What the pinned versions offer:

- **TDLib** (a94b83d) downloads a file in parts: `downloadFile(file_id, priority, offset, limit, synchronous)` from any
  offset (`limit` 0: to the end), one range at a time per file (a new offset moves the download), and reports in
  `updateFile` where it is (`local.download_offset`, `local.downloaded_prefix_size`) and the partial file's
  `local.path`, which stays readable while it grows. `video.supports_streaming` says a video is laid out for playing as
  it arrives.
- **Media3** (1.11.1) reads media through a `DataSource`, on its loading thread, which may block: it opens at a
  position, reads, and on a seek closes and opens again at the new one. `media3-datasource` is part of
  `media3-exoplayer`, which the viewer and the conversation already ship.

## Decision

- **`core:domain`'s `FileStreamRepository`** (`TdlibFileStreamRepository`, `core:data`, no cache): `awaitRange(file,
  offset, length)` waits until that many bytes from the offset are on the device, downloading from the offset at
  visible priority (32) unless a download it started covers it, and answers the partial file's path; it subscribes to
  `updateFile` before it asks, and gives up when the download stops short of the range. `stop(file)` cancels the
  download it started (`cancelDownloadFile`), keeping what came, unless the file is complete.
- **`core:ui`'s `TdlibDataSource`**, a Media3 `DataSource` over it, for `tdlib-file://<file id>` URIs: each read waits
  for at least 64 KB (fewer at the end) on the player's loading thread, at most 20 s, then reads the partial file
  directly. Closing it (the player leaving the file, a seek) stops the download, so a closed viewer downloads nothing
  more (`docs/PERFORMANCE.md` §3). In `core:ui`, as the features that play depend on it and not on each other.
- **The viewer** streams videos that support it (`supportsStreaming`): they play as they arrive, and the viewer's
  own downloads leave the file to the stream, as two downloads of one file would move each other's offset. Other
  videos and GIFs wait for their file as before. The seek bar shows how far the player has buffered.
- **The conversation** streams songs as the viewer streams videos, in a change of its own once the conversation's
  player (`ExoAudioPlayer`, raise to listen) has settled.

## Consequences

- A seek past what is downloaded downloads from there, and what lies before stays missing until it is played: TDLib
  keeps the parts, and a later play downloads only what is missing.
- Each read that waits costs a `getFile` and, when it moves, a `downloadFile`: a few requests per second at most.
  Memory: Media3's buffer (its default, 50 s of media at most), no copy of the file.
- Videos that do not support streaming (an index at the end) still play once downloaded, as Telegram's apps do.
