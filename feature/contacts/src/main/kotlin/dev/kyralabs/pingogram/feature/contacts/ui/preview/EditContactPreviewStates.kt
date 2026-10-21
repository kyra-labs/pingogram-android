package dev.kyralabs.pingogram.feature.contacts.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.contacts.ui.EditContactUiState

/**
 * Sample states of the Edit contact screen, shared by the previews and the screenshot tests: a contact, a user to add
 * who cannot see the user's number yet, loading, an error, and the question before deleting.
 */
internal object EditContactPreviewData {
    val contact =
        EditContactUiState.Content(
            userId = 4,
            name = "Grace Hopper",
            photo = null,
            phoneNumber = "447911123456",
            isContact = true,
            canSharePhoneNumber = false,
            firstName = "Grace",
            lastName = "Hopper",
            sharePhoneNumber = false,
        )

    val notContact =
        contact.copy(
            userId = 11,
            name = "Alan Turing",
            phoneNumber = "",
            isContact = false,
            canSharePhoneNumber = true,
            firstName = "Alan",
            lastName = "Turing",
            sharePhoneNumber = true,
        )

    val loading = EditContactUiState.Loading

    val error = EditContactUiState.Error(DomainError.Network)

    val deleting = contact.copy(showsDeleteDialog = true)
}

/** Every state of the Edit contact screen for its previews, named in the preview pane. */
internal class EditContactPreviewStates : PreviewParameterProvider<EditContactUiState> {
    private val states =
        listOf(
            "Contact" to EditContactPreviewData.contact,
            "Add to contacts" to EditContactPreviewData.notContact,
            "Loading" to EditContactPreviewData.loading,
            "Error" to EditContactPreviewData.error,
            "Deleting" to EditContactPreviewData.deleting,
        )

    override val values: Sequence<EditContactUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
