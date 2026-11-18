package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramAmoledPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.preview.PingogramVariantPreviews
import dev.kyralabs.pingogram.core.ui.theme.CategoryColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A row's leading icon in a circle of its category's color (`docs/DESIGN_SYSTEM.md` §13.10, "Row icons"): [icon] in
 * [colors]' content on its container, `sizes.leadingIconContainer` wide, as a list item's leading avatar, so rows keep
 * their heights. Decorative: the row's text names it. Disabled, it fades as a disabled row's text does.
 */
@Composable
fun CategoryIconBadge(
    icon: Painter,
    colors: CategoryColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .size(MaterialTheme.sizes.leadingIconContainer)
                .background(
                    color = if (enabled) colors.container else onSurface.copy(alpha = DISABLED_CONTAINER_ALPHA),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = if (enabled) colors.content else onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
        )
    }
}

// Material's disabled container and content opacities (docs/DESIGN_SYSTEM.md §2.2).
private const val DISABLED_CONTAINER_ALPHA = 0.12f
private const val DISABLED_CONTENT_ALPHA = 0.38f

@PingogramPreviews
@PingogramVariantPreviews
@PingogramAmoledPreview
@Composable
private fun CategoryIconBadgePreview() {
    PingogramPreview {
        val categories = MaterialTheme.pingogramColors.categories
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            PreviewIcons.forEachIndexed { index, icon ->
                CategoryIconBadge(painterResource(icon), categories[index])
            }
            CategoryIconBadge(painterResource(R.drawable.ic_lock), categories[0], enabled = false)
        }
    }
}

private val PreviewIcons =
    listOf(
        R.drawable.ic_notifications,
        R.drawable.ic_bookmark_filled,
        R.drawable.ic_alarm,
        R.drawable.ic_palette,
        R.drawable.ic_folder,
        R.drawable.ic_info,
    )
