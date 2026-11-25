package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * How the photo of an avatar of [kind] takes its [shape]: cut by [AvatarMask] where the kind has one, except in
 * previews and screenshots, whose images (Coil's preview handler) skip a request's transformations, so the clip stays.
 */
@Composable
internal fun rememberAvatarClip(
    shape: Shape,
    kind: AvatarShape,
): AvatarClip {
    val preview = LocalInspectionMode.current
    return remember(shape, kind, preview) { AvatarClip(shape, if (preview) null else kind.photoMask()) }
}

/**
 * How an avatar's photo takes its shape: clipped to [shape] where the GPU clips it without a mask (a circle, a rounded
 * square), else cut to it once by [mask] and drawn unclipped (`docs/PERFORMANCE.md` §5).
 */
@Immutable
internal class AvatarClip(
    val shape: Shape,
    val mask: AvatarMask?,
)
