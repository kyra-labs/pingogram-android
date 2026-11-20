package dev.kyralabs.pingogram.feature.media.storyalbum

/** What the user does in a profile's Stories tab. */
sealed interface ProfileStoriesEvent {
    /** The grid nears its end: load the next page. */
    data object LoadMore : ProfileStoriesEvent

    /** Try again after a failure. */
    data object Retry : ProfileStoriesEvent
}
