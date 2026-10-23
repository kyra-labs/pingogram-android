package dev.kyralabs.pingogram.feature.chat.ui.bots

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotButtonEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotButtonsPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import dev.kyralabs.pingogram.core.ui.R as UiR

// The buttons a bot attaches under a message (docs/DESIGN_SYSTEM.md §6 "Bot buttons").

/**
 * [keyboard]'s rows under a bubble, as wide as a bubble can be: each row a connected group of `FilledTonalButton`s
 * sharing its width equally (`ButtonGroupDefaults.ConnectedSpaceBetween` apart, the connected shapes with their press
 * morph; a row of one keeps the pill's), at the small container height in its 48 dp touch target (rows `spacing.small`
 * apart, so the targets do not overlap), labels in the
 * button's `labelLarge` as user content on one line. A button that opens a link or an inline query ends in a small
 * icon; one waiting for Telegram shows a `LoadingIndicator` there instead ([BotKeyboard.pending]). A tap goes to
 * [onPress]. Screen readers reach the buttons as the bubble's custom actions ([botButtonActions]), as the bubble is
 * read as one. A column of rows, measured once with the bubble's row (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun BotKeyboardButtons(
    keyboard: BotKeyboard,
    onPress: (BotButtonPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Rows a small spacing apart: the buttons are 40 dp tall, so their 48 dp touch targets meet without overlapping.
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        keyboard.rows.forEachIndexed { rowIndex, row ->
            key(rowIndex) { BotKeyboardRow(row, rowIndex, keyboard.pending, onPress) }
        }
    }
}

/**
 * Row [rowIndex] of a keyboard, its [buttons] a connected group sharing the row's width, those in [pending] showing
 * their progress; a tap goes to [onPress]. Also a row of a bot's keyboard in the system keyboard's place
 * (`BotReplyKeyboardPanel`).
 */
@Composable
internal fun BotKeyboardRow(
    buttons: ImmutableList<BotKeyboardButton>,
    rowIndex: Int,
    pending: ImmutableSet<BotButtonPosition>,
    onPress: (BotButtonPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        buttons.forEachIndexed { column, button ->
            key(column) {
                val position = BotButtonPosition(rowIndex, column)
                BotButton(
                    button = button,
                    shapes = connectedShapes(column, buttons.size),
                    isPending = position in pending,
                    onClick = { onPress(position) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One button: its text, then its mark or its progress. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
private fun BotButton(
    button: BotKeyboardButton,
    shapes: ButtonShapes,
    isPending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MinHeight
    val hasEnd = isPending || button.mark != BotButtonMark.None
    FilledTonalButton(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.heightIn(min = height),
        enabled = button.isEnabled,
        colors = button.tone.colors(),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasEndIcon = hasEnd),
    ) {
        Text(
            text = button.text,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ButtonDefaults.textStyleFor(height).userContent,
        )
        if (hasEnd) {
            val iconSize = ButtonDefaults.iconSizeFor(height)
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
            if (isPending) {
                LoadingIndicator(modifier = Modifier.size(iconSize), color = LocalContentColor.current)
            } else {
                Icon(
                    painter =
                        painterResource(
                            if (button.mark ==
                                BotButtonMark.Link
                            ) {
                                R.drawable.ic_open_in_new
                            } else {
                                UiR.drawable.ic_alternate_email
                            },
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

/** The shapes of the button at [index] of a row of [count]: connected, or the pill of a button alone. */
@Composable
private fun connectedShapes(
    index: Int,
    count: Int,
): ButtonShapes {
    val toggleShapes =
        when {
            count == 1 -> return ButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
            index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        }
    return ButtonShapes(shape = toggleShapes.shape, pressedShape = toggleShapes.pressedShape)
}

/** The colors of [this] tone, from the scheme. */
@Composable
private fun BotButtonTone.colors(): ButtonColors {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        BotButtonTone.Tonal -> {
            ButtonDefaults.filledTonalButtonColors()
        }

        BotButtonTone.Primary -> {
            ButtonDefaults.buttonColors()
        }

        BotButtonTone.Danger -> {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = scheme.errorContainer,
                contentColor = scheme.onErrorContainer,
            )
        }
    }
}

/**
 * The screen reader's actions of [keyboard]'s buttons, in reading order, each named by its text (and what it opens);
 * none for disabled buttons.
 */
internal fun botButtonActions(
    resources: Resources,
    keyboard: BotKeyboard,
): List<Pair<String, BotButtonEvent>> =
    keyboard.rows.flatMapIndexed { rowIndex, row ->
        row.mapIndexedNotNull { column, button ->
            if (!button.isEnabled) return@mapIndexedNotNull null
            val label =
                when (button.mark) {
                    BotButtonMark.None -> button.text
                    BotButtonMark.Link -> resources.getString(R.string.bot_button_link_action, button.text)
                    BotButtonMark.SwitchInline -> resources.getString(R.string.bot_button_inline_action, button.text)
                }
            label to BotButtonEvent.Press(keyboard.messageId, BotButtonPosition(rowIndex, column))
        }
    }

@PingogramPreviews
@Composable
private fun BotKeyboardButtonsPreview() {
    PingogramPreview {
        BotKeyboardButtons(BotButtonsPreviewData.keyboard, onPress = {})
    }
}

@PingogramPreviews
@Composable
private fun BotKeyboardButtonsPendingPreview() {
    PingogramPreview {
        BotKeyboardButtons(BotButtonsPreviewData.pendingKeyboard, onPress = {})
    }
}
