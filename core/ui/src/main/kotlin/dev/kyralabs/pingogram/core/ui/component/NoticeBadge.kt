package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.graphics.shapes.RoundedPolygon

/** The badge of a [ListNotice]: [icon] tinted [content] on a [shape] of `MaterialShapes` in [container]. */
@Immutable
data class NoticeBadge(
    val shape: RoundedPolygon,
    @param:DrawableRes val icon: Int,
    val container: Color,
    val content: Color,
)
