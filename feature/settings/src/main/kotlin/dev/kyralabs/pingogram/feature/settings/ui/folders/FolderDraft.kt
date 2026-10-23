package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import kotlinx.collections.immutable.persistentListOf

/**
 * What the folder editor changes of a folder: its [name], icon, and the chats and kinds of chats on each side, the
 * folder's pinned chats among the [included] ones. Kept in the editor's saved state ([saveTo], [restore]).
 */
internal data class FolderDraft(
    val name: String,
    val iconName: String?,
    val includedTypes: Set<ChatFolderIncludedType>,
    val excludedTypes: Set<ChatFolderExcludedType>,
    val included: List<Long>,
    val excluded: List<Long>,
) {
    val includesAnything: Boolean get() = includedTypes.isNotEmpty() || included.isNotEmpty()

    fun chatsOf(side: FolderPickerSide): List<Long> =
        when (side) {
            FolderPickerSide.Included -> included
            FolderPickerSide.Excluded -> excluded
        }

    /** With chat [id] on [side], and not on the other. */
    fun with(
        side: FolderPickerSide,
        id: Long,
    ): FolderDraft =
        when (side) {
            FolderPickerSide.Included -> copy(included = included - id + id, excluded = excluded - id)
            FolderPickerSide.Excluded -> copy(excluded = excluded - id + id, included = included - id)
        }

    fun without(
        side: FolderPickerSide,
        id: Long,
    ): FolderDraft =
        when (side) {
            FolderPickerSide.Included -> copy(included = included - id)
            FolderPickerSide.Excluded -> copy(excluded = excluded - id)
        }

    /**
     * [original] with this draft's changes: its title kept, custom emoji included, while the name keeps its text; its
     * pinned chats kept while they are still included.
     */
    fun applyTo(original: ChatFolderSettings): ChatFolderSettings {
        val title = name.trim()
        return original.copy(
            title = if (title == original.title.text) original.title else FormattedText(title, persistentListOf()),
            iconName = iconName,
            pinnedChatIds = original.pinnedChatIds.filter { it in included },
            includedChatIds = included,
            excludedChatIds = excluded,
            includedTypes = includedTypes,
            excludedTypes = excludedTypes,
        )
    }

    fun saveTo(handle: SavedStateHandle) {
        handle[KEY_NAME] = name
        handle[KEY_ICON] = iconName
        handle[KEY_INCLUDED_TYPES] = includedTypes.map { it.name }.toTypedArray()
        handle[KEY_EXCLUDED_TYPES] = excludedTypes.map { it.name }.toTypedArray()
        handle[KEY_INCLUDED] = included.toLongArray()
        handle[KEY_EXCLUDED] = excluded.toLongArray()
    }

    companion object {
        fun of(settings: ChatFolderSettings) =
            FolderDraft(
                name = settings.title.text,
                iconName = settings.iconName,
                includedTypes = settings.includedTypes,
                excludedTypes = settings.excludedTypes,
                included = (settings.pinnedChatIds + settings.includedChatIds).distinct(),
                excluded = settings.excludedChatIds,
            )

        /** The draft [saveTo] kept, or null before the user changed anything. */
        fun restore(handle: SavedStateHandle): FolderDraft? {
            val name = handle.get<String>(KEY_NAME) ?: return null
            return FolderDraft(
                name = name,
                iconName = handle[KEY_ICON],
                includedTypes =
                    handle
                        .get<Array<String>>(KEY_INCLUDED_TYPES)
                        .orEmpty()
                        .mapNotNull { name -> ChatFolderIncludedType.entries.firstOrNull { it.name == name } }
                        .toSet(),
                excludedTypes =
                    handle
                        .get<Array<String>>(KEY_EXCLUDED_TYPES)
                        .orEmpty()
                        .mapNotNull { name -> ChatFolderExcludedType.entries.firstOrNull { it.name == name } }
                        .toSet(),
                included = handle.get<LongArray>(KEY_INCLUDED)?.toList().orEmpty(),
                excluded = handle.get<LongArray>(KEY_EXCLUDED)?.toList().orEmpty(),
            )
        }

        private const val KEY_NAME = "draft.name"
        private const val KEY_ICON = "draft.icon"
        private const val KEY_INCLUDED_TYPES = "draft.includedTypes"
        private const val KEY_EXCLUDED_TYPES = "draft.excludedTypes"
        private const val KEY_INCLUDED = "draft.included"
        private const val KEY_EXCLUDED = "draft.excluded"
    }
}
