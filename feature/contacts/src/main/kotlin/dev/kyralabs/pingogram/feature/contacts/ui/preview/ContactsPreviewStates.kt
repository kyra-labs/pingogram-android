package dev.kyralabs.pingogram.feature.contacts.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.feature.contacts.ui.ContactsUiState

/** Every state of the Contacts tab for its previews, named in the preview pane. */
internal class ContactsPreviewStates : PreviewParameterProvider<ContactsUiState> {
    private val states =
        listOf(
            "Contacts" to ContactsPreviewData.content,
            "Long list" to ContactsPreviewData.long,
            "Search results" to ContactsPreviewData.results,
            "No results" to ContactsPreviewData.noResults,
            "Loading" to ContactsPreviewData.loading,
            "No contacts" to ContactsPreviewData.empty,
            "Error" to ContactsPreviewData.error,
            "Deleting" to ContactsPreviewData.deleting,
            "Sync offered" to ContactsPreviewData.syncOffer,
            "Sync paused" to ContactsPreviewData.syncPaused,
            "Sync on" to ContactsPreviewData.syncOn,
        )

    override val values: Sequence<ContactsUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
