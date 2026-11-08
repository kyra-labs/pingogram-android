package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.core.ui.component.folderIconDrawable
import dev.kyralabs.pingogram.feature.settings.R

/**
 * A folder icon Pingogram offers in the folder editor's picker: TDLib's [tdlibName] for it (td_api.tl
 * `chatFolderIcon.name`), the Material Symbol it shows as (`core:ui`'s `FolderSymbols`, which the chat list's tabs
 * share) and its [label] for screen readers. Telegram's other icon names have no symbol in the app yet: folders that
 * have one show the plain folder, and the picker leaves them out.
 */
internal enum class FolderIcon(
    val tdlibName: String,
    @StringRes val label: Int,
) {
    All("All", R.string.settings_chat_folder_icon_all),
    Unread("Unread", R.string.settings_chat_folder_icon_unread),
    Unmuted("Unmuted", R.string.settings_chat_folder_icon_unmuted),
    Private("Private", R.string.settings_chat_folder_icon_private),
    Groups("Groups", R.string.settings_chat_folder_icon_groups),
    Channels("Channels", R.string.settings_chat_folder_icon_channels),
    Custom("Custom", R.string.settings_chat_folder_icon_custom),
    Setup("Setup", R.string.settings_chat_folder_icon_setup),
    Favorite("Favorite", R.string.settings_chat_folder_icon_favorite),
    Flower("Flower", R.string.settings_chat_folder_icon_flower),
    Game("Game", R.string.settings_chat_folder_icon_game),
    Sport("Sport", R.string.settings_chat_folder_icon_sport),
    Book("Book", R.string.settings_chat_folder_icon_book),
    Travel("Travel", R.string.settings_chat_folder_icon_travel),
    Light("Light", R.string.settings_chat_folder_icon_light),
    Palette("Palette", R.string.settings_chat_folder_icon_palette),
    ;

    /** The symbol it shows as. */
    @get:DrawableRes
    val icon: Int get() = folderIconDrawable(tdlibName)

    companion object {
        /** The icon of TDLib's icon [name], or null for one Pingogram has no symbol for. */
        fun of(name: String?): FolderIcon? = entries.firstOrNull { it.tdlibName == name }
    }
}
