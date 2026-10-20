package dev.kyralabs.pingogram.feature.settings.ui.passcode

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.feature.settings.R
import javax.inject.Inject

// Unlocking with a fingerprint or the face, through the platform's BiometricPrompt (Android 10 and later), without
// androidx.biometric: its stable release needs a FragmentActivity and brings AppCompat
// (docs/adr/0005-passcode-lock.md).

/** Whether this device can unlock the app with biometrics right now: strong ones set up, Android 10 or later. */
fun interface BiometricAvailability {
    fun canAuthenticate(): Boolean
}

/**
 * [BiometricAvailability] from the platform's [BiometricManager]: strong (class 3) biometrics enrolled on Android 11
 * and later; on Android 10, which cannot tell their class, the biometrics it lets apps use. Before Android 10, never.
 */
internal class PlatformBiometricAvailability
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : BiometricAvailability {
        override fun canAuthenticate(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && canAuthenticateSinceQ()

        // USE_BIOMETRIC is declared in :app's manifest (pinned by AppManifestTest), which a library's lint cannot see.
        @SuppressLint("MissingPermission")
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun canAuthenticateSinceQ(): Boolean {
            val manager = context.getSystemService(BiometricManager::class.java) ?: return false
            val result =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    manager.canAuthenticate(BIOMETRIC_STRONG)
                } else {
                    canAuthenticateQ(manager)
                }
            return result == BiometricManager.BIOMETRIC_SUCCESS
        }

        // Android 10's only check; Android 11 deprecated it for the one with authenticator classes used above.
        // USE_BIOMETRIC is declared in :app's manifest, as above.
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun canAuthenticateQ(manager: BiometricManager): Int = manager.canAuthenticate()
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface BiometricModule {
    @Binds
    fun biometricAvailability(availability: PlatformBiometricAvailability): BiometricAvailability
}

/** Shows the system's biometric prompt ([show]); see [rememberBiometricPrompt]. */
@Stable
internal fun interface BiometricPromptLauncher {
    fun show()
}

/**
 * The system's biometric prompt for unlocking ("Unlock Pingogram", with "Use passcode" to cancel): strong biometrics
 * only (class 3, Android 11 and later), never the device's screen lock, which would let anyone who knows the phone's
 * PIN past the app's passcode. [onSucceeded] runs once the system recognized the user. A failed attempt is the
 * system's to show, and cancelling or an error leaves the passcode entry. A prompt still showing is cancelled when this
 * leaves the composition. Does nothing before Android 10.
 */
@Composable
internal fun rememberBiometricPrompt(onSucceeded: () -> Unit): BiometricPromptLauncher {
    val context = LocalContext.current
    val title = stringResource(R.string.lock_biometric_prompt_title)
    val cancel = stringResource(R.string.lock_biometric_prompt_cancel)
    val currentOnSucceeded by rememberUpdatedState(onSucceeded)
    val signals = remember { mutableListOf<CancellationSignal>() }
    DisposableEffect(signals) {
        onDispose {
            signals.forEach(CancellationSignal::cancel)
            signals.clear()
        }
    }
    return remember(context, title, cancel) {
        BiometricPromptLauncher {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                signals.forEach(CancellationSignal::cancel)
                signals.clear()
                val signal = CancellationSignal().also(signals::add)
                showPrompt(context, title, cancel, signal) { currentOnSucceeded() }
            }
        }
    }
}

// USE_BIOMETRIC is declared in :app's manifest (pinned by AppManifestTest), which a library's lint cannot see.
@SuppressLint("MissingPermission")
@RequiresApi(Build.VERSION_CODES.Q)
private fun showPrompt(
    context: Context,
    title: String,
    cancel: String,
    signal: CancellationSignal,
    onSucceeded: () -> Unit,
) {
    val executor = context.mainExecutor
    val prompt =
        BiometricPrompt
            .Builder(context)
            .setTitle(title)
            .setNegativeButton(cancel, executor) { _, _ -> }
            // A face recognized needs no extra tap: the app is only unlocked, nothing is paid or signed.
            .setConfirmationRequired(false)
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setAllowedAuthenticators(BIOMETRIC_STRONG) }
            .build()
    prompt.authenticate(
        signal,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                onSucceeded()
            }
        },
    )
}
