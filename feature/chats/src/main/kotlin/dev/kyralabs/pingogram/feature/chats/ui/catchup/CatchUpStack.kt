package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The stack (`docs/DESIGN_SYSTEM.md` §6 "Catch-up"): behind the shown card, the next chat's (its header, on
 * `surfaceContainer`) and a layer below it peek out, decorative, so a card swiped away reveals the next; the shown
 * card follows the finger ([catchUpSwipe]: toward the end reads, toward the start skips, mirrored right to left) with a
 * "READ" or "SKIP" stamp fading in as the swipe nears its threshold. Under the stack, how the swipes work, which screen
 * readers do through the card's custom actions instead.
 */
@Composable
internal fun CatchUpStack(
    stage: CatchUpStage.Cards,
    onEvent: (CatchUpEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val peek = MaterialTheme.spacing.small
    val swipe =
        rememberCatchUpSwipe(
            chatId = stage.current.chatId,
            onRead = { onEvent(CatchUpEvent.MarkAsRead) },
            onSkip = { onEvent(CatchUpEvent.Skip) },
        )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = peek * 2)) {
            if (stage.position < stage.total) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(horizontal = peek * 2)
                        .offset(y = peek * 2)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraLarge),
                )
                NextCard(stage.next, Modifier.matchParentSize().padding(horizontal = peek).offset(y = peek))
            }
            Box(Modifier.fillMaxSize().catchUpSwipe(swipe, enabled = stage.reply == null)) {
                CatchUpChatCard(stage.current, onEvent, Modifier.fillMaxSize(), stage.reply)
                Stamp(
                    text = stringResource(R.string.catch_up_stamp_read),
                    icon = UiR.drawable.ic_done_all,
                    color = MaterialTheme.pingogramColors.online,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .graphicsLayer {
                                alpha = swipe.progress.coerceAtLeast(0f)
                                rotationZ = -STAMP_TILT
                            },
                )
                Stamp(
                    text = stringResource(R.string.catch_up_stamp_skip),
                    icon = R.drawable.ic_skip_next_filled,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer {
                                alpha = (-swipe.progress).coerceAtLeast(0f)
                                rotationZ = STAMP_TILT
                            },
                )
            }
        }
        if (stage.reply == null) {
            SwipeHint()
        }
    }
}

@Composable
private fun SwipeHint() {
    Text(
        text = stringResource(R.string.catch_up_swipe_hint),
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
    )
}

/** The next chat's card behind the shown one: its header alone, decorative. */
@Composable
private fun NextCard(
    card: CatchUpCard?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clearAndSetSemantics {},
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (card != null) {
            Box(Modifier.padding(MaterialTheme.spacing.medium)) { CardHeader(card) }
        }
    }
}

/** "READ" or "SKIP" in [color], on the card's surface with a border of it, decorative. */
@Composable
private fun Stamp(
    text: String,
    icon: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(MaterialTheme.spacing.large).clearAndSetSemantics {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = color,
        border = BorderStroke(MaterialTheme.sizes.avatarRing, color),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = MaterialTheme.spacing.medium,
                    vertical = MaterialTheme.spacing.small,
                ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            )
            Text(text = text, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

private const val STAMP_TILT = 8f
