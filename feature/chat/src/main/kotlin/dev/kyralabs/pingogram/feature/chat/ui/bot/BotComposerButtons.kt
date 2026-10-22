package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.BOTTOM_EDGES
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotPreviewData

/**
 * The bot button at the start of the composer's pill (`docs/DESIGN_SYSTEM.md` §6 "Composer"), in the attach button's
 * place and as large, a 48 dp touch target whose state layer morphs while pressed: "/" for the commands of the chat's
 * bots ([ComposerBotButton.Commands]), which opens their sheet; the tiles of an app for a bot's Mini App menu button
 * ([ComposerBotButton.MiniApp]), named by the bot ("Open app, Mini App" for screen readers), which Pingogram cannot
 * open yet and says so. [onClick] does either.
 */
@Composable
internal fun BotMenuButton(
    button: ComposerBotButton,
    onClick: () -> Unit,
) {
    val description =
        when (button) {
            ComposerBotButton.Commands -> stringResource(R.string.bot_commands_button)
            is ComposerBotButton.MiniApp -> stringResource(R.string.bot_mini_app_button, button.text.bidiIsolated())
        }
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter =
                painterResource(
                    if (button is ComposerBotButton.MiniApp) R.drawable.ic_bot_app else R.drawable.ic_bot_commands,
                ),
            contentDescription = description,
        )
    }
}

/**
 * "Start" in the composer's place (`REQUIREMENTS.md` FR-C5, [ComposerUiState.BotStart]), in a private chat with a bot
 * that has no messages yet or that a start link opened: a default filled `Button` with the press morph, centred in the
 * composer's bar above the keyboard and system bars as "Join" is; disabled while Telegram is asked. [onStart] starts
 * the bot, only on this tap.
 */
@Composable
internal fun BotStartButton(
    start: ComposerUiState.BotStart,
    onStart: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(BOTTOM_EDGES))
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.spacing.medium),
        contentAlignment = Alignment.Center,
    ) {
        Button(onClick = onStart, enabled = !start.isStarting, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.bot_start), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@PingogramPreviews
@Composable
private fun BotStartPreview() {
    PingogramPreview { ChatBottomBar(ComposerUiState.BotStart(), ComposerText(), {}, {}) }
}

@PingogramPreviews
@Composable
private fun BotComposerPreview() {
    PingogramPreview {
        ChatBottomBar(
            ComposerUiState.Available(
                canSchedule = true,
                attachOptions = BotPreviewData.attachOptions,
                bot = ComposerBotButton.Commands,
            ),
            ComposerText(),
            {},
            {},
        )
    }
}
