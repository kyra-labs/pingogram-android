package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport

/**
 * The feedback report's data (`docs/TESTING_GUIDE.md`): all of it read on the device, none of it sent anywhere; the
 * user shares the report themselves.
 */
interface DiagnosticsRepository {
    /** The report as of now. */
    suspend fun report(): DiagnosticReport

    /** Forgets the kept crash. */
    suspend fun clearCrash()
}
