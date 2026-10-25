package dev.kyralabs.pingogram.feature.chat.ui.topics

import dev.kyralabs.pingogram.core.common.result.DomainError

/** What the topic list does once, beside its state ([ForumTopicsViewModel.effects]). */
sealed interface ForumTopicsEffect {
    /** A topic the user created: its conversation opens. */
    data class OpenTopic(
        val topicId: Int,
    ) : ForumTopicsEffect

    /** A topic the user deleted: a snackbar says so. */
    data object TopicDeleted : ForumTopicsEffect

    /** Managing a topic failed: a snackbar says why. */
    data class Failed(
        val error: DomainError,
    ) : ForumTopicsEffect
}
