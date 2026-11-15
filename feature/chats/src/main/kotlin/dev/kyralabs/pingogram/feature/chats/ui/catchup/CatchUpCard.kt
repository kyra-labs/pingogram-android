package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One chat's card (`surfaceContainerHigh`, `extraLarge` corners), a region for screen readers named by the chat and
 * its unread messages: the avatar in the chat's shape, its name over "9 unread · 1 mention · since 9:02", an "@" badge
 * while it mentions the user; the newest unread messages, "N earlier messages" above them; then "Mark as read" (a
 * filled button), "Open chat" and "Skip" (tonal icon buttons) as a connected group, which at large font scales puts
 * "Mark as read" on a row of its own.
 */
@Composable
internal fun CatchUpChatCard(
    card: CatchUpCard,
    onEvent: (CatchUpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val region = resources.getQuantityString(R.plurals.catch_up_card, card.unreadCount, card.title, card.unreadCount)
    val markAsRead = resources.getString(R.string.catch_up_mark_as_read)
    val skip = resources.getString(R.string.catch_up_skip)
    Card(
        modifier =
            modifier.semantics {
                paneTitle = region
                // The swipes' equivalents.
                customActions =
                    listOf(
                        CustomAccessibilityAction(markAsRead) { onEvent(CatchUpEvent.MarkAsRead).let { true } },
                        CustomAccessibilityAction(skip) { onEvent(CatchUpEvent.Skip).let { true } },
                    )
            },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            CardHeader(card)
            CardMessages(card, onEvent, Modifier.weight(1f).fillMaxWidth())
            CardActions(onEvent)
        }
    }
}

@Composable
internal fun CardHeader(card: CatchUpCard) {
    val resources = LocalResources.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = card.photo,
            name = card.title,
            id = card.chatId,
            size = MaterialTheme.sizes.avatarRow,
            shape = card.avatarShape,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = card.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMediumEmphasized.userContent,
            )
            Text(
                text = cardMeta(card, resources),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (card.mentionCount > 0) MentionBadge()
    }
}

/** "9 unread · 1 mention · since 9:02". */
private fun cardMeta(
    card: CatchUpCard,
    resources: android.content.res.Resources,
): String =
    listOfNotNull(
        resources.getQuantityString(R.plurals.catch_up_unread, card.unreadCount, card.unreadCount),
        card.mentionCount.takeIf { it > 0 }?.let {
            resources.getQuantityString(R.plurals.catch_up_mentions, it, it)
        },
        card.since?.let { resources.getString(R.string.catch_up_since, it) },
    ).joinToString(resources.getString(R.string.catch_up_separator))

/** "@" on a `primary` circle: the chat mentions the user; the meta line says it for screen readers. */
@Composable
private fun MentionBadge() {
    Box(
        modifier =
            Modifier
                .size(MaterialTheme.sizes.avatarExtraSmall)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_alternate_email),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** The newest unread messages, scrolled to the newest; a `LoadingIndicator` while the history loads. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardMessages(
    card: CatchUpCard,
    onEvent: (CatchUpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages = card.messages
    if (messages == null) {
        Box(modifier, contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    val scroll = rememberScrollState()
    // Opens on the newest message, also after "N earlier messages".
    LaunchedEffect(messages.size) { scroll.scrollTo(scroll.maxValue) }
    Column(
        modifier = modifier.verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        if (card.earlierCount > 0) {
            val resources = LocalResources.current
            TextButton(onClick = { onEvent(CatchUpEvent.ShowEarlier) }, shapes = ButtonDefaults.shapes()) {
                Icon(painterResource(R.drawable.ic_unfold_more), contentDescription = null)
                Text(resources.getQuantityString(R.plurals.catch_up_earlier, card.earlierCount, card.earlierCount))
            }
        }
        messages.forEach { message -> CardMessage(message) }
    }
}

/** "Meera  Okay final plan…  9:02": the sender in the emphasized weight, the text, the time in `labelSmall`. */
@Composable
private fun CardMessage(message: CatchUpMessage) {
    val resources = LocalResources.current
    val emphasized = MaterialTheme.typography.bodyMediumEmphasized.fontWeight
    val sender = message.sender?.resolve(resources)
    val text = message.text.resolve(resources)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text =
                buildAnnotatedString {
                    if (sender != null) {
                        withStyle(SpanStyle(fontWeight = emphasized)) { append(sender) }
                        append(' ')
                    }
                    append(text)
                },
            modifier = Modifier.weight(1f),
            maxLines = MESSAGE_LINES,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
        Text(
            text = message.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private const val MESSAGE_LINES = 4

/** "Mark as read", "Open chat" and "Skip" as a connected group; "Mark as read" alone on its row from 150% font. */
@Composable
private fun CardActions(onEvent: (CatchUpEvent) -> Unit) {
    val large = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    val markAsRead =
        @Composable { modifier: Modifier ->
            Button(
                onClick = { onEvent(CatchUpEvent.MarkAsRead) },
                modifier = modifier,
                shapes =
                    if (large) {
                        ButtonDefaults.shapes()
                    } else {
                        connected(
                            ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        )
                    },
            ) {
                Icon(painterResource(UiR.drawable.ic_done_all), contentDescription = null)
                Text(
                    text = stringResource(R.string.catch_up_mark_as_read),
                    modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        if (large) markAsRead(Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            if (!large) markAsRead(Modifier.weight(1f))
            IconAction(
                icon = R.drawable.ic_open_in_new,
                label = stringResource(R.string.catch_up_open_chat),
                first = large,
                last = false,
                modifier = Modifier.weight(if (large) 1f else ICON_WEIGHT),
            ) { onEvent(CatchUpEvent.OpenChat) }
            IconAction(
                icon = R.drawable.ic_skip_next_filled,
                label = stringResource(R.string.catch_up_skip),
                first = false,
                last = true,
                modifier = Modifier.weight(if (large) 1f else ICON_WEIGHT),
            ) { onEvent(CatchUpEvent.Skip) }
        }
    }
}

@Composable
private fun IconAction(
    icon: Int,
    label: String,
    first: Boolean,
    last: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        shapes =
            connected(
                when {
                    first -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ),
        contentPadding = ButtonDefaults.SmallContentPadding,
    ) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

/** A connected group's [shapes] for a plain button, with their press morph. */
private fun connected(shapes: ToggleButtonShapes) =
    ButtonShapes(shape = shapes.shape, pressedShape = shapes.pressedShape)

private const val LARGE_FONT_SCALE = 1.5f
private const val ICON_WEIGHT = 0.45f
