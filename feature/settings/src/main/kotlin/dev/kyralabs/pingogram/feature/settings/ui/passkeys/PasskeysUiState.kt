package dev.kyralabs.pingogram.feature.settings.ui.passkeys

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PasskeyRegistration
import dev.kyralabs.pingogram.core.ui.passkey.PasskeyResult
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the Passkeys page shows (`docs/adr/0037-passkeys.md`): the account's [passkeys], what else protects the
 * account ([protection]), the removal being confirmed ([dialog]), the passkeys being removed ([removingIds]), and
 * whether one is being added ([isAdding]).
 */
@Immutable
data class PasskeysUiState(
    val passkeys: PasskeysState = PasskeysState.Loading,
    val protection: AccountProtection = AccountProtection(),
    val dialog: RemovePasskeyDialog? = null,
    val removingIds: ImmutableSet<String> = persistentSetOf(),
    val isAdding: Boolean = false,
)

@Immutable
sealed interface PasskeysState {
    data object Loading : PasskeysState

    data class Failed(
        val error: DomainError,
    ) : PasskeysState

    data class Loaded(
        val passkeys: ImmutableList<PasskeyItem>,
    ) : PasskeysState
}

/**
 * A passkey as its row shows it: the password manager that keeps it ([name], Telegram's name for it), and when it was
 * added and last used ([details]: "Added Oct 14 · used 9:41 AM").
 */
@Immutable
data class PasskeyItem(
    val id: String,
    val name: String,
    val details: UiText,
)

/**
 * What else protects the account, the board's "Also protecting this account": whether a 2-step verification password
 * is set and how many sessions there are; each null while unknown (not loaded, or it failed).
 */
@Immutable
data class AccountProtection(
    val twoStepVerification: Boolean? = null,
    val sessionCount: Int? = null,
)

/** Removing the passkey [id] kept by [name], waiting for confirmation (`AlertDialog`, destructive). */
@Immutable
data class RemovePasskeyDialog(
    val id: String,
    val name: String,
)

sealed interface PasskeysEvent {
    /** The page shows (again): load everything again, keeping what shows meanwhile. */
    data object Refresh : PasskeysEvent

    data object Retry : PasskeysEvent

    /** "Add passkey": ask Telegram for its request, then [PasskeysEffect.CreatePasskey]. */
    data object Add : PasskeysEvent

    /** What the password manager answered to [PasskeysEffect.CreatePasskey]. */
    data class Created(
        val result: PasskeyResult<PasskeyRegistration>,
    ) : PasskeysEvent

    /** A passkey's "Remove": ask to confirm. */
    data class Remove(
        val passkey: PasskeyItem,
    ) : PasskeysEvent

    data object Confirm : PasskeysEvent

    data object Dismiss : PasskeysEvent
}

sealed interface PasskeysEffect {
    /** Hand Telegram's creation request [requestJson] to the password managers; answer with [PasskeysEvent.Created]. */
    data class CreatePasskey(
        val requestJson: String,
    ) : PasskeysEffect

    /** A passkey kept by [name] was added. */
    data class Added(
        val name: String,
    ) : PasskeysEffect

    /** The passkey kept by [name] was removed. */
    data class Removed(
        val name: String,
    ) : PasskeysEffect

    /** No password manager could make Telegram's passkey for Pingogram. */
    data object Unavailable : PasskeysEffect

    /** The password manager failed; nothing was made. */
    data object Failed : PasskeysEffect

    data class ShowError(
        val error: DomainError,
    ) : PasskeysEffect
}

/** The pages the Passkeys page opens: the board's "Also protecting this account". */
enum class PasskeysLink {
    TwoStepVerification,
    ActiveSessions,
}
