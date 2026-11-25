package dev.kyralabs.pingogram.core.data.transcription

import android.os.Build
import android.os.Trace

/**
 * The trace section of transcription on the phone (`docs/PERFORMANCE.md` §9, "Measure first"), which
 * `tools/perfetto/measure-first.sh` records: [RESAMPLE], one decoded chunk of a voice or video message turned into the
 * speech recognizer's 16 kHz mono ([SpeechPcm]). It costs a check of a flag while no trace records the app (Android
 * 10+, where apps can read that flag).
 */
internal object TranscriptionTrace {
    /** One decoded chunk mixed to one channel and resampled. */
    const val RESAMPLE = "speechPcm"

    inline fun <T> section(
        name: String,
        block: () -> T,
    ): T {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !Trace.isEnabled()) return block()
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}
