package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.feature.chat.ui.NextChannel

/** The next unread channel for previews and screenshot tests (`docs/DESIGN_SYSTEM.md` §6 "Next unread channel"). */
internal object NextChannelPreviewData {
    /** The channel a read channel's pull opens. */
    val next = NextChannel(chatId = -300L, title = "Android Developers", photo = null)
}
