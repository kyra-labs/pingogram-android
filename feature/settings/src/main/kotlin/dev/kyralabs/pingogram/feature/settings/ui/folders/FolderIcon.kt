package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A folder icon Pingogram offers: TDLib's [name] for it (td_api.tl `chatFolderIcon.name`), the Material Symbol it
 * shows as and its [label] for screen readers. Telegram's other icon names ("Cat", "Crown", "Work"…) have no Material
 * Symbol in the app yet: folders that have one show [FallbackIcon], and the picker leaves them out.
 */
internal enum class FolderIcon(
    val tdlibName: String,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    All("All", UiR.drawable.ic_chat, R.string.settings_chat_folder_icon_all),
    Unread("Unread", UiR.drawable.ic_mark_chat_unread, R.string.settings_chat_folder_icon_unread),
    Unmuted("Unmuted", UiR.drawable.ic_notifications, R.string.settings_chat_folder_icon_unmuted),
    Private("Private", UiR.drawable.ic_person_filled, R.string.settings_chat_folder_icon_private),
    Groups("Groups", UiR.drawable.ic_contacts, R.string.settings_chat_folder_icon_groups),
    Channels("Channels", R.drawable.ic_public, R.string.settings_chat_folder_icon_channels),
    Custom("Custom", R.drawable.ic_folder, R.string.settings_chat_folder_icon_custom),
    Setup("Setup", UiR.drawable.ic_settings, R.string.settings_chat_folder_icon_setup),
    Favorite("Favorite", UiR.drawable.ic_favorite, R.string.settings_chat_folder_icon_favorite),
    Flower("Flower", UiR.drawable.ic_emoji_nature, R.string.settings_chat_folder_icon_flower),
    Game("Game", R.drawable.ic_sports_esports, R.string.settings_chat_folder_icon_game),
    Sport("Sport", UiR.drawable.ic_sports_soccer, R.string.settings_chat_folder_icon_sport),
    Book("Book", R.drawable.ic_library_books, R.string.settings_chat_folder_icon_book),
    Travel("Travel", UiR.drawable.ic_emoji_transportation, R.string.settings_chat_folder_icon_travel),
    Light("Light", UiR.drawable.ic_emoji_objects, R.string.settings_chat_folder_icon_light),
    Palette("Palette", R.drawable.ic_palette, R.string.settings_chat_folder_icon_palette),
    ;

    companion object {
        /** The icon of TDLib's icon [name], or null for one Pingogram has no symbol for. */
        fun of(name: String?): FolderIcon? = entries.firstOrNull { it.tdlibName == name }
    }
}

/** The icon of a folder whose icon has no symbol in the app. */
@DrawableRes
internal val FallbackIcon: Int = R.drawable.ic_folder

/** The drawable of TDLib's icon [name]. */
@DrawableRes
internal fun folderIconDrawable(name: String?): Int = FolderIcon.of(name)?.icon ?: FallbackIcon
