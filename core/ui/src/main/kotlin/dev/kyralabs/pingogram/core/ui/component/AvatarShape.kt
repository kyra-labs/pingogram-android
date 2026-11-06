package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
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
 */
@Immutable
enum class AvatarShape {
    /** People, bots, groups, channels and Saved Messages: a circle. */
    Circle,

    /** Forums (supergroups with topics): a square with the shape scale's `large` corners at the avatar's size. */
    RoundedSquare,
    ;

    companion object {
        /**
         * The shape of a chat's avatar: a rounded square for a forum ([isForum], td_api.tl `supergroup.is_forum`), a
         * circle for every other chat.
         */
        fun ofChat(isForum: Boolean): AvatarShape = if (isForum) RoundedSquare else Circle
    }
}

/** This avatar shape as a Compose [Shape]: [CircleShape], or `MaterialTheme.shapes.large`. */
@Composable
fun AvatarShape.toShape(): Shape =
    when (this) {
        AvatarShape.Circle -> CircleShape
        AvatarShape.RoundedSquare -> MaterialTheme.shapes.large
    }

/**
 * This avatar shape at [size] as a polygon in the unit square, for the shape morphs that start from it (the selected
 * avatar's check, the profile header's collapse): `MaterialShapes.Circle`, or a square whose corners are
 * `MaterialTheme.shapes.large`'s at [size].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AvatarShape.rememberPolygon(size: Dp): RoundedPolygon {
    if (this == AvatarShape.Circle) return MaterialShapes.Circle
    val corner = MaterialTheme.shapes.large.topStart
    val density = LocalDensity.current
    return remember(corner, density, size) {
        val sidePx = with(density) { size.toPx() }.coerceAtLeast(1f)
        val radius = corner.toPx(Size(sidePx, sidePx), density) / sidePx
        roundedSquarePolygon(radius)
    }
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
