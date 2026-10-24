package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.feature.settings.ui.privacy.messages.NewChatPrivacyUiState

/** The states of the privacy pages beyond the settings with rules, for previews and goldens. */
internal object PrivacyPreviewStates {
    val newChats = NewChatPrivacyUiState.Content(NewChatPrivacy(allowsNonContacts = true, canRestrict = true))

    val newChatsRestricted =
        NewChatPrivacyUiState.Content(NewChatPrivacy(allowsNonContacts = false, canRestrict = true))

    /** Telegram does not let the account keep non-contacts out (no Telegram Premium). */
    val newChatsWithoutPremium = NewChatPrivacyUiState.Content(NewChatPrivacy(allowsNonContacts = true))

    /** Another app set a price for non-contacts' messages. */
    val newChatsPaid =
        NewChatPrivacyUiState.Content(
            NewChatPrivacy(allowsNonContacts = true, paidMessageStars = 25, canRestrict = true),
        )

    val newChatsSaving = newChatsRestricted.copy(isSaving = true)

    val newChatsError = NewChatPrivacyUiState.Error(DomainError.Network)
}
