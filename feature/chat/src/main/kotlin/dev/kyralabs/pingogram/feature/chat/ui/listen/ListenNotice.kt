package dev.kyralabs.pingogram.feature.chat.ui.listen

/** What Listen to posts tells the user in a snackbar instead of reading. */
enum class ListenNotice {
    /** No voice of the device's language works offline; the snackbar opens Android's text-to-speech settings. */
    NoOfflineVoice,

    /** The device has no speech engine, or it failed. */
    Unavailable,

    /** The message or posts have no text to read. */
    NothingToRead,

    /** The posts could not be loaded (offline, or Telegram asked to wait). */
    NotLoaded,
}
