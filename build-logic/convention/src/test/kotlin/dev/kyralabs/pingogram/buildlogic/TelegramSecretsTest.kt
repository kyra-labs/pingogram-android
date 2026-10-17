package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TelegramSecretsTest {
    private val hash = "0123456789abcdef0123456789abcdef"

    private fun secretsFile(
        id: String,
        hash: String,
    ) = "TELEGRAM_API_ID=$id\nTELEGRAM_API_HASH=$hash\n"

    private fun errorFor(contents: String?): String =
        assertThrows<IllegalStateException> { TelegramSecrets.parse(contents) }.message.orEmpty()

    @Test
    fun `valid file provides the api id and hash`() {
        val secrets = TelegramSecrets.parse(secretsFile(id = "12345", hash = hash))

        assertEquals(12345 to hash, secrets.apiId to secrets.apiHash)
    }

    @Test
    fun `placeholders written by CI for fork pull requests are accepted`() {
        val secrets = TelegramSecrets.parse(secretsFile(id = "0", hash = "0".repeat(32)))

        assertEquals(0 to "0".repeat(32), secrets.apiId to secrets.apiHash)
    }

    @Test
    fun `whitespace around values is ignored`() {
        val secrets = TelegramSecrets.parse("TELEGRAM_API_ID = 12345 \nTELEGRAM_API_HASH = $hash \t\n")

        assertEquals(12345 to hash, secrets.apiId to secrets.apiHash)
    }

    @Test
    fun `missing file points to the example and the setup doc`() {
        val message = errorFor(null)

        assertTrue("secrets.properties.example" in message && "docs/OWNER_SETUP.md" in message, message)
    }

    @Test
    fun `missing key is named in the error`() {
        val message = errorFor("TELEGRAM_API_HASH=$hash")

        assertTrue("TELEGRAM_API_ID is missing" in message, message)
    }

    @Test
    fun `empty value counts as missing`() {
        val message = errorFor(secretsFile(id = "12345", hash = ""))

        assertTrue("TELEGRAM_API_HASH is missing" in message, message)
    }

    @Test
    fun `api id that is not a non-negative integer is rejected`() {
        listOf("-1", "+1", "12a", "1.5", "0x10", "2147483648").forEach { id ->
            val message = errorFor(secretsFile(id = id, hash = hash))

            assertTrue("TELEGRAM_API_ID must be a non-negative integer" in message, id)
        }
    }

    @Test
    fun `api hash that is not 32 hex characters is rejected`() {
        listOf(hash.dropLast(1), hash + "0", hash.replaceFirst('a', 'g')).forEach { badHash ->
            val message = errorFor(secretsFile(id = "12345", hash = badHash))

            assertTrue("TELEGRAM_API_HASH must be exactly 32 hexadecimal characters" in message, badHash)
        }
    }

    @Test
    fun `every problem is reported at once`() {
        val message = errorFor("TELEGRAM_API_ID=abc")

        assertTrue("TELEGRAM_API_ID must be" in message && "TELEGRAM_API_HASH is missing" in message, message)
    }

    @Test
    fun `errors never contain the configured values`() {
        val message = errorFor(secretsFile(id = "98765x", hash = "my-private-hash"))

        assertFalse("98765x" in message || "my-private-hash" in message, "the error must not echo values")
    }
}
