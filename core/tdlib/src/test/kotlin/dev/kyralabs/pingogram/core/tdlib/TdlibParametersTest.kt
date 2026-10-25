package dev.kyralabs.pingogram.core.tdlib

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class TdlibParametersTest {
    private val filesDir = File("/data/user/0/dev.kyralabs.pingogram/files")
    private val config =
        TdlibConfig(
            apiId = 12345,
            apiHash = "0123456789abcdef0123456789abcdef",
            appVersionName = "0.1.0",
            isDebug = false,
        )
    private val key = ByteArray(32) { it.toByte() }
    private val device = DeviceInfo(manufacturer = "Google", model = "Pixel 9", androidVersion = "16")

    private fun parameters(servers: TdlibServers = TdlibServers.Production) =
        tdlibParameters(
            config,
            servers,
            TdlibDirectories.of(filesDir, useTestDc = servers == TdlibServers.Test),
            key,
            device,
            languageTag = "en-GB",
        )

    @Test
    fun `database and files live under filesDir tdlib, on the production servers`() {
        val parameters = parameters()

        assertEquals(
            listOf(false, "$filesDir/tdlib/db", "$filesDir/tdlib/files"),
            listOf(parameters.useTestDc, parameters.databaseDirectory, parameters.filesDirectory),
        )
    }

    @Test
    fun `the test servers get their own directories`() {
        val parameters = parameters(TdlibServers.Test)

        assertEquals(
            listOf(true, "$filesDir/tdlib-test/db", "$filesDir/tdlib-test/files"),
            listOf(parameters.useTestDc, parameters.databaseDirectory, parameters.filesDirectory),
        )
    }

    @Test
    fun `the client identifies itself honestly as Pingogram`() {
        assertEquals("Pingogram 0.1.0", parameters().applicationVersion)
    }

    @Test
    fun `device model and system version describe the device`() {
        val parameters = parameters()

        assertEquals(listOf("Google Pixel 9", "Android 16"), listOf(parameters.deviceModel, parameters.systemVersion))
    }

    @Test
    fun `all databases and secret chats are enabled`() {
        val parameters = parameters()

        assertTrue(
            parameters.useFileDatabase &&
                parameters.useChatInfoDatabase &&
                parameters.useMessageDatabase &&
                parameters.useSecretChats,
        )
    }

    @Test
    fun `API credentials, language and database key are passed through`() {
        val parameters = parameters()

        assertEquals(
            listOf<Any>(12345, "0123456789abcdef0123456789abcdef", "en-GB"),
            listOf(parameters.apiId, parameters.apiHash, parameters.systemLanguageCode),
        )
        assertArrayEquals(key, parameters.databaseEncryptionKey)
    }

    @Test
    fun `TdlibConfig never prints the API credentials`() {
        val printed = config.toString()

        assertFalse("12345" in printed || config.apiHash in printed, printed)
    }

    @Test
    fun `release builds log errors only, to a small file for the feedback report`() {
        val settings = TdlibLogSettings.of(isDebug = false, TdlibDirectories(filesDir.resolve("tdlib")))

        assertEquals(TdlibLogSettings(1, filesDir.resolve("tdlib/logs/tdlib.log"), 1L * 1024 * 1024), settings)
    }

    @Test
    fun `debug builds log at verbosity 3 to a file under the TDLib directory`() {
        val settings = TdlibLogSettings.of(isDebug = true, TdlibDirectories(filesDir.resolve("tdlib")))

        assertEquals(TdlibLogSettings(3, filesDir.resolve("tdlib/logs/tdlib.log"), 10L * 1024 * 1024), settings)
    }
}
