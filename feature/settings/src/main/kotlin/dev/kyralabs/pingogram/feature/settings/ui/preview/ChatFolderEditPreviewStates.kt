package dev.kyralabs.pingogram.feature.settings.ui.preview

import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.feature.settings.ui.folders.ChatFolderEditUiState
import dev.kyralabs.pingogram.feature.settings.ui.folders.FolderChatItem
import dev.kyralabs.pingogram.feature.settings.ui.folders.FolderPickerSide
import dev.kyralabs.pingogram.feature.settings.ui.folders.FolderPickerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of the folder editor and its picker for previews and screenshot tests. */
internal object ChatFolderEditPreviewStates {
    private val ada = FolderChatItem(chatId = 1, title = "Ada Lovelace", photo = null)
    private val team = FolderChatItem(chatId = 2, title = "Design team", photo = null)
    private val news = FolderChatItem(chatId = 3, title = "Pingogram News", photo = null)

    val editing =
        ChatFolderEditUiState.Content(
            isNew = false,
            name = "Work",
            iconName = "Custom",
            includedTypes = persistentSetOf(ChatFolderIncludedType.Groups),
            excludedTypes = persistentSetOf(ChatFolderExcludedType.Muted),
            includedChats = persistentListOf(ada, team),
            excludedChats = persistentListOf(news),
        )

    val new = ChatFolderEditUiState.Content(isNew = true)

    val refused = new.copy(nameMissing = true, nothingIncluded = true)

    val saving = editing.copy(isSaving = true)

    val confirmingDelete = editing.copy(confirmingDelete = true)

    val picker =
        FolderPickerUiState(
            side = FolderPickerSide.Included,
            includedTypes = persistentSetOf(ChatFolderIncludedType.Groups),
            chats = persistentListOf(ada.copy(selected = true), team.copy(selected = true), news),
        )
}
