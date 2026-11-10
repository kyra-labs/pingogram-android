package dev.kyralabs.pingogram.core.ui.passkey

import android.credentials.CreateCredentialException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

/** How a password manager's WebAuthn JSON becomes what TDLib takes, and its errors what the page says. */
@RunWith(RobolectricTestRunner::class)
class PasskeyResponsesTest {
    @Test
    fun `decodes the client data into its JSON text and the attestation object into its bytes`() {
        val clientData = """{"type":"webauthn.create","challenge":"abc","origin":"android:apk-key-hash:x"}"""
        val attestation = byteArrayOf(0xA3.toByte(), 0x63, 0x66, 0x6D, 0x74, 0xFF.toByte())
        val response =
            """
            {"id":"cred","rawId":"cred","type":"public-key","response":{
              "clientDataJSON":"${unpadded(clientData.toByteArray())}",
              "attestationObject":"${unpadded(attestation)}"}}
            """.trimIndent()

        val registration = registrationOf(response)!!

        assertEquals(clientData, registration.clientData)
        assertArrayEquals(attestation, registration.attestationObject)
    }

    @Test
    fun `reads padded base64url too`() {
        assertArrayEquals(byteArrayOf(1, 2), base64Url("AQI="))
        assertArrayEquals(byteArrayOf(1, 2), base64Url("AQI"))
    }

    @Test
    fun `an answer without its response, or no JSON, is no registration`() {
        assertNull(registrationOf("""{"id":"cred"}"""))
        assertNull(registrationOf("not json"))
        assertNull(registrationOf("""{"response":{"clientDataJSON":"!!","attestationObject":"AQI"}}"""))
    }

    @Test
    fun `reads the account a creation request is for`() {
        val user =
            creationUser("""{"rp":{"id":"telegram.org"},"user":{"id":"AQI","name":"+44 7700","displayName":"Ada"}}""")

        assertEquals("+44 7700", user.name)
        assertEquals("Ada", user.displayName)
        assertEquals("", creationUser("[").name)
    }

    @Test
    fun `words Android's errors as the page tells them`() {
        assertEquals(PasskeyResult.Cancelled, creationFailure(CreateCredentialException.TYPE_USER_CANCELED))
        assertEquals(PasskeyResult.Unavailable, creationFailure(CreateCredentialException.TYPE_NO_CREATE_OPTIONS))
        assertEquals(
            PasskeyResult.Unavailable,
            creationFailure(
                "androidx.credentials.TYPE_CREATE_PUBLIC_KEY_CREDENTIAL_DOM_EXCEPTION/" +
                    "androidx.credentials.TYPE_SECURITY_ERROR",
            ),
        )
        assertEquals(PasskeyResult.Failed, creationFailure(CreateCredentialException.TYPE_UNKNOWN))
    }

    private fun unpadded(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
