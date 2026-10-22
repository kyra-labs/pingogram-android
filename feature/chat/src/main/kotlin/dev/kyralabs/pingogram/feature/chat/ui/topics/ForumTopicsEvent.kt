package dev.kyralabs.pingogram.feature.chat.ui.topics

/** What the user and the list do on a forum's topic list, sent to [ForumTopicsViewModel.onEvent]. */
sealed interface ForumTopicsEvent {
    /** Whether the list shows its last topic or is close to it: while it does, more topics load until the end. */
    data class NearEndChanged(
        val isNearEnd: Boolean,
    ) : ForumTopicsEvent

    /** "Try again" after the first page or the next one failed. */
    data object Retry : ForumTopicsEvent
}
