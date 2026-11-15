package dev.kyralabs.pingogram.feature.chat.ui.reminders

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import dev.kyralabs.pingogram.feature.chat.ui.openTarget
import kotlinx.collections.immutable.ImmutableMap
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The reminders of a conversation's messages that are not done yet, by message id, as their bubbles' metas show them
 * (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29c): when each rings, worded as the Remind me
 * sheet words it ("Sun, 9:00 AM"). [open] opens a message's reminder in the sheet, to change or delete it. Only the
 * bubbles read [chips], so a reminder set or deleted recomposes them alone.
 */
@Stable
internal class ReminderChips(
    val chips: State<ImmutableMap<Long, UiText>>,
    val open: (messageId: Long) -> Unit,
) {
    /** When the reminder of message [messageId] rings, or null where it has none still to come. */
    fun labelFor(messageId: Long): UiText? = chips.value[messageId]
}

/** The conversation's [ReminderChips], provided by its route; none outside it (previews, scheduled messages). */
internal val LocalReminderChips = staticCompositionLocalOf<ReminderChips?> { null }

/**
 * A message's reminder in its bubble's meta, before the time (the owner's board 29c): the alarm symbol and when it
 * rings ([label]) in `labelSmallEmphasized`, `onPrimaryContainer` on a `primaryContainer` pill. A tap opens the
 * reminder ([onClick]), on a 48 dp touch target that moves nothing; screen readers hear it in the bubble's description
 * and open it through the bubble's custom action ([reminderActions]).
 */
@Composable
internal fun ReminderChip(
    label: UiText,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .openTarget(R.string.reminder_chip_open, CircleShape, onClick)
                .background(scheme.primaryContainer)
                .padding(horizontal = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_alarm),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = scheme.onPrimaryContainer,
        )
        Text(
            text = label.resolve(),
            color = scheme.onPrimaryContainer,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmallEmphasized.userContent,
        )
    }
}

/**
 * The bubble's custom action for its reminder chip, as the chip hides inside the bubble's merged semantics: "Change
 * reminder", which opens message [messageId]'s reminder in the sheet; none without a [reminder].
 */
internal fun reminderActions(
    resources: Resources,
    messageId: Long,
    reminder: UiText?,
    onEvent: (ChatEvent) -> Unit,
): List<CustomAccessibilityAction> =
    if (reminder == null) {
        emptyList()
    } else {
        listOf(
            CustomAccessibilityAction(resources.getString(R.string.reminder_chip_open)) {
                onEvent(ReminderEvent.Open(messageId))
                true
            },
        )
    }
