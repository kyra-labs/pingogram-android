package dev.kyralabs.pingogram.core.datastore

import android.os.Build
import android.os.Trace

/**
 * The trace section of the passcode lock (`docs/PERFORMANCE.md` §9, "Measure first"), which
 * `tools/perfetto/measure-first.sh` records: [HASH], one PBKDF2 hash ([PasscodeHasher]), when a passcode is set,
 * changed or entered. It costs a check of a flag while no trace records the app (Android 10+, where apps can read that
 * flag).
 */
internal object PasscodeTrace {
    /** One passcode hashed, to keep it or to compare it with the one kept. */
    const val HASH = "passcodeHash"

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
