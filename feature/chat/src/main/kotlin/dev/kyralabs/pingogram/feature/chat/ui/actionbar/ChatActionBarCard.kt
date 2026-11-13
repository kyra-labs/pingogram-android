package dev.kyralabs.pingogram.feature.chat.ui.actionbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ActionBarEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The action bar at the top of the conversation's sheet, below its other bars and inset `spacing.small` as they are,
 * growing in and shrinking away as the pinned bar does ([bar] null hides it).
 */
@Composable
internal fun ActionBarSlot(
    bar: ActionBarUi?,
    onEvent: (ChatEvent) -> Unit,
    padding: PaddingValues,
) {
    val motion = MaterialTheme.motionScheme
    val last = remember { mutableStateOf(bar) }
    SideEffect { if (bar != null) last.value = bar }
    val shown = bar ?: last.value
    val direction = LocalLayoutDirection.current
    val small = MaterialTheme.spacing.small
    AnimatedVisibility(
        visible = bar != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        shown?.let {
            ChatActionBarCard(
                bar = it,
                onEvent = onEvent,
                modifier =
                    Modifier.padding(
                        start = padding.calculateStartPadding(direction) + small,
                        top = padding.calculateTopPadding() + small,
                        end = padding.calculateEndPadding(direction) + small,
                    ),
            )
        }
    }
}

/**
 * What TDLib suggests about a chat someone new started (`docs/DESIGN_SYSTEM.md` §6 "Chat action bar"): a card on
 * `surfaceContainerHigh` with `large` corners, its title in `titleSmallEmphasized` (a heading) beside a close
 * `IconButton`, what Telegram tells of the account in `bodySmall` `onSurfaceVariant` ([AccountDetails]), then its
 * actions wrapping as room allows: "Block" (a `TextButton` in `error`), "Report" (a `TextButton`) and "Add contact" or
 * "Share my phone number" (a `FilledTonalButton`), each with `ButtonDefaults.shapes()`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun ChatActionBarCard(
    bar: ActionBarUi,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(start = spacing.medium, bottom = spacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = actionBarTitle(bar),
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                IconButton(onClick = { onEvent(ActionBarEvent.Dismiss) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.action_bar_close))
                }
            }
            Column(Modifier.padding(end = spacing.medium), verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                AccountDetails(bar)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                ) { ActionBarButtons(bar.bar, onEvent) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionBarButtons(
    bar: ChatActionBar,
    onEvent: (ChatEvent) -> Unit,
) {
    val canBlock = bar is ChatActionBar.ReportAddBlock
    val canReport = bar is ChatActionBar.ReportAddBlock || bar is ChatActionBar.ReportSpam
    val canAdd = bar is ChatActionBar.ReportAddBlock || bar is ChatActionBar.AddContact
    if (canBlock) {
        TextButton(
            onClick = { onEvent(ActionBarEvent.Block) },
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.action_bar_block))
        }
    }
    if (canReport) {
        TextButton(onClick = { onEvent(ChatEvent.ReportChat) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.action_bar_report))
        }
    }
    if (canAdd) {
        FilledTonalButton(onClick = { onEvent(ActionBarEvent.AddContact) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.action_bar_add_contact))
        }
    }
    if (bar is ChatActionBar.SharePhoneNumber) {
        FilledTonalButton(onClick = { onEvent(ActionBarEvent.SharePhoneNumber) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.action_bar_share_phone))
        }
    }
}

/** The card's title, naming the other person where it is known. */
@Composable
private fun actionBarTitle(bar: ActionBarUi): String {
    val name = bar.name
    return when (val kind = bar.bar) {
        is ChatActionBar.ReportAddBlock, ChatActionBar.AddContact -> {
            name?.let { stringResource(R.string.action_bar_not_contact_named, it.bidiIsolated()) }
                ?: stringResource(R.string.action_bar_not_contact)
        }

        is ChatActionBar.ReportSpam -> {
            stringResource(R.string.action_bar_spam)
        }

        ChatActionBar.SharePhoneNumber -> {
            name?.let { stringResource(R.string.action_bar_share_phone_title_named, it.bidiIsolated()) }
                ?: stringResource(R.string.action_bar_share_phone_title)
        }

        is ChatActionBar.JoinRequest -> {
            joinRequestText(kind, name)
        }
    }
}

/**
 * The action bar of the conversation on screen, which its route provides with its view model's state, so the
 * conversation's screen takes no parameter more; null where nothing provides one (previews, other conversations).
 */
internal val LocalChatActionBar = compositionLocalOf<(@Composable (PaddingValues) -> Unit)?> { null }
