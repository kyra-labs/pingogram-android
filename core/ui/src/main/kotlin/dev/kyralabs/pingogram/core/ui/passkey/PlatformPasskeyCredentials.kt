package dev.kyralabs.pingogram.core.ui.passkey

import android.app.Activity
import android.credentials.CreateCredentialException
import android.credentials.CreateCredentialRequest
import android.credentials.CreateCredentialResponse
import android.credentials.CredentialManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [PasskeyCredentials] on Android's own Credential Manager (Android 14, `docs/adr/0037-passkeys.md`), with no library:
 * the requests carry the keys password managers read from Jetpack's `androidx.credentials` (its 1.6.0's constants), so
 * they take them as they take Jetpack's.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class PlatformPasskeyCredentials(
    private val activity: Activity,
) : PasskeyCredentials {
    override suspend fun create(requestJson: String): PasskeyResult<PasskeyRegistration> {
        val manager =
            activity.getSystemService(CredentialManager::class.java) ?: return PasskeyResult.Unavailable
        val request =
            CreateCredentialRequest
                .Builder(
                    TYPE_PUBLIC_KEY_CREDENTIAL,
                    creationData(requestJson, withDisplayInfo = true),
                    creationData(requestJson, withDisplayInfo = false),
                ).setIsSystemProviderRequired(false)
                .setAlwaysSendAppInfoToProvider(true)
                .build()
        return suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            manager.createCredential(
                activity,
                request,
                cancellation,
                activity.mainExecutor,
                object : OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> {
                    override fun onResult(result: CreateCredentialResponse) {
                        val registration =
                            result.data.getString(BUNDLE_KEY_REGISTRATION_RESPONSE_JSON)?.let(::registrationOf)
                        continuation.resume(
                            registration?.let { PasskeyResult.Done(it) } ?: PasskeyResult.Failed,
                        )
                    }

                    override fun onError(error: CreateCredentialException) {
                        continuation.resume(creationFailure(error.type))
                    }
                },
            )
        }
    }
}

/**
 * The creation request as Jetpack's `CreatePublicKeyCredentialRequest` makes it: its subtype and [requestJson], the
 * flags it always sets, and, in the credential data, the account the passkey is for as Android's sheet shows it.
 */
private fun creationData(
    requestJson: String,
    withDisplayInfo: Boolean,
): Bundle =
    Bundle().apply {
        putString(BUNDLE_KEY_SUBTYPE, BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST)
        putString(BUNDLE_KEY_REQUEST_JSON, requestJson)
        putBoolean(BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED, false)
        if (withDisplayInfo) {
            putBoolean(BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS, false)
            val user = creationUser(requestJson)
            putBundle(
                BUNDLE_KEY_REQUEST_DISPLAY_INFO,
                Bundle().apply {
                    putCharSequence(BUNDLE_KEY_USER_ID, user.name)
                    user.displayName?.let { putCharSequence(BUNDLE_KEY_USER_DISPLAY_NAME, it) }
                },
            )
        }
    }

/** A creation that failed with Android's error [type], as the page words it. */
internal fun creationFailure(type: String): PasskeyResult<Nothing> =
    when {
        type == CreateCredentialException.TYPE_USER_CANCELED -> PasskeyResult.Cancelled

        type == CreateCredentialException.TYPE_NO_CREATE_OPTIONS -> PasskeyResult.Unavailable

        // A password manager refusing a relying party that does not name the app (WebAuthn's SecurityError, as
        // Jetpack's DOM exception type ends).
        type.endsWith(SECURITY_ERROR) -> PasskeyResult.Unavailable

        else -> PasskeyResult.Failed
    }

// Jetpack's keys, as `androidx.credentials` 1.6.0 compiles them (`docs/adr/0037-passkeys.md`).
private const val TYPE_PUBLIC_KEY_CREDENTIAL = "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL"
private const val BUNDLE_KEY_SUBTYPE = "androidx.credentials.BUNDLE_KEY_SUBTYPE"
private const val BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST =
    "androidx.credentials.BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST"
private const val BUNDLE_KEY_REQUEST_JSON = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
private const val BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED = "androidx.credentials.BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED"
private const val BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS =
    "androidx.credentials.BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS"
private const val BUNDLE_KEY_REQUEST_DISPLAY_INFO = "androidx.credentials.BUNDLE_KEY_REQUEST_DISPLAY_INFO"
private const val BUNDLE_KEY_USER_ID = "androidx.credentials.BUNDLE_KEY_USER_ID"
private const val BUNDLE_KEY_USER_DISPLAY_NAME = "androidx.credentials.BUNDLE_KEY_USER_DISPLAY_NAME"
private const val BUNDLE_KEY_REGISTRATION_RESPONSE_JSON = "androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON"
private const val SECURITY_ERROR = "androidx.credentials.TYPE_SECURITY_ERROR"

/** The account a creation request is for: its `user.name`, and its `user.displayName` where it has one. */
internal class CreationUser(
    val name: String,
    val displayName: String?,
)
