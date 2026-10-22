package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

// The rows of the people picker (MemberPickerScreen): a contact to toggle, and the notes in the contacts' place.

/**
 * A contact: its avatar with the online dot, its name and username, in a list item inset in the sheet, toggled by a
 * tap. Picked, the row takes the list item's selected colors and shape (`large`) and its avatar morphs into a check.
 */
@Composable
internal fun MemberRow(
    member: MemberItem,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = rowInsets()
    val shapes = ListItemDefaults.shapes()
    val colors = listItemColors(member.isPicked)
    ListItem(
        checked = member.isPicked,
        onCheckedChange = { onToggle() },
        modifier = modifier.padding(insets),
        leadingContent = {
            Avatar(
                photo = member.photo,
                name = member.name,
                id = member.userId,
                size = MaterialTheme.sizes.avatarRow,
                isOnline = member.isOnline,
                onlineRingColor = colors.containerColor,
                selected = member.isPicked,
            )
        },
        supportingContent =
            member.username?.let { username ->
                {
                    Text(
                        text = stringResource(R.string.search_username, username),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        shapes = if (member.isPicked) shapes.copy(shape = shapes.selectedShape) else shapes,
        colors = colors,
        contentPadding = insetContentPadding(insets),
    ) {
        Text(
            text = member.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.userContent,
        )
    }
}

@Composable
internal fun ListNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
internal fun ListError(
    error: MemberList.Error,
    onEvent: (MemberPickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = errorText(error.error),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { onEvent(MemberPickerEvent.Retry) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.search_retry), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** The insets of a row in the sheet: `spacing.small` from its sides, half the segmented gap from the rows around. */
@Composable
private fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
private fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

/** The list item's colors on the sheet's color, or its selected colors for a picked contact. */
@Composable
private fun listItemColors(selected: Boolean): ListItemColors {
    if (!selected) return ListItemDefaults.colors(containerColor = Color.Transparent)
    val colors = ListItemDefaults.colors()
    return colors.copy(
        containerColor = colors.selectedContainerColor,
        contentColor = colors.selectedContentColor,
        leadingContentColor = colors.selectedLeadingContentColor,
    )
}

/** A row of [scope] at most [maxWidth] wide, animating as the list changes on the [motionScheme]'s springs. */
internal fun Modifier.row(
    scope: LazyItemScope,
    maxWidth: Dp,
    motionScheme: MotionScheme,
): Modifier =
    with(scope) {
        this@row
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = motionScheme.defaultEffectsSpec(),
                placementSpec = motionScheme.defaultSpatialSpec(),
                fadeOutSpec = motionScheme.fastEffectsSpec(),
            )
    }
