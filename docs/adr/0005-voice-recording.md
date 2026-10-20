# 0005 — Voice messages: the platform's MediaRecorder, Ogg Opus on Android 10+, AAC before

Status: accepted · Date: 2026-10-20 · Milestone: M3 (`REQUIREMENTS.md` FR-C5 "voice record (hold)")

## Context

The composer records voice messages: hold the microphone to record, slide to cancel or to lock, release to send.
TDLib sends them with `sendMessage(inputMessageVoiceNote(inputVoiceNote(file, duration, waveform), caption,
self_destruct_type))` (td_api.tl of the pinned TDLib, 1.8.68): any local file, its length in seconds and a 5-bit
waveform. TDLib takes the file's MIME type from its extension.

What the recording has to be:

- **A format Telegram's apps play as a voice note.** Telegram's own voice notes are Ogg Opus (`audio/ogg`). The Bot API
  documents what the servers take as a voice note: "audio must be in an .OGG file encoded with OPUS, or in .MP3
  format, or in .M4A format" (`sendVoice`, https://core.telegram.org/bots/api#sendvoice). Pingogram's own player
  (Media3 ExoPlayer) plays both.
- **Every supported Android version**: `minSdk` 26.
- **Small and fast**: no native library if the platform can do it (`docs/PERFORMANCE.md` §8, APK budget), nothing
  blocking the main thread, a live level for the waveform.
- **Google-free** for the `foss` flavor, no network.

Options considered:

1. **`MediaRecorder` with `OutputFormat.OGG` + `AudioEncoder.OPUS`.** Telegram's format byte for byte, a few lines,
   no library, hardware-independent (AOSP's software Opus encoder, C2 `c2.android.opus.encoder`). But both constants
   are API 29: Android 8–9 (API 26–28) have no Opus encoder and no Ogg writer at all.
2. **`AudioRecord` + `MediaCodec` Opus + our own Ogg muxer.** More control (PCM for an exact waveform), but the Opus
   encoder is the same API 29 codec, and `MediaMuxer` writes Ogg only from API 29 as well. It solves nothing for
   26–28 and costs an Ogg page writer and a PCM pipeline of our own.
3. **libopus + libogg through JNI** (what Telegram's own Android app does, and what Telegram X builds). Ogg Opus on
   every version, but a native library per ABI to build in CI and ship (about 300–400 KB per ABI), a JNI layer,
   16 KB page alignment to verify, and BSD license entries for both. All of that only for Android 8–9, about 5% of
   devices and falling.
4. **AAC in an MP4 container (`.m4a`) with `MediaRecorder`** on every version. Works everywhere, but needs about twice
   Opus's bit rate for the same speech, and on Android 10+ there is no reason to send Telegram anything but its own
   format.

## Decision

The platform's `MediaRecorder`, no library (`feature:chat`, `voice/MediaVoiceRecorder.kt`):

- **Android 10 and later, where `MediaCodecList` has an Opus encoder** (always on AOSP): Ogg Opus, 48 kHz, mono,
  32 kbit/s (about 240 KB a minute) into `voice.ogg`.
- **Android 8–9, or a device without the encoder**: AAC-LC, 44.1 kHz, mono, 64 kbit/s into `voice.m4a`, which Telegram
  takes as a voice note (option 4 as the fallback of option 1). The format is picked before the file is made, so the
  file's extension, and with it the type TDLib sends, always matches its content.
- **Level and waveform**: `MediaRecorder.getMaxAmplitude()` read 20 times a second while recording (a UI ticker, not
  polling; nothing runs while paused or idle), mapped to decibels (−50 dB to full scale) for the live meter; the
  levels of the whole recording are cut into 100 stretches, each its loudest level, scaled to the loudest stretch and
  packed into Telegram's 5-bit waveform (63 bytes), the format the bubbles already decode.
- **Threading**: the recorder lives on a thread of its own, like the audio player: `prepare`, `start` and above all
  `stop` wait for the audio input and the encoder.
- **Audio focus**: `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` while recording, so music and other apps' speech pause rather
  than end up in the message.
- **Permission**: `RECORD_AUDIO` (approved by the owner), asked for at the first hold after a snackbar says why, with
  `android.hardware.microphone` declared as not required, so devices without one still install the app.

## Consequences

- No new dependency, no native code, no APK growth beyond the feature's own code.
- Android 8–9 send AAC voice notes. Telegram takes them as voice notes, but that every other client plays them is
  not verified here: one that plays only Ogg Opus would not. If that ever matters, option 3 can replace the fallback
  behind the same `VoiceRecorder` interface.
- The waveform comes from the recorder's peak amplitude at 20 Hz rather than from PCM: close enough for a 100-level
  overview, and the live meter and the sent waveform agree.
- A recording does not survive process death (its file is swept from the upload cache later), and stops (pauses) when
  the screen stops: Android gives no microphone to apps in the background unless they run a foreground service of type
  `microphone`, which voice messages do not justify.
- Video messages (round videos) are not recorded: they need the camera, which the app does not ask for.
