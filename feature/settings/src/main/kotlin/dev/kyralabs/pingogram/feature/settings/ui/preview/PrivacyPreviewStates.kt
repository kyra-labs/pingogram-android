package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions.ExceptionCandidate
import dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions.ExceptionEntry
import dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions.ExceptionKey
import dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions.ExceptionPickerUiState
import dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions.PrivacyExceptionsUiState
import dev.kyralabs.pingogram.feature.settings.ui.privacy.messages.NewChatPrivacyUiState
import kotlinx.collections.immutable.persistentListOf

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

    private val adaEntry = ExceptionEntry(ExceptionKey(5, isGroup = false), UiText.Plain("Ada Lovelace"), photo = null)
    private val graceEntry =
        ExceptionEntry(ExceptionKey(6, isGroup = false), UiText.Plain("Grace Hopper"), photo = null)
    private val teamEntry =
        ExceptionEntry(ExceptionKey(-100, isGroup = true), UiText.Plain("Analytical Engine"), photo = null)

    val exceptions = PrivacyExceptionsUiState.Content(persistentListOf(adaEntry, teamEntry))

    val exceptionsEmpty = PrivacyExceptionsUiState.Content(persistentListOf())

    val exceptionsSaving = exceptions.copy(isSaving = true)

    val exceptionsError = PrivacyExceptionsUiState.Error(DomainError.Network)

    val exceptionsPicker =
        ExceptionPickerUiState(
            query = "",
            candidates =
                persistentListOf(
                    ExceptionCandidate(adaEntry, isGroup = false, selected = true),
                    ExceptionCandidate(graceEntry, isGroup = false, selected = false),
                    ExceptionCandidate(teamEntry, isGroup = true, selected = true),
                ),
        )
}
