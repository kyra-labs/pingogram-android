package dev.kyralabs.pingogram.link

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.domain.model.LinkTarget

/** What came of opening a Telegram link ([LinkViewModel]): a screen to open, or a snackbar. */
sealed interface LinkEffect {
    /**
     * Opens the screen of [target] (`openLinkTarget`). [fromOutside]: the link came from another app, so a chat opens
     * above the chat list, as from a notification; a link tapped in the app opens above the screen it was tapped on.
     */
    data class Open(
        val target: LinkTarget,
        val fromOutside: Boolean,
    ) : LinkEffect

    /** Says what stops the link from opening, in a snackbar. */
    data class Notice(
        val notice: LinkNotice,
    ) : LinkEffect
}

/** Why a link does not open. */
sealed interface LinkNotice {
    /** A kind of link Pingogram does not open; [browserUrl] is the `https` link a browser can open instead, if any. */
    data class Unsupported(
        val browserUrl: String?,
    ) : LinkNotice {
        override fun toString() = "Unsupported(web=${browserUrl != null})"
    }

    /** A link whose screen Pingogram does not have yet ([hasScreen]): an invitation, a QR login, a boost. */
    data object NotYet : LinkNotice

    /** A login code: never used, and not to be shared (`docs/adr/0013-deep-links.md`, Security). */
    data object LoginCode : LinkNotice

    /** What the link named leads to nothing. */
    data class NotFound(
        val subject: LinkSubject,
    ) : LinkNotice

    /** Looking the link up failed: no network, a `FLOOD_WAIT` … */
    data class Failed(
        val error: DomainError,
    ) : LinkNotice
}

/**
 * Whether Pingogram has a screen for [this][LinkTarget] yet. A message to share opens the share sheet. An invitation
 * (the join sheet), a QR code login (its confirmation) and a boost come with their screens later; until then they say
 * so ([LinkNotice.NotYet]). Notices ([LinkTarget.NotFound], [LinkTarget.LoginCode], [LinkTarget.Unsupported]) have
 * none.
 */
internal fun LinkTarget.hasScreen(): Boolean =
    when (this) {
        is LinkTarget.Chat,
        is LinkTarget.ChatInfo,
        is LinkTarget.UserProfile,
        LinkTarget.EditProfile,
        LinkTarget.Contacts,
        is LinkTarget.Settings,
        is LinkTarget.NewChat,
        LinkTarget.Search,
        is LinkTarget.MessageDraft,
        -> true

        is LinkTarget.ChatInvite,
        is LinkTarget.QrCodeLogin,
        is LinkTarget.ChatBoost,
        LinkTarget.LoginCode,
        is LinkTarget.NotFound,
        is LinkTarget.Unsupported,
        -> false
    }
