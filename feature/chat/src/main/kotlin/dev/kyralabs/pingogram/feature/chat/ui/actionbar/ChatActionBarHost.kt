package dev.kyralabs.pingogram.feature.chat.ui.actionbar

import android.text.format.DateFormat
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.AccountInfo
import dev.kyralabs.pingogram.core.domain.model.ChatActionBar
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ActionBarEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.topics.showError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale as JavaLocale

/**
 * Hosts the conversation's action bar ([ChatActionBarViewModel]): provides the card to the screen
 * ([LocalChatActionBar]), its "Report" going to the conversation's [onChatEvent], shows the question before blocking
 * and the snackbars of [viewModel]'s effects in [snackbarHostState], around [content]; nothing while not [shown].
 */
@Composable
internal fun ChatActionBarHost(
    viewModel: ChatActionBarViewModel,
    onChatEvent: (ChatEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    shown: Boolean,
    content: @Composable () -> Unit,
) {
    // Read by the slot alone, so the bar changing recomposes the card, not the conversation.
    val bar = viewModel.bar.collectAsStateWithLifecycle()
    val blockDialog by viewModel.blockDialog.collectAsStateWithLifecycle()
    val currentOnChatEvent by rememberUpdatedState(onChatEvent)
    val onEvent: (ChatEvent) -> Unit =
        remember(viewModel) { { e -> if (e is ActionBarEvent) viewModel.onEvent(e) else currentOnChatEvent(e) } }
    val slot: @Composable (PaddingValues) -> Unit =
        remember(onEvent) { { padding -> ActionBarSlot(bar.value, onEvent, padding) } }
    if (!shown) {
        content()
        return
    }
    ActionBarEffects(viewModel, snackbarHostState)
    CompositionLocalProvider(LocalChatActionBar provides slot, content = content)
    if (blockDialog) BlockAlert(bar.value?.name, onEvent)
}

/** The snackbars after the bar's actions. */
@Composable
private fun ActionBarEffects(
    viewModel: ChatActionBarViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ActionBarEffect.Blocked -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.action_bar_blocked))
                }

                ActionBarEffect.ContactAdded -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.action_bar_contact_added))
                }

                ActionBarEffect.PhoneNumberShared -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.action_bar_phone_shared))
                }

                is ActionBarEffect.Failed -> {
                    snackbarHostState.showError(errorMessage(resources, effect.error, showDetails), effect.error)
                }
            }
        }
    }
}

/**
 * The question before blocking (`docs/DESIGN_SYSTEM.md` §6 "Chat action bar": an [AlertDialog], a destructive
 * confirmation): "Block Ada?", that they can no longer message or call, and "Block" in the `error` color.
 */
@Composable
internal fun BlockAlert(
    name: String?,
    onEvent: (ChatEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(ActionBarEvent.DismissBlock) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(ActionBarEvent.ConfirmBlock) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_bar_block), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ActionBarEvent.DismissBlock) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.action_bar_cancel), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
        icon = { Icon(painter = painterResource(R.drawable.ic_block), contentDescription = null) },
        title = {
            Text(
                name?.let { stringResource(R.string.action_bar_block_title_named, it.bidiIsolated()) }
                    ?: stringResource(R.string.action_bar_block_title),
            )
        },
        text = { Text(stringResource(R.string.action_bar_block_text)) },
    )
}

/**
 * What Telegram tells of the account that wrote first, in `bodySmall` `onSurfaceVariant`, one fact a line: when it
 * signed up, its phone number's country, and its last name and photo changes; nothing for other bars.
 */
@Composable
internal fun AccountDetails(bar: ActionBarUi) {
    val lines =
        when (val kind = bar.bar) {
            is ChatActionBar.ReportAddBlock -> kind.account?.let { accountLines(it) }.orEmpty()
            else -> emptyList()
        }
    if (lines.isEmpty()) return
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.bodySmall.userContent,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun accountLines(account: AccountInfo): List<String> {
    val locale = Locale.current.platformLocale
    return buildList {
        if (account.registrationYear > 0 && account.registrationMonth in 1..MONTHS) {
            val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMy")
            val date = java.time.YearMonth.of(account.registrationYear, account.registrationMonth)
            add(
                stringResource(
                    R.string.action_bar_registered,
                    date.format(DateTimeFormatter.ofPattern(pattern, locale)),
                ),
            )
        }
        if (account.phoneCountryCode.isNotEmpty()) {
            val country = JavaLocale("", account.phoneCountryCode).getDisplayCountry(locale)
            add(stringResource(R.string.action_bar_phone_country, country))
        }
        if (account.lastNameChangeDate > 0) {
            add(stringResource(R.string.action_bar_name_changed, dayOf(account.lastNameChangeDate, locale)))
        }
        if (account.lastPhotoChangeDate > 0) {
            add(stringResource(R.string.action_bar_photo_changed, dayOf(account.lastPhotoChangeDate, locale)))
        }
    }
}

/** The text of a join request's bar: who asked to join which group or channel, and when. */
@Composable
internal fun joinRequestText(
    bar: ChatActionBar.JoinRequest,
    name: String?,
): String {
    val day = dayOf(bar.requestDate, Locale.current.platformLocale)
    val title = bar.title.bidiIsolated()
    return when {
        name != null && bar.isChannel -> {
            stringResource(
                R.string.action_bar_join_channel_named,
                name.bidiIsolated(),
                title,
                day,
            )
        }

        name != null -> {
            stringResource(R.string.action_bar_join_group_named, name.bidiIsolated(), title, day)
        }

        bar.isChannel -> {
            stringResource(R.string.action_bar_join_channel, title, day)
        }

        else -> {
            stringResource(R.string.action_bar_join_group, title, day)
        }
    }
}

private fun dayOf(
    seconds: Long,
    locale: JavaLocale,
): String =
    DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(locale)
        .format(Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault()))

private const val MONTHS = 12
