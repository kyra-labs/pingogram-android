package dev.kyralabs.pingogram.feature.scanner

import android.os.Build
import android.os.Trace

/**
 * The trace section of the QR code scanner (`docs/PERFORMANCE.md` §9, "Measure first"), which
 * `tools/perfetto/measure-first.sh` records: [DECODE], one camera frame or picked image searched for a QR code
 * ([QrDecoder]). It costs a check of a flag while no trace records the app (Android 10+, where apps can read that
 * flag).
 */
internal object ScannerTrace {
    /** One image searched for a QR code, as it is and inverted. */
    const val DECODE = "qrDecode"

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
