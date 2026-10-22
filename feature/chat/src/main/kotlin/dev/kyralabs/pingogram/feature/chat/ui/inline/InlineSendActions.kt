package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.Minithumbnail
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendOptionsMenu
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.MinithumbnailPlaceholder
import dev.kyralabs.pingogram.feature.chat.ui.content.StickerImage
import dev.kyralabs.pingogram.feature.chat.ui.content.TdlibImage
import dev.kyralabs.pingogram.feature.chat.ui.content.asPicture
import dev.kyralabs.pingogram.feature.chat.ui.content.fadingOutAs
import dev.kyralabs.pingogram.feature.chat.ui.content.fitInSquare
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDurationText
import dev.kyralabs.pingogram.feature.chat.ui.content.rememberImageAlpha
import dev.kyralabs.pingogram.feature.chat.ui.content.selectPhotoSize
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * How a result is sent: a tap sends it now ([onSend]); a long press (a `LongPress` haptic) opens the composer's send
 * menu, "Send without sound" and, where [canSchedule], "Schedule message" ([onSchedule], which asks for the time).
 * Screen readers get both as custom actions.
 */
@Immutable
internal class InlineSendActions(
    val canSchedule: Boolean,
    val onSend: (InlineResultEntry, silent: Boolean) -> Unit,
    val onSchedule: (InlineResultEntry) -> Unit,
)

/**
 * A result's touch target: a tap sends it (a `Confirm` haptic), a long press opens the send menu; screen readers hear
 * it as one button, "GIF, Cat", with the send menu's entries as custom actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InlineResultTarget(
    entry: InlineResultEntry,
    actions: InlineSendActions,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    var menuOpen by rememberSaveable(entry.key) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val kind = stringResource(entry.result.kindLabel())
    val title = entry.result.spokenTitle()
    val sendLabel = stringResource(R.string.inline_result_send)
    val silentLabel = stringResource(R.string.composer_send_silently)
    val scheduleLabel = stringResource(R.string.composer_schedule)
    val send = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        actions.onSend(entry, false)
    }
    Box(
        modifier
            .combinedClickable(
                onClickLabel = sendLabel,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
                onClick = send,
            ).clearAndSetSemantics {
                role = Role.Button
                contentDescription = listOf(kind, title).filter { it.isNotBlank() }.joinToString(", ")
                onClick(sendLabel) {
                    send()
                    true
                }
                customActions =
                    listOfNotNull(
                        CustomAccessibilityAction(silentLabel) {
                            actions.onSend(entry, true)
                            true
                        },
                        if (actions.canSchedule) {
                            CustomAccessibilityAction(scheduleLabel) {
                                actions.onSchedule(entry)
                                true
                            }
                        } else {
                            null
                        },
                    )
            },
    ) {
        content()
        SendOptionsMenu(
            expanded = menuOpen,
            canSchedule = actions.canSchedule,
            onDismiss = { menuOpen = false },
            onSendSilently = { actions.onSend(entry, true) },
            onSchedule = { actions.onSchedule(entry) },
        )
    }
}
