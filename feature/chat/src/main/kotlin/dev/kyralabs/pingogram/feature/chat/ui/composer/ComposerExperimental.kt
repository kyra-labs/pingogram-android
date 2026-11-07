package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotMenuButton
import dev.kyralabs.pingogram.core.ui.R as UiR

// The composer's pill start in the experimental look (`docs/DESIGN_SYSTEM.md` §13.5).

/**
 * The experimental look's attach button at the pill's start (§13.5): a "+" in a `primary` circle, Material's default
 * filled icon button (40 dp in its 48 dp touch target), whose shape morphs while pressed.
 */
@Composable
internal fun ExperimentalAttachButton(onAttach: () -> Unit) {
    FilledIconButton(onClick = onAttach, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = stringResource(R.string.attach),
        )
    }
}

/**
 * The experimental look's camera button after the attach button (§13.5): a tonal circle that opens the camera app for
 * a photo, as the attach menu's "Camera" does.
 */
@Composable
internal fun CameraButton(onCamera: () -> Unit) {
    FilledTonalIconButton(onClick = onCamera, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painter = painterResource(UiR.drawable.ic_photo_camera_filled),
            contentDescription = stringResource(R.string.attach_camera),
        )
    }
}

/**
 * The icons at the pill's start: the bot button where the chat's bots offer commands or a menu; else, where the chat
 * takes attachments, the attach "+", in the [experimental] look a `primary` circle followed, while the field is empty
 * ([canSend] false) and the chat takes photos, by the camera (`docs/DESIGN_SYSTEM.md` §13.5); else none.
 */
internal fun pillLeadingIcons(
    composer: ComposerUiState.Available,
    experimental: Boolean,
    canSend: Boolean,
    attach: AttachInput,
    onBotMenu: () -> Unit,
): (@Composable RowScope.() -> Unit)? {
    val bot = composer.bot
    val canAttach = composer.attachOptions.isNotEmpty()
    val camera = composer.bar !is ComposerBar.Edit && AttachOption.Camera in composer.attachOptions
    return when {
        bot != null -> {
            { BotMenuButton(bot, onBotMenu) }
        }

        canAttach && experimental -> {
            {
                ExperimentalAttachButton(attach.onAttach)
                if (camera) Appearing(visible = !canSend) { CameraButton(attach.onCamera) }
            }
        }

        canAttach -> {
            { AttachButton(attach.onAttach) }
        }

        else -> {
            null
        }
    }
}
