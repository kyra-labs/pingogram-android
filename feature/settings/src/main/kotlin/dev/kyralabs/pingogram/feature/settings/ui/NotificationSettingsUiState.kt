package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the Notifications page shows (`REQUIREMENTS.md` FR-S1, FR-N1).
 *
 * @property scopes the defaults of private chats, groups and channels, from TDLib.
 * @property notificationsBlocked Android shows no notification of Pingogram: the permission was refused (Android 13+)
 *   or the user turned the app's notifications off; the page says so first and offers to turn them on.
 * @property blockedChannels the kinds of chats whose notification channel the user turned off in Android's settings.
 * @property isKeepConnectedOffered the build offers "Keep connected" (`foss`, `PushDelivery.TdlibConnection`).
 * @property keepConnected "Keep connected" is on.
 * @property contactJoined whether Telegram tells the user when a contact joins; null until TDLib has said.
 * @property accountSlot the shown account's slot, whose notification channels the page opens
 *   (`docs/adr/0031-multiple-accounts.md`).
 * @property fromAllAccounts "Notifications from all accounts"; null (and not shown) with one account on the device.
 * @property isResetDialogOpen the confirmation of "Reset all notification settings" is open.
 * @property isResetting resetting was confirmed and TDLib has not answered yet.
 */
@Immutable
data class NotificationSettingsUiState(
    val scopes: ScopeSettingsState = ScopeSettingsState.Loading,
    val notificationsBlocked: Boolean = false,
    val blockedChannels: ImmutableSet<NotificationScope> = persistentSetOf(),
    val isKeepConnectedOffered: Boolean = false,
    val keepConnected: Boolean = false,
    val contactJoined: Boolean? = null,
    val accountSlot: Int = 0,
    val fromAllAccounts: Boolean? = null,
    val isResetDialogOpen: Boolean = false,
    val isResetting: Boolean = false,
)

/** The defaults of the kinds of chats, as the Notifications page shows them. */
@Immutable
sealed interface ScopeSettingsState {
    data object Loading : ScopeSettingsState

    /** They could not be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : ScopeSettingsState

    /** The defaults of every [NotificationScope]. */
    data class Content(
        val settings: ImmutableMap<NotificationScope, ScopeNotificationSettings>,
    ) : ScopeSettingsState
}

/** What the user does on the Notifications page, handled by [NotificationSettingsViewModel.onEvent]. */
sealed interface NotificationSettingsEvent {
    /** Load the defaults again after they failed to load. */
    data object Retry : NotificationSettingsEvent

    /** One of [scope]'s switches was flipped: [settings] are its defaults with that change. */
    data class ChangeScope(
        val scope: NotificationScope,
        val settings: ScopeNotificationSettings,
    ) : NotificationSettingsEvent

    /** "Sound and vibration" of [scope]: open its notification channel in Android's settings. */
    data class OpenChannelSettings(
        val scope: NotificationScope,
    ) : NotificationSettingsEvent

    /**
     * What Android allows, as the page found it when it showed or came back (the user may have changed it in Android's
     * settings meanwhile): whether it shows Pingogram's notifications at all ([notificationsEnabled]); whether the
     * notification permission is granted ([permissionGranted], always before Android 13) and whether Android would
     * show its dialog again with a reason first ([shouldShowRationale]); and the kinds of chats whose channel is off.
     */
    data class AccessChecked(
        val notificationsEnabled: Boolean,
        val permissionGranted: Boolean,
        val shouldShowRationale: Boolean,
        val blockedChannels: Set<NotificationScope>,
    ) : NotificationSettingsEvent

    /** The banner of blocked notifications was tapped. */
    data object TurnOnNotifications : NotificationSettingsEvent

    data class SetKeepConnected(
        val enabled: Boolean,
    ) : NotificationSettingsEvent

    data class SetContactJoined(
        val enabled: Boolean,
    ) : NotificationSettingsEvent

    /** "Notifications from all accounts" was flipped. */
    data class SetFromAllAccounts(
        val enabled: Boolean,
    ) : NotificationSettingsEvent

    /** "Reset all notification settings" was tapped: ask for confirmation. */
    data object Reset : NotificationSettingsEvent

    data object ConfirmReset : NotificationSettingsEvent

    data object DismissReset : NotificationSettingsEvent
}

/** One-off side effects of [NotificationSettingsViewModel], handled once by its route. */
sealed interface NotificationSettingsEffect {
    /** A change could not be saved or the reset failed, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : NotificationSettingsEffect

    /** Show the system's dialog for the notification permission (Android 13+). */
    data object RequestPermission : NotificationSettingsEffect

    /** Open the app's notification settings in Android's settings. */
    data object OpenAppNotificationSettings : NotificationSettingsEffect

    /** Open the notification channel of [scope]'s messages, the account in [slot]'s, in Android's settings. */
    data class OpenChannelSettings(
        val scope: NotificationScope,
        val slot: Int = 0,
    ) : NotificationSettingsEffect

    /** Every notification setting was reset, said in a snackbar. */
    data object ResetDone : NotificationSettingsEffect
}

/** The pages the Notifications page opens, each pushed above it. */
@Immutable
sealed interface NotificationSettingsPage {
    /** Whom the account hears of reactions and poll votes from. */
    data object Reactions : NotificationSettingsPage

    /** The chats of [scope] with settings of their own. */
    data class Exceptions(
        val scope: NotificationScope,
    ) : NotificationSettingsPage
}
