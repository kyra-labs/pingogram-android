package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The conversation's small [TopAppBar] (`docs/DESIGN_SYSTEM.md` §6): back, then the chat's avatar and a two-line
 * title, its name in `titleMedium` over the subtitle in `bodySmall`: `onSurfaceVariant`, or `primary` while someone
 * is typing. No actions yet, and the title does not open the profile yet (M4). It sits on the screen's
 * `surfaceContainer`, above the sheet the messages scroll in. Without [onBack] there is no back button: in the detail
 * pane beside the chat list, which has nothing to go back to.
 *
 * It grows with the font scale instead of clipping its two lines, which the default height fits only up to about
 * 150%.
 *
 * In selection mode ([selectedCount] not null, §6 "Selection mode") the same bar shows "3 selected" in `titleLarge`
 * with a close button that leaves it ([onClearSelection]) in place of back, the titles cross-fading on the motion
 * scheme's effects springs; screen readers hear the count change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatTopBar(
    header: ChatHeader?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    selectedCount: Int? = null,
    onClearSelection: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme
    TopAppBar(
        title = {
            AnimatedContent(
                targetState = selectedCount,
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                },
                contentKey = { it != null },
                label = "chatTitle",
            ) { count ->
                if (count != null) SelectionTitle(count) else header?.let { ChatTitle(it) }
            }
        },
        modifier = modifier,
        navigationIcon = {
            when {
                selectedCount != null -> {
                    IconButton(onClick = onClearSelection) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.selection_clear),
                        )
                    }
                }

                onBack != null -> {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.chat_back),
                        )
                    }
                }
            }
        },
        expandedHeight = titleHeight(),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = colors.surfaceContainer,
                scrolledContainerColor = colors.surfaceContainer,
            ),
    )
}

/** The default height, or the height of the two lines of the title with some room when they need more. */
@Composable
private fun titleHeight(): Dp {
    val typography = MaterialTheme.typography
    val lines =
        with(LocalDensity.current) { typography.titleMedium.lineHeight.toDp() + typography.bodySmall.lineHeight.toDp() }
    return max(TopAppBarDefaults.TopAppBarExpandedHeight, lines + MaterialTheme.spacing.large)
}

/** "3 selected", a heading whose changes screen readers announce. */
@Composable
private fun SelectionTitle(count: Int) {
    Text(
        text = LocalResources.current.getQuantityString(R.plurals.selection_count, count, count),
        modifier =
            Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        maxLines = 1,
        style = MaterialTheme.typography.titleLarge,
    )
}

/** The avatar beside the name and the subtitle; one heading for screen readers. */
@Composable
private fun ChatTitle(header: ChatHeader) {
    val title = header.title.resolve()
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = header.photo,
            name = title,
            id = header.chatId,
            size = MaterialTheme.sizes.avatarSmall,
            placeholderIcon = header.placeholderIcon,
        )
        Column {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            header.subtitle?.let { subtitle ->
                Text(
                    text = subtitle.text.resolve(),
                    color =
                        when (subtitle) {
                            is ChatSubtitle.Action -> MaterialTheme.colorScheme.primary
                            is ChatSubtitle.Status -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
