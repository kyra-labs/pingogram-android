package dev.kyralabs.pingogram.core.tdlib

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Build-time TDLib settings, provided by `:app` from its `BuildConfig`. [apiId] and [apiHash] come from the
 * git-ignored `secrets.properties` (CLAUDE.md rule 1), so [toString] leaves them out.
 */
data class TdlibConfig(
    val apiId: Int,
    val apiHash: String,
    /** `BuildConfig.VERSION_NAME`; TDLib reports the client as "Pingogram <it>" (CLAUDE.md rule 3). */
    val appVersionName: String,
    val isDebug: Boolean,
    val useTestDc: Boolean,
) {
    override fun toString() =
        "TdlibConfig(apiId=…, apiHash=…, appVersionName=$appVersionName, isDebug=$isDebug, useTestDc=$useTestDc)"
}

/** Builds the `SetTdlibParameters` that [TdlibClient] sends when TDLib asks for them. */
interface TdlibParametersProvider {
    suspend fun parameters(): TdApi.SetTdlibParameters

    /**
     * TDLib rejected the database key (error 401): forget the key and delete the local database, so the next
     * [parameters] start a new, logged-out session.
     */
    suspend fun discardDatabase()
}

/**
 * Where TDLib keeps its data: under `filesDir`, which is never backed up (`android:allowBackup="false"`,
 * `REQUIREMENTS.md` §6.2). The test DC gets its own directories, as a database belongs to one environment.
 */
class TdlibDirectories(
    val root: File,
) {
    val database: File get() = root.resolve("db")
    val files: File get() = root.resolve("files")

    /** TDLib's own log (`TdlibLogSettings`): verbosity 1 in release, for the feedback report; 3 in debug builds. */
    val logFile: File get() = root.resolve("logs/tdlib.log")

    companion object {
        fun of(
            filesDir: File,
            useTestDc: Boolean,
        ) = TdlibDirectories(filesDir.resolve(if (useTestDc) "tdlib-test" else "tdlib"))
    }
}

internal data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
) {
    companion object {
        fun current() = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE)
    }
}

/** The values from the parameters table in `docs/TDLIB.md`. */
internal fun tdlibParameters(
    config: TdlibConfig,
    directories: TdlibDirectories,
    databaseKey: ByteArray,
    device: DeviceInfo,
    languageTag: String,
): TdApi.SetTdlibParameters =
    TdApi.SetTdlibParameters().apply {
        useTestDc = config.useTestDc
        databaseDirectory = directories.database.path
        filesDirectory = directories.files.path
        databaseEncryptionKey = databaseKey
        useFileDatabase = true
        useChatInfoDatabase = true
        useMessageDatabase = true
        useSecretChats = true
        apiId = config.apiId
        apiHash = config.apiHash
        systemLanguageCode = languageTag
        deviceModel = "${device.manufacturer} ${device.model}"
        systemVersion = "Android ${device.androidVersion}"
        // Honest client identification (Telegram API terms, CLAUDE.md rule 3).
        applicationVersion = "Pingogram ${config.appVersionName}"
    }

class AndroidTdlibParametersProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val config: TdlibConfig,
        private val databaseKeys: DatabaseKeyProvider,
        @Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : TdlibParametersProvider {
        // Lazy: Context.getFilesDir() touches the disk, and this class is created on the main thread.
        private val directories by lazy { TdlibDirectories.of(context.filesDir, config.useTestDc) }

        override suspend fun parameters(): TdApi.SetTdlibParameters =
            withContext(io) {
                val key = databaseKeys.databaseKey()
                // A database encrypted with a lost key can never be opened again: start over, logged out.
                if (key.isNew) deleteDatabase()
                tdlibParameters(
                    config,
                    directories,
                    key.bytes,
                    DeviceInfo.current(),
                    Locale.getDefault().toLanguageTag(),
                )
            }

        override suspend fun discardDatabase() =
            withContext(io) {
                databaseKeys.discard()
                deleteDatabase()
            }

        private fun deleteDatabase() {
            directories.database.deleteRecursively()
            directories.files.deleteRecursively()
        }
    }
