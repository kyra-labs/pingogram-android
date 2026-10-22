package dev.kyralabs.pingogram.core.ui.transition

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The key of the container transform from a conversation's avatar into the page its title opens
 * (`docs/DESIGN_SYSTEM.md` §5, "avatar → profile"): a user's profile ([profile]) or a group's or channel's info page
 * ([chatInfo]). Equal on both sides, and on no other content.
 */
data class AvatarTransformKey private constructor(
    val kind: Kind,
    val id: Long,
) {
    /** What the avatar opens. */
    enum class Kind {
        Profile,
        ChatInfo,
    }

    companion object {
        /** The avatar that opens user [userId]'s profile. */
        fun profile(userId: Long) = AvatarTransformKey(Kind.Profile, userId)

        /** The avatar that opens the info page of group or channel [chatId]. */
        fun chatInfo(chatId: Long) = AvatarTransformKey(Kind.ChatInfo, chatId)
    }
}

/**
 * The avatar's side of the container transform of [key] ([containerTransform]): the round avatar grows into the page
 * it opens, and the page shrinks back into it, the circle morphing to the page's square corners and back. Nothing when
 * [key] is null (the title opens nothing), with reduced motion, or outside a `NavHost` destination.
 */
@Composable
fun Modifier.avatarTransform(key: AvatarTransformKey?): Modifier =
    if (key == null) this else containerTransform(key, shape = CircleShape, counterpartShape = FullBleedShape)

/** The page's side of the container transform of [key] ([avatarTransform]): a full-window page with square corners. */
@Composable
fun Modifier.avatarTransformTarget(key: AvatarTransformKey): Modifier =
    containerTransform(key, shape = FullBleedShape, counterpartShape = CircleShape)
