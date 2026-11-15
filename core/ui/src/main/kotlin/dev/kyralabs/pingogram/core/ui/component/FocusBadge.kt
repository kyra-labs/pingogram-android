package dev.kyralabs.pingogram.core.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.FocusIcon
import dev.kyralabs.pingogram.core.ui.R

// Each Focus profile's symbol in its shape (board 31: Work `Cookie9Sided`, Sleep `Cookie12Sided`, Weekend `Sunny`),
// shared by Settings' Focus page and the chat list's Focus pill and switcher. The symbols stand in for Material
// Symbols the app does not bundle yet (work, bedtime, beach, book, fitness, car); they change here alone.

/** The Material Symbol of this profile icon. */
@DrawableRes
fun FocusIcon.symbol(): Int =
    when (this) {
        FocusIcon.Work -> R.drawable.ic_schedule
        FocusIcon.Sleep -> R.drawable.ic_notifications_off
        FocusIcon.Weekend -> R.drawable.ic_emoji_nature
        FocusIcon.Reading -> R.drawable.ic_library_books
        FocusIcon.Fitness -> R.drawable.ic_sports_soccer
        FocusIcon.Driving -> R.drawable.ic_emoji_transportation
    }

/** The `MaterialShapes` polygon this profile icon sits on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusIcon.shape(): Shape =
    when (this) {
        FocusIcon.Work -> MaterialShapes.Cookie9Sided
        FocusIcon.Sleep -> MaterialShapes.Cookie12Sided
        FocusIcon.Weekend -> MaterialShapes.Sunny
        FocusIcon.Reading -> MaterialShapes.Clover4Leaf
        FocusIcon.Fitness -> MaterialShapes.Flower
        FocusIcon.Driving -> MaterialShapes.Cookie6Sided
    }.toShape()

/** A profile's symbol on its shape, [size] across, [content] on [container]; decorative, as what shows it names it. */
@Composable
fun FocusBadge(
    icon: FocusIcon,
    size: Dp,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp? = null,
) {
    Box(
        modifier = modifier.size(size).background(container, icon.shape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon.symbol()),
            contentDescription = null,
            modifier = if (iconSize != null) Modifier.size(iconSize) else Modifier,
            tint = content,
        )
    }
}
