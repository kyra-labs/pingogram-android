package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle

/**
 * The shape of a chat's avatar (`docs/DESIGN_SYSTEM.md` §2.2, §4, §8; owner decision 2026-11-06), the one rule every
 * place that shows a chat's avatar follows: the chat list, search, the forward and share sheets, the conversation's
 * title, the info page's header and the home screen widget.
 *
 * It names what the chat is, which the composable turns into a shape ([toShape], [rememberPolygon]): in the standard
 * look a circle for every chat but forums; in the experimental look (`LocalExperimentalUi`, §13) people stay circles,
 * groups take `MaterialShapes.Cookie9Sided`, channels `MaterialShapes.Cookie6Sided` and Saved Messages
 * `MaterialShapes.Clover4Leaf` (Owner decision to confirm (2026-11-07): it differs from the 2026-11-06 decision of
 * circles for every chat), forums keeping their rounded square.
 */
@Immutable
enum class AvatarShape {
    /** People and bots, and every chat not told apart: a circle. */
    Circle,

    /** Forums (supergroups with topics): a square with the shape scale's `large` corners at the avatar's size. */
    RoundedSquare,

    /** Basic groups and supergroups without topics: a circle, `Cookie9Sided` in the experimental look. */
    Group,

    /** Channels: a circle, `Cookie6Sided` (six soft lobes) in the experimental look. */
    Channel,

    /** Saved Messages: a circle, `Clover4Leaf` in the experimental look. */
    SavedMessages,
    ;

    companion object {
        /**
         * The shape of a chat's avatar: a rounded square for a forum ([isForum], td_api.tl `supergroup.is_forum`), and
         * otherwise what the chat is ([isGroup], [isChannel], [isSavedMessages]), which only the experimental look
         * shows; a circle for people and bots.
         */
        fun ofChat(
            isForum: Boolean,
            isGroup: Boolean = false,
            isChannel: Boolean = false,
            isSavedMessages: Boolean = false,
        ): AvatarShape =
            when {
                isForum -> RoundedSquare
                isSavedMessages -> SavedMessages
                isChannel -> Channel
                isGroup -> Group
                else -> Circle
            }
    }
}

/**
 * This avatar shape as a Compose [Shape]: `MaterialTheme.shapes.large` for a forum, [CircleShape] for every other chat
 * in the standard look, and the experimental look's `MaterialShapes` ([experimentalPolygon]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarShape.toShape(): Shape {
    if (this == AvatarShape.RoundedSquare) return MaterialTheme.shapes.large
    val experimental = LocalExperimentalUi.current
    val polygon = experimentalPolygon()?.takeIf { experimental } ?: return CircleShape
    return remember(polygon) { polygon.toShape() }
}

/**
 * This avatar shape at [size] as a polygon in the unit square, for the shape morphs that start from it (the selected
 * avatar's check, the profile header's collapse): `MaterialShapes.Circle` (or the experimental look's shape), or a
 * square whose corners are `MaterialTheme.shapes.large`'s at [size].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarShape.rememberPolygon(size: Dp): RoundedPolygon {
    if (this != AvatarShape.RoundedSquare) {
        val experimental = LocalExperimentalUi.current
        return experimentalPolygon()?.takeIf { experimental } ?: MaterialShapes.Circle
    }
    val corner = MaterialTheme.shapes.large.topStart
    val density = LocalDensity.current
    return remember(corner, density, size) {
        val sidePx = with(density) { size.toPx() }.coerceAtLeast(1f)
        val radius = corner.toPx(Size(sidePx, sidePx), density) / sidePx
        roundedSquarePolygon(radius)
    }
}

/** The experimental look's shape of this kind of chat (`docs/DESIGN_SYSTEM.md` §13), null where it keeps a circle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AvatarShape.experimentalPolygon(): RoundedPolygon? =
    when (this) {
        AvatarShape.Group -> MaterialShapes.Cookie9Sided
        AvatarShape.Channel -> MaterialShapes.Cookie6Sided
        AvatarShape.SavedMessages -> MaterialShapes.Clover4Leaf
        AvatarShape.Circle, AvatarShape.RoundedSquare -> null
    }

/** A square of side 1 centred in the unit square, its corners rounded by [radius] (a fraction of its side). */
internal fun roundedSquarePolygon(radius: Float): RoundedPolygon =
    RoundedPolygon.rectangle(
        width = 1f,
        height = 1f,
        rounding = CornerRounding(radius.coerceIn(0f, MAX_CORNER_FRACTION)),
        centerX = UNIT_CENTER,
        centerY = UNIT_CENTER,
    )

private const val UNIT_CENTER = 0.5f

// A corner can take at most half of the side.
private const val MAX_CORNER_FRACTION = 0.5f
