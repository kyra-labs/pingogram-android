package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.DrawableRes
import dev.kyralabs.pingogram.core.ui.R

/**
 * The Material Symbol a chat folder shows as, by TDLib's name of its icon (td_api.tl `chatFolderIcon.name`), shared by
 * the folder settings and the chat list's folder tabs. Telegram's other names ("Cat", "Crown", "Work"…) have no symbol
 * in the app yet and show the plain folder ([FOLDER_FALLBACK_SYMBOL]).
 */
val FolderSymbols: Map<String, Int> =
    mapOf(
        "All" to R.drawable.ic_chat,
        "Unread" to R.drawable.ic_mark_chat_unread,
        "Unmuted" to R.drawable.ic_notifications,
        "Private" to R.drawable.ic_person_filled,
        "Groups" to R.drawable.ic_contacts,
        "Channels" to R.drawable.ic_public,
        "Custom" to R.drawable.ic_folder,
        "Setup" to R.drawable.ic_settings,
        "Favorite" to R.drawable.ic_favorite,
        "Flower" to R.drawable.ic_emoji_nature,
        "Game" to R.drawable.ic_sports_esports,
        "Sport" to R.drawable.ic_sports_soccer,
        "Book" to R.drawable.ic_library_books,
        "Travel" to R.drawable.ic_emoji_transportation,
        "Light" to R.drawable.ic_emoji_objects,
        "Palette" to R.drawable.ic_palette,
    )

/** The symbol of a folder whose icon has none in the app. */
@DrawableRes
val FOLDER_FALLBACK_SYMBOL: Int = R.drawable.ic_folder

/** The symbol of TDLib's icon [name], or the plain folder for a name the app has no symbol for (or none). */
@DrawableRes
fun folderIconDrawable(name: String?): Int = FolderSymbols[name] ?: FOLDER_FALLBACK_SYMBOL
