package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.SessionDevice
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R

// The rows and the confirmation of the Active sessions page.

/**
 * A session, a row of its group ([shapes]): its device's icon, its device model, app and system, and where and when it
 * was last active. With [onClick] (other sessions) a tap asks to terminate it; while [isBusy], it waits with progress.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SessionRow(
    session: SessionItem,
    isBusy: Boolean,
    shapes: ListItemShapes,
    onClick: (() -> Unit)? = null,
    overline: String? = null,
    colors: ListItemColors = settingsRowColors(),
    isOnTestServers: Boolean = false,
) {
    val busy = stringResource(R.string.settings_session_busy)
    val leading: @Composable () -> Unit = { SettingsItemIcon(session.device.iconRes()) }
    val trailing: (@Composable () -> Unit)? =
        if (isBusy) ({ LoadingIndicator(Modifier.semantics { contentDescription = busy }) }) else null
    val overlineContent: (@Composable () -> Unit)? = overline?.let { { Text(it) } }
    val supporting: @Composable () -> Unit = {
        Column {
            Text(text = session.app.resolve(), style = LocalTextStyle.current.userContent)
            Text(session.activity.resolve())
            if (session.isPasswordPending) {
                Text(
                    text = stringResource(R.string.settings_session_password_pending),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (isOnTestServers) TestServerLabel(Modifier.padding(top = MaterialTheme.spacing.small))
        }
    }
    if (onClick != null) {
        val terminate = stringResource(R.string.settings_session_terminate)
        SegmentedListItem(
            onClick = onClick,
            // Names what a tap does; the list item's click supplies the action.
            modifier = Modifier.semantics { onClick(label = terminate, action = null) },
            shapes = shapes,
            enabled = !isBusy,
            leadingContent = leading,
            trailingContent = trailing,
            overlineContent = overlineContent,
            supportingContent = supporting,
            colors = colors,
        ) {
            Text(text = session.title.resolve(), style = LocalTextStyle.current.userContent)
        }
    } else {
        SegmentedListItem(
            shapes = shapes,
            leadingContent = leading,
            trailingContent = trailing,
            overlineContent = overlineContent,
            supportingContent = supporting,
            colors = colors,
        ) {
            Text(text = session.title.resolve(), style = LocalTextStyle.current.userContent)
        }
    }
}

/**
 * A new login, a row of its group ([shapes]): the session, labelled as one, then "No, terminate" and "Yes, it's me",
 * in one segment.
 */
@Composable
internal fun NewLogin(
    session: SessionItem,
    isBusy: Boolean,
    shapes: ListItemShapes,
    onConfirm: () -> Unit,
    onTerminate: () -> Unit,
) {
    SettingsSegment(shapes) {
        SessionRow(
            session = session,
            isBusy = isBusy,
            shapes = ListItemDefaults.shapes(),
            overline = stringResource(R.string.settings_session_new_login),
            colors = nestedItemColors(),
        )
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.medium,
                    ),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            TextButton(
                onClick = onTerminate,
                shapes = ButtonDefaults.shapes(),
                enabled = !isBusy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.settings_session_not_me))
            }
            FilledTonalButton(onClick = onConfirm, shapes = ButtonDefaults.shapes(), enabled = !isBusy) {
                Text(stringResource(R.string.settings_session_confirm))
            }
        }
    }
}

/** "Terminate all other sessions": destructive, in the error role; disabled with progress while it runs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TerminateAllItem(
    isTerminating: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val terminating = stringResource(R.string.settings_terminating_all)
    SettingsRow(
        title = stringResource(R.string.settings_terminate_all),
        shapes = shapes,
        icon = R.drawable.ic_logout,
        supportingContent = { Text(stringResource(R.string.settings_terminate_all_summary)) },
        trailingContent =
            if (isTerminating) {
                { LoadingIndicator(Modifier.semantics { contentDescription = terminating }) }
            } else {
                null
            },
        onClick = onClick,
        enabled = !isTerminating,
        colors = destructiveItemColors(),
        iconTone = SettingsIconTone.Error,
    )
}

/** The destructive confirmation of a termination ([DestructiveDialog]). */
@Composable
internal fun TerminationDialog(
    dialog: TerminateDialog,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        is TerminateDialog.Session -> {
            DestructiveDialog(
                icon = R.drawable.ic_devices,
                texts =
                    DestructiveDialogTexts(
                        title = stringResource(R.string.settings_terminate_session_dialog_title),
                        text =
                            stringResource(
                                R.string.settings_terminate_session_dialog_text,
                                dialog.title.bidiIsolated().resolve(),
                            ).bidiIsolated(),
                        confirm = stringResource(R.string.settings_terminate_session_dialog_confirm),
                    ),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }

        TerminateDialog.AllOthers -> {
            DestructiveDialog(
                icon = R.drawable.ic_devices,
                texts =
                    DestructiveDialogTexts(
                        title = stringResource(R.string.settings_terminate_all_dialog_title),
                        text = stringResource(R.string.settings_terminate_all_dialog_text),
                        confirm = stringResource(R.string.settings_terminate_all_dialog_confirm),
                    ),
                onConfirm = onConfirm,
                onDismiss = onDismiss,
            )
        }
    }
}

/** How long sessions may stay without activity: in years or months where that is exact, else in days. */
@Composable
internal fun ttlText(days: Int): String =
    when {
        days > 0 && days % DAYS_PER_YEAR == 0 -> {
            val years = days / DAYS_PER_YEAR
            pluralStringResource(R.plurals.settings_sessions_ttl_years, years, years)
        }

        days > 0 && days % DAYS_PER_MONTH == 0 -> {
            val months = days / DAYS_PER_MONTH
            pluralStringResource(R.plurals.settings_sessions_ttl_months, months, months)
        }

        else -> {
            pluralStringResource(R.plurals.settings_sessions_ttl_days, days, days)
        }
    }

@DrawableRes
private fun SessionDevice.iconRes(): Int =
    when (this) {
        SessionDevice.Phone -> R.drawable.ic_smartphone
        SessionDevice.Tablet -> R.drawable.ic_tablet
        SessionDevice.Desktop -> R.drawable.ic_computer
        SessionDevice.Browser -> R.drawable.ic_language
        SessionDevice.GameConsole -> R.drawable.ic_sports_esports
        SessionDevice.Unknown -> R.drawable.ic_devices
    }

// Telegram's choices for the inactive session TTL are a week, months and a year (1, 3, 6 or 12 months of 30 days).
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
