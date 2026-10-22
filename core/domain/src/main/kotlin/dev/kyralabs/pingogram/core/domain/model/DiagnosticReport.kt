package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What "Send feedback" prepares on the device for the user to read and share themselves (`docs/TESTING_GUIDE.md`):
 * nothing in it leaves the device by itself (`CLAUDE.md` rule 10). It holds the build, the device, the last crash kept
 * on the device and the end of TDLib's error log; messages, phone numbers, quoted texts and tokens are taken out before
 * it is made ([crash] and [log] are already redacted).
 *
 * @property generatedAtMillis when it was made, wall clock.
 * @property app the build: version, version code, flavor, debug or release.
 * @property device the manufacturer and model, Android's version and API level, and the ABIs.
 * @property crash the last crash, if one was kept, until the user deletes it or a new crash replaces it.
 * @property log the last lines of TDLib's log (verbosity 1: errors), oldest first.
 */
data class DiagnosticReport(
    val generatedAtMillis: Long,
    val app: String,
    val device: String,
    val crash: CrashReport? = null,
    val log: ImmutableList<String> = persistentListOf(),
)

/**
 * The last time the app crashed, as kept on the device.
 *
 * @property timeMillis when, wall clock.
 * @property thread the thread that threw.
 * @property trace the exception and its causes, their classes, redacted messages and stack frames.
 */
data class CrashReport(
    val timeMillis: Long,
    val thread: String,
    val trace: String,
)
