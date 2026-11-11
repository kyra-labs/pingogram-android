package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Message channel" in the composer's place of a channel whose subscribers write to its administrators (the owner's
 * board 7a): a Material medium-height `Button` across the bar (at most a bubble's width, centred), the chat symbol
 * before its label, in `primaryContainer`. Where each message costs Telegram Stars (board 7b), "Message · 10 ★" in
 * `tertiaryContainer`, under a note on `surfaceContainerHigh` that the channel charges per message and Pingogram can't
 * pay yet: information, never an offer or a payment step (`CLAUDE.md` rule 3). Either opens the user's thread with the
 * channel ([onOpen]), whose composer says the same where messages cost Stars.
 */
@Composable
internal fun MessageChannelButton(
    button: ComposerUiState.MessageChannel,
    onOpen: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val paid = button.starCount > 0
    val count = button.starCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val stars = NumberFormat.getIntegerInstance(LocalResources.current.configuration.locales[0]).format(count)
    Column(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(BOTTOM_EDGES))
                .fillMaxWidth()
                .wrapContentWidth()
                .widthIn(max = MaterialTheme.sizes.bubbleMaxWidth)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        if (paid) PaidNote(pluralStringResource(R.plurals.composer_channel_charges, count, count))
        val height = ButtonDefaults.MediumContainerHeight
        val description =
            if (paid) {
                pluralStringResource(R.plurals.composer_message_channel_paid_description, count, count)
            } else {
                stringResource(R.string.composer_message_channel)
            }
        Button(
            onClick = onOpen,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = height)
                    .semantics { contentDescription = description },
            shapes = ButtonDefaults.shapesFor(height),
            colors =
                if (paid) {
                    ButtonDefaults.buttonColors(colors.tertiaryContainer, colors.onTertiaryContainer)
                } else {
                    ButtonDefaults.buttonColors(colors.primaryContainer, colors.onPrimaryContainer)
                },
            contentPadding = ButtonDefaults.contentPaddingFor(height),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_chat),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Text(
                text =
                    if (paid) {
                        stringResource(R.string.composer_message_channel_paid, stars)
                    } else {
                        stringResource(R.string.composer_message_channel)
                    },
                modifier = Modifier.padding(start = ButtonDefaults.iconSpacingFor(height)),
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
            )
        }
    }
}

/** Why a message costs Stars here, on `surfaceContainerHigh` with `large` corners, the info symbol before it. */
@Composable
private fun PaidNote(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
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
                painter = painterResource(UiR.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@PingogramPreviews
@Composable
private fun MessageChannelPreview() {
    PingogramPreview {
        ChatBottomBar(ComposerUiState.MessageChannel(chatId = 1, starCount = 0), ComposerText(), {}, {})
    }
}

@PingogramPreviews
@Composable
private fun MessageChannelPaidPreview() {
    PingogramPreview {
        ChatBottomBar(ComposerUiState.MessageChannel(chatId = 1, starCount = 10), ComposerText(), {}, {})
    }
}
