package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReleaseSigningTest {
    private val env =
        mapOf(
            "SIGNING_KEYSTORE_PATH" to "/ci/release.jks",
            "SIGNING_KEYSTORE_PASSWORD" to "env-store-pass",
            "SIGNING_KEY_ALIAS" to "pingogram",
            "SIGNING_KEY_PASSWORD" to "env-key-pass",
        )
    private val keystoreProperties =
        """
        storeFile=/home/me/pingogram-release.jks
        storePassword=file-store-pass
        keyAlias=local
        keyPassword=file-key-pass
        """.trimIndent()

    private val ReleaseSigning.fields get() = listOf(storeFile, storePassword, keyAlias, keyPassword)

    private fun signing(
        env: Map<String, String>,
        keystoreProperties: String?,
    ): ReleaseSigning =
        assertInstanceOf(
            ReleaseSigning.Resolution.Configured::class.java,
            ReleaseSigning.resolve(env::get, keystoreProperties),
        ).signing

    private fun missingMessage(
        env: Map<String, String>,
        keystoreProperties: String?,
    ): String =
        assertInstanceOf(
            ReleaseSigning.Resolution.Missing::class.java,
            ReleaseSigning.resolve(env::get, keystoreProperties),
        ).message

    @Test
    fun `complete environment is used`() {
        val signing = signing(env, keystoreProperties = null)

        assertEquals(listOf("/ci/release.jks", "env-store-pass", "pingogram", "env-key-pass"), signing.fields)
    }

    @Test
    fun `environment takes precedence over keystore properties`() {
        val signing = signing(env, keystoreProperties)

        assertEquals("env-store-pass", signing.storePassword)
    }

    @Test
    fun `keystore properties are used when the environment is incomplete`() {
        val signing = signing(env - "SIGNING_KEY_PASSWORD", keystoreProperties)

        assertEquals(
            listOf("/home/me/pingogram-release.jks", "file-store-pass", "local", "file-key-pass"),
            signing.fields,
        )
    }

    @Test
    fun `empty environment variables count as unset`() {
        val message = missingMessage(env + ("SIGNING_KEY_PASSWORD" to ""), keystoreProperties = null)

        assertTrue("environment (missing SIGNING_KEY_PASSWORD)" in message, message)
    }

    @Test
    fun `nothing configured explains both sources and points to the release doc`() {
        val message = missingMessage(emptyMap(), keystoreProperties = null)

        val expected = listOf("SIGNING_KEYSTORE_PATH", "keystore.properties", "storeFile", "docs/RELEASE.md")
        assertTrue(expected.all { it in message }, message)
    }

    @Test
    fun `nothing configured lists no incomplete source`() {
        val message = missingMessage(emptyMap(), keystoreProperties = null)

        assertFalse("Incomplete" in message, message)
    }

    @Test
    fun `incomplete sources name what is missing`() {
        val message =
            missingMessage(
                env - "SIGNING_KEY_ALIAS",
                keystoreProperties.lines().filterNot { it.startsWith("keyPassword") }.joinToString("\n"),
            )

        val expected = listOf("environment (missing SIGNING_KEY_ALIAS)", "keystore.properties (missing keyPassword)")
        assertTrue(expected.all { it in message }, message)
    }

    @Test
    fun `messages never contain passwords`() {
        val message = missingMessage(env - "SIGNING_KEYSTORE_PATH", "storePassword=file-store-pass")

        assertFalse(
            listOf("env-store-pass", "env-key-pass", "file-store-pass").any { it in message },
            "the message must not echo passwords",
        )
    }
}
