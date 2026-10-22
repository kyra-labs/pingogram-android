package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the invite links page of a group or channel shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface InviteLinksUiState {
    data object Loading : InviteLinksUiState

    data class Error(
        val error: DomainError,
    ) : InviteLinksUiState

    /**
     * @property primary the primary link, which everyone who may invite shares; null if there is none yet.
     * @property links the other links the user made that still work, or no longer do (expired, full), newest first.
     * @property revoked the user's revoked links, which can only be deleted.
     * @property busy the links a request is on its way for.
     */
    data class Content(
        val primary: InviteLinkItem? = null,
        val links: ImmutableList<InviteLinkItem> = persistentListOf(),
        val revoked: ImmutableList<InviteLinkItem> = persistentListOf(),
        val dialog: InviteLinksDialog? = null,
        val busy: ImmutableList<String> = persistentListOf(),
    ) : InviteLinksUiState
}

/**
 * An invite link as its row shows it.
 *
 * @property title its name, else the link without `https://`.
 * @property detail "3 joined · Expires Oct 30", "Expired", "Full", "2 waiting for approval".
 * @property isActive it still works.
 * @property canEdit its settings can be changed: not the primary link, a revoked one or a subscription.
 */
@Immutable
data class InviteLinkItem(
    val link: String,
    val title: String,
    val detail: UiText,
    val isActive: Boolean,
    val canEdit: Boolean,
) {
    override fun toString() = "InviteLinkItem(isActive=$isActive, …)"
}

/** A confirmation or sheet the invite links page shows. */
@Immutable
sealed interface InviteLinksDialog {
    /** Revoking [link]: nobody can join through it any more ([isPrimary]: a new primary link replaces it). */
    data class Revoke(
        val link: String,
        val isPrimary: Boolean,
    ) : InviteLinksDialog {
        override fun toString() = "Revoke(isPrimary=$isPrimary, …)"
    }

    data class Delete(
        val link: String,
    ) : InviteLinksDialog {
        override fun toString() = "Delete(…)"
    }

    data object DeleteAllRevoked : InviteLinksDialog

    data class QrCode(
        val link: String,
    ) : InviteLinksDialog {
        override fun toString() = "QrCode(…)"
    }
}

sealed interface InviteLinksEvent {
    data object Retry : InviteLinksEvent

    /** The page shows again after a link was created or edited: read the links again. */
    data object Refresh : InviteLinksEvent

    data object Create : InviteLinksEvent

    data class Copy(
        val link: String,
    ) : InviteLinksEvent

    data class Share(
        val link: String,
    ) : InviteLinksEvent

    data class ShowQrCode(
        val link: String,
    ) : InviteLinksEvent

    data class Edit(
        val link: String,
    ) : InviteLinksEvent

    data class Revoke(
        val link: String,
    ) : InviteLinksEvent

    data class Delete(
        val link: String,
    ) : InviteLinksEvent

    data object DeleteAllRevoked : InviteLinksEvent

    /** Do what the dialog shown asks to confirm. */
    data object Confirm : InviteLinksEvent

    data object DismissDialog : InviteLinksEvent
}

sealed interface InviteLinksEffect {
    /** Open the page that creates a link, or edits [link]. */
    data class OpenEditor(
        val link: String?,
    ) : InviteLinksEffect {
        override fun toString() = "OpenEditor(isNew=${link == null})"
    }

    data class Copy(
        val link: String,
    ) : InviteLinksEffect {
        override fun toString() = "Copy(…)"
    }

    data class Share(
        val link: String,
    ) : InviteLinksEffect {
        override fun toString() = "Share(…)"
    }

    /** A link was revoked or deleted: a snackbar says so. */
    data class Done(
        val text: UiText,
    ) : InviteLinksEffect

    data class ShowError(
        val error: DomainError,
    ) : InviteLinksEffect
}
