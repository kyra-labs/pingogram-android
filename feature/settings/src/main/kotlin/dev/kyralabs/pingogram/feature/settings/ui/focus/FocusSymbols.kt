package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// Each Focus profile's symbol in its shape (board 31: Work `Cookie9Sided`, Sleep `Cookie12Sided`, Weekend `Sunny`).
// The symbols stand in for Material Symbols the app does not bundle yet (work, bedtime, beach, book, fitness, car);
// they change here alone.

/** The Material Symbol of this profile icon. */
@DrawableRes
internal fun FocusIcon.symbol(): Int =
    when (this) {
        FocusIcon.Work -> UiR.drawable.ic_schedule
        FocusIcon.Sleep -> UiR.drawable.ic_notifications_off
        FocusIcon.Weekend -> UiR.drawable.ic_emoji_nature
        FocusIcon.Reading -> UiR.drawable.ic_library_books
        FocusIcon.Fitness -> UiR.drawable.ic_sports_soccer
        FocusIcon.Driving -> UiR.drawable.ic_emoji_transportation
    }

/** What screen readers call this profile icon in the editor's icon choice. */
@StringRes
internal fun FocusIcon.label(): Int =
    when (this) {
        FocusIcon.Work -> R.string.settings_focus_icon_work
        FocusIcon.Sleep -> R.string.settings_focus_icon_sleep
        FocusIcon.Weekend -> R.string.settings_focus_icon_weekend
        FocusIcon.Reading -> R.string.settings_focus_icon_reading
        FocusIcon.Fitness -> R.string.settings_focus_icon_fitness
        FocusIcon.Driving -> R.string.settings_focus_icon_driving
    }

/** The `MaterialShapes` polygon this profile icon sits on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FocusIcon.shape(): Shape =
    when (this) {
        FocusIcon.Work -> MaterialShapes.Cookie9Sided
        FocusIcon.Sleep -> MaterialShapes.Cookie12Sided
        FocusIcon.Weekend -> MaterialShapes.Sunny
        FocusIcon.Reading -> MaterialShapes.Clover4Leaf
        FocusIcon.Fitness -> MaterialShapes.Flower
        FocusIcon.Driving -> MaterialShapes.Cookie6Sided
    }.toShape()

/** A profile's symbol on its shape, [size] across, [content] on [container]; decorative, as its row names it. */
@Composable
internal fun FocusBadge(
    icon: FocusIcon,
    size: Dp,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(size).background(container, icon.shape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon.symbol()), contentDescription = null, tint = content)
    }
}
