package dev.kyralabs.pingogram.feature.contacts.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.contacts.ui.ContactField
import dev.kyralabs.pingogram.feature.contacts.ui.ContactFieldError
import dev.kyralabs.pingogram.feature.contacts.ui.ContactMethod
import dev.kyralabs.pingogram.feature.contacts.ui.NewContactUiState

/**
 * Sample states of the Add contact screen, shared by the previews and the screenshot tests: a phone number being typed,
 * a username Telegram does not know, the `FLOOD_WAIT` countdown, and a number not on Telegram.
 */
internal object NewContactPreviewData {
    val phoneNumber =
        NewContactUiState(
            firstName = "Grace",
            lastName = "Hopper",
            phoneNumber = "+44 7911 123456",
            country = "🇬🇧 United Kingdom",
        )

    val username =
        NewContactUiState(
            method = ContactMethod.Username,
            username = "gracehopper",
            fieldError = ContactFieldError(ContactField.Username, DomainError.NotFound),
        )

    val floodWait = phoneNumber.copy(floodWaitSeconds = 42, floodWaitTotalSeconds = 60)

    val notOnTelegram = phoneNumber.copy(notOnTelegram = true)
}

/** Every state of the Add contact screen for its previews, named in the preview pane. */
internal class NewContactPreviewStates : PreviewParameterProvider<NewContactUiState> {
    private val states =
        listOf(
            "Phone number" to NewContactPreviewData.phoneNumber,
            "Username not found" to NewContactPreviewData.username,
            "Flood wait" to NewContactPreviewData.floodWait,
            "Not on Telegram" to NewContactPreviewData.notOnTelegram,
        )

    override val values: Sequence<NewContactUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
