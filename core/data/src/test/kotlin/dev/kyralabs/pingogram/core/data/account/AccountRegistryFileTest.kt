package dev.kyralabs.pingogram.core.data.account

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AccountRegistryFileTest {
    @TempDir
    lateinit var dir: File

    private val file get() = dir.resolve(AccountRegistryFile.FILE_NAME)
    private val registryFile get() = AccountRegistryFile { file }

    @Test
    fun `a registry reads back as it was written`() {
        val registry = AccountRegistry(slots = listOf(0, 2, 1), active = 2)

        registryFile.write(registry)

        assertEquals(registry, registryFile.read())
    }

    @Test
    fun `without a file the first account is the only one`() {
        assertEquals(AccountRegistry.First, registryFile.read())
    }

    @Test
    fun `a file in another format or with an active slot not on the device reads as the first account`() {
        listOf("2\nactive=0\nslots=0\n", "1\nactive=3\nslots=0,1\n", "1\nslots=\n", "garbage").forEach { text ->
            file.writeText(text)

            assertEquals(AccountRegistry.First, registryFile.read(), text)
        }
    }
}
