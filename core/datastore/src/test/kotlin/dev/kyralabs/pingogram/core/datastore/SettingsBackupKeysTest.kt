package dev.kyralabs.pingogram.core.datastore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every key of the settings file is either backed up or named as left out (`docs/adr/0039-local-backup-and-restore.md`,
 * Consequences), read from the sources of the settings repositories: the name in the first string after each
 * `…PreferencesKey(`, up to a `$` where it is made from a chat or a network.
 */
class SettingsBackupKeysTest {
    private val sources =
        File("src/main/kotlin/dev/kyralabs/pingogram/core/datastore")
            .listFiles { file -> file.name.startsWith("DataStore") && file.name != SELF }
            .orEmpty()
            .map { it.readText() }

    private val keys: Set<String> =
        sources
            .flatMap { source ->
                KEY_CALL.findAll(source).mapNotNull { call ->
                    LITERAL
                        .find(source, call.range.last)
                        ?.groupValues
                        ?.get(1)
                        ?.substringBefore('$')
                }
            }.toSet()

    private val listed = BackedUpSettings + LeftOutSettings.keys

    @Test
    fun `the settings file's keys are read from its repositories`() {
        assertTrue(keys.size > MIN_KEYS, "$keys")
        assertTrue("passcode.hash" in keys && "auto_download." in keys, "$keys")
    }

    @Test
    fun `every key of the settings file is backed up or named as left out`() {
        val unlisted = keys.filterNot { key -> listed.any { it.covers(key) } }

        assertEquals(emptyList<String>(), unlisted, "Add each to BackedUpSettings or LeftOutSettings")
    }

    @Test
    fun `no key is both backed up and left out, and each listed key is still in the settings file`() {
        assertEquals(emptySet<String>(), BackedUpSettings intersect LeftOutSettings.keys)
        val stale = listed.filterNot { name -> keys.any { name.covers(it) || it.startsWith(name) } }
        assertEquals(emptyList<String>(), stale)
    }

    private companion object {
        const val SELF = "DataStoreSettingsBackupRepository.kt"
        const val MIN_KEYS = 50
        val KEY_CALL = Regex("""[a-zA-Z]+PreferencesKey\(""")
        val LITERAL = Regex(""""([a-z_][a-z0-9_.]*(?:\$[^"]*)?)"""")
    }
}
