package dev.kyralabs.pingogram.feature.chat.ui

import android.util.Log

/**
 * Where the conversation logs what fails without the user hearing of it (`REQUIREMENTS.md` §6.2): a seam, as
 * `android.util.Log` is not there in JVM unit tests. Messages name what failed and the kind of error, never message
 * text, file paths or ids.
 */
internal fun interface ChatLog {
    fun warn(message: String)
}

internal object AndroidChatLog : ChatLog {
    private const val TAG = "Chat"

    override fun warn(message: String) {
        Log.w(TAG, message)
    }
}
