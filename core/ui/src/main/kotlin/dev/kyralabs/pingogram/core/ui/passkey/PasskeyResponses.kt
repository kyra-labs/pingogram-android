package dev.kyralabs.pingogram.core.ui.passkey

import dev.kyralabs.pingogram.core.domain.model.PasskeyAssertion
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

// The WebAuthn JSON that requests and password managers' answers carry, read for TDLib (`docs/adr/0037-passkeys.md`).

/** The user of creation request [requestJson]; an empty name where the JSON has none. */
internal fun creationUser(requestJson: String): CreationUser =
    try {
        val user = JSONObject(requestJson).optJSONObject("user")
        CreationUser(
            name = user?.optString("name").orEmpty(),
            displayName = user?.optString("displayName")?.takeIf { it.isNotEmpty() },
        )
    } catch (_: JSONException) {
        CreationUser(name = "", displayName = null)
    }

/**
 * A password manager's answer to a creation request ([responseJson], WebAuthn's `RegistrationResponseJSON`) as TDLib's
 * `addLoginPasskey` takes it: the client data decoded into its JSON text, the attestation object into its bytes. Null
 * where the answer lacks either or is no JSON.
 */
internal fun registrationOf(responseJson: String): PasskeyRegistration? =
    try {
        val response = JSONObject(responseJson).getJSONObject("response")
        PasskeyRegistration(
            clientData = String(base64Url(response.getString("clientDataJSON")), Charsets.UTF_8),
            attestationObject = base64Url(response.getString("attestationObject")),
        )
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * A password manager's answer to a sign-in request ([responseJson], WebAuthn's `AuthenticationResponseJSON`) as TDLib's
 * `checkAuthenticationPasskey` takes it: the credential's id as written (base64url), the client data decoded into its
 * JSON text, the authenticator data, signature and user handle (empty where it has none) into their bytes. Null where
 * the answer lacks one of the others or is no JSON.
 */
internal fun assertionOf(responseJson: String): PasskeyAssertion? =
    try {
        val json = JSONObject(responseJson)
        val response = json.getJSONObject("response")
        PasskeyAssertion(
            credentialId = json.getString("id"),
            clientData = String(base64Url(response.getString("clientDataJSON")), Charsets.UTF_8),
            authenticatorData = base64Url(response.getString("authenticatorData")),
            signature = base64Url(response.getString("signature")),
            userHandle = response.optString("userHandle").let { if (it.isEmpty()) ByteArray(0) else base64Url(it) },
        )
    } catch (_: JSONException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/** [text] decoded from base64url, padded or not, as WebAuthn's JSON writes binary values. */
internal fun base64Url(text: String): ByteArray = Base64.getUrlDecoder().decode(text.trimEnd('='))
