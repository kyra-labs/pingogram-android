package dev.kyralabs.pingogram.core.ui.passkey

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration

/**
 * The user's password managers, as Android's Credential Manager reaches them (`docs/adr/0037-passkeys.md`): they make
 * and keep the passkey; Pingogram hands them Telegram's request and takes their answer back to Telegram. Each call
 * shows Android's own sheet.
 */
interface PasskeyCredentials {
    /** Asks for a new passkey for Telegram's creation request [requestJson] (WebAuthn's JSON, as TDLib gives it). */
    suspend fun create(requestJson: String): PasskeyResult<PasskeyRegistration>
}

/** What a password manager answered. */
sealed interface PasskeyResult<out T> {
    data class Done<T>(
        val value: T,
    ) : PasskeyResult<T>

    /** The user closed Android's sheet. */
    data object Cancelled : PasskeyResult<Nothing>

    /**
     * No password manager could do it: none is set up, or none may make Telegram's passkeys for Pingogram (Telegram's
     * Digital Asset Links do not name it yet, `docs/adr/0037-passkeys.md`).
     */
    data object Unavailable : PasskeyResult<Nothing>

    /** Anything else went wrong; nothing was made. */
    data object Failed : PasskeyResult<Nothing>
}

/** Whether this device offers passkeys to Pingogram: Android 14 and later (`docs/adr/0037-passkeys.md`). */
val arePasskeysSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/** The password managers for this screen's activity, or null before Android 14, where Pingogram offers no passkeys. */
@Composable
fun rememberPasskeyCredentials(): PasskeyCredentials? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            PlatformPasskeyCredentials(activity)
        } else {
            null
        }
    }
}
