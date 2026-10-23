package dev.kyralabs.pingogram.core.data.diagnostics

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.AppInfo
import dev.kyralabs.pingogram.core.domain.model.CrashReport
import dev.kyralabs.pingogram.core.domain.model.DiagnosticReport
import dev.kyralabs.pingogram.core.domain.repository.DiagnosticsRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibConfig
import dev.kyralabs.pingogram.core.tdlib.TdlibDirectories
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * The feedback report from what is on the device (`docs/TESTING_GUIDE.md`): [AppInfo], the device, the crash
 * [CrashRecorder] kept, and the end of TDLib's log (verbosity 1, `docs/TDLIB.md`), every line redacted. It reads only;
 * it sends nothing.
 */
class AndroidDiagnosticsRepository internal constructor(
    private val crashFile: File,
    private val tdlibLog: File,
    private val app: AppInfo,
    private val device: String,
    private val now: () -> Long,
    private val io: CoroutineDispatcher,
) : DiagnosticsRepository {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        app: AppInfo,
        tdlib: TdlibConfig,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        crashFile = CrashRecorder.crashFile(context),
        tdlibLog = TdlibDirectories.of(context.filesDir, tdlib.useTestDc).logFile,
        app = app,
        device = currentDevice(),
        now = System::currentTimeMillis,
        io = io,
    )

    override suspend fun report(): DiagnosticReport =
        withContext(io) {
            DiagnosticReport(
                generatedAtMillis = now(),
                app =
                    "Pingogram ${app.versionName} (${app.versionCode}), ${app.flavor}, " +
                        if (app.isDebug) "debug" else "release",
                device = device,
                crash = readCrash(),
                log = readLog().toImmutableList(),
            )
        }

    override suspend fun clearCrash() {
        withContext(io) { crashFile.delete() }
    }

    private fun readCrash(): CrashReport? {
        val lines = readOrNull(crashFile)?.lines().orEmpty()
        val time = lines.firstOrNull()?.removePrefix(CrashRecorder.TIME)?.toLongOrNull()
        val thread = lines.getOrNull(1)?.removePrefix(CrashRecorder.THREAD)
        return if (time == null || thread == null) {
            null
        } else {
            CrashReport(time, thread, lines.drop(2).joinToString("\n").trimEnd())
        }
    }

    /** The last [LOG_LINES] lines of the last [LOG_BYTES] of TDLib's log, redacted and shortened. */
    private fun readLog(): List<String> {
        if (!tdlibLog.isFile) return emptyList()
        val lines =
            try {
                tail(tdlibLog)
            } catch (e: IOException) {
                listOf("(TDLib's log could not be read: ${e.javaClass.simpleName})")
            }
        return lines
            .filter { it.isNotBlank() }
            .takeLast(LOG_LINES)
            .map { Redaction.redact(it).take(MAX_LINE) }
    }

    /** The lines of the last [LOG_BYTES] of [file]; a line the start cuts is dropped. */
    private fun tail(file: File): List<String> =
        RandomAccessFile(file, "r").use { log ->
            val start = (log.length() - LOG_BYTES).coerceAtLeast(0)
            log.seek(start)
            val bytes = ByteArray((log.length() - start).toInt())
            log.readFully(bytes)
            // The file may also end in a half-written line, which is kept.
            String(bytes).lines().let { if (start > 0) it.drop(1) else it }
        }

    private fun readOrNull(file: File): String? =
        try {
            file.takeIf(File::isFile)?.readText()
        } catch (_: IOException) {
            null
        }

    private companion object {
        const val LOG_BYTES = 64L * 1024
        const val LOG_LINES = 200
        const val MAX_LINE = 300

        fun currentDevice(): String =
            "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                Build.SUPPORTED_ABIS.joinToString()
    }
}
