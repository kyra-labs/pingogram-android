package dev.kyralabs.pingogram.feature.settings.ui.keywords

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.kyralabs.pingogram.core.domain.keyword.KeywordMatcher
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.domain.model.KeywordScope
import dev.kyralabs.pingogram.core.notifications.PingogramChannel
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PickerDone
import dev.kyralabs.pingogram.feature.settings.ui.PickerTitle
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.openSystemSettings
import dev.kyralabs.pingogram.feature.settings.ui.pickerRowLook
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the keyword alerts page (KeywordAlertsScreen), board 30.

/**
 * A word: its text (user content, one line), how many unread messages matched it this week ([matches], left out at 0)
 * and a close symbol; a tap removes it ("launch, 4 matches, remove").
 */
@Composable
internal fun KeywordChip(
    word: String,
    matches: Int,
    onRemove: () -> Unit,
) {
    val isolated = word.bidiIsolated()
    val label =
        if (matches > 0) {
            pluralStringResource(R.plurals.settings_keyword_alerts_remove_matches, matches, isolated, matches)
        } else {
            stringResource(R.string.settings_keyword_alerts_remove, isolated)
        }
    val maxWidth = MaterialTheme.sizes.mediaMinSize * LocalDensity.current.fontScale.coerceAtLeast(1f)
    InputChip(
        selected = false,
        onClick = onRemove,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    word,
                    modifier = Modifier.widthIn(max = maxWidth),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.userContent,
                )
                if (matches > 0) {
                    Text(
                        NumberFormat.getIntegerInstance().format(matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        shapes = InputChipDefaults.shapes(),
        modifier =
            Modifier.semantics {
                contentDescription = label
                onClick(label = label) {
                    onRemove()
                    true
                }
            },
        trailingIcon = {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

/**
 * No words yet: the bell on a `Cookie12Sided` badge, "No alert words yet", that matching runs on this phone, and
 * one-tap suggestions (the account's first name and username, then two common words) that are not kept yet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KeywordsEmpty(
    state: KeywordAlertsUiState,
    onEvent: (KeywordAlertsEvent) -> Unit,
) {
    val kept =
        state.settings
            ?.words
            .orEmpty()
            .map(KeywordMatcher::keyOf)
            .toSet()
    val common =
        listOf(
            stringResource(R.string.settings_keyword_alerts_suggest_urgent),
            stringResource(R.string.settings_keyword_alerts_suggest_meeting),
        ).filter { KeywordMatcher.keyOf(it) !in kept }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        EmptyHeading()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.CenterHorizontally),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_keyword_alerts_try),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            state.suggestions.forEach { suggestion ->
                val label =
                    when (suggestion.kind) {
                        KeywordSuggestion.Kind.Name -> R.string.settings_keyword_alerts_add_name
                        KeywordSuggestion.Kind.Username -> R.string.settings_keyword_alerts_add_username
                    }
                SuggestionChip(suggestion.word, stringResource(label)) { onEvent(KeywordAlertsEvent.Add(it)) }
            }
            common.forEach { word ->
                SuggestionChip(word, stringResource(R.string.settings_keyword_alerts_add_named, word.bidiIsolated())) {
                    onEvent(KeywordAlertsEvent.Add(it))
                }
            }
        }
    }
}

/**
 * The bell on a `Cookie9Sided` in `primary` inside a `Cookie12Sided` in `primaryContainer`, a "launch" tag on its edge
 * (decorative), over "No alert words yet" (a heading, a polite live region) and what alerts do.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyHeading() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier
                .size(MaterialTheme.sizes.avatarProfile)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(MaterialTheme.sizes.headlineBadge)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(UiR.drawable.ic_notifications), contentDescription = null, tint = colors.onPrimary)
            }
        }
        Text(
            stringResource(R.string.settings_keyword_alerts_sample),
            modifier =
                Modifier
                    .background(colors.tertiaryContainer, CircleShape)
                    .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
            color = colors.onTertiaryContainer,
            style = MaterialTheme.typography.labelMediumEmphasized,
        )
    }
    Column(
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            stringResource(R.string.settings_keyword_alerts_empty_title),
            style = MaterialTheme.typography.titleMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.settings_keyword_alerts_empty_body),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** A suggestion: [word] after an add symbol, [label] for screen readers ("Add your name"). */
@Composable
private fun SuggestionChip(
    word: String,
    label: String,
    onAdd: (String) -> Unit,
) {
    AssistChip(
        onClick = { onAdd(word) },
        label = { Text(word, style = LocalTextStyle.current.userContent, maxLines = 1) },
        modifier = Modifier.semantics { contentDescription = label },
        leadingIcon = {
            Icon(
                painterResource(UiR.drawable.ic_add),
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/**
 * "Where to look": All chats, Chosen folders (the folders' names, and "Edit" while chosen; disabled without folders)
 * and Muted chats only, one radio group.
 */
@Composable
internal fun KeywordWhereGroup(
    state: KeywordAlertsUiState,
    settings: KeywordAlertSettings,
    onEvent: (KeywordAlertsEvent) -> Unit,
) {
    val where = settings.where
    val chosen =
        (where as? KeywordScope.Folders)?.folderIds?.let { ids -> state.folders.filter { it.id in ids } }.orEmpty()
    SettingsGroup(
        title = stringResource(R.string.settings_keyword_alerts_where),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        item { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_keyword_alerts_where_all),
                selected = where == KeywordScope.AllChats,
                enabled = true,
                shapes = shapes,
                onClick = { onEvent(KeywordAlertsEvent.Where(KeywordScope.AllChats)) },
            )
        }
        item { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_keyword_alerts_where_folders),
                selected = where is KeywordScope.Folders,
                enabled = state.folders.isNotEmpty(),
                shapes = shapes,
                onClick = { onEvent(KeywordAlertsEvent.ChooseFolders) },
                summary =
                    when {
                        state.folders.isEmpty() -> stringResource(R.string.settings_keyword_alerts_no_folders)
                        chosen.isNotEmpty() -> chosen.joinToString(", ") { it.title }
                        else -> stringResource(R.string.settings_keyword_alerts_where_folders_summary)
                    },
                trailingContent =
                    if (where is KeywordScope.Folders) {
                        {
                            TextButton(onClick = { onEvent(KeywordAlertsEvent.ChooseFolders) }) {
                                Text(stringResource(R.string.settings_keyword_alerts_edit_folders))
                            }
                        }
                    } else {
                        null
                    },
            )
        }
        item { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_keyword_alerts_where_muted),
                selected = where == KeywordScope.MutedChats,
                enabled = true,
                shapes = shapes,
                onClick = { onEvent(KeywordAlertsEvent.Where(KeywordScope.MutedChats)) },
                summary = stringResource(R.string.settings_keyword_alerts_where_muted_summary),
            )
        }
    }
}

/**
 * "Alert": whether muted chats alert too, the chat list's tab, and "Sound and vibration", which opens the channel's
 * settings in Android ("Turned off…" in `error` while Android blocks it).
 */
@Composable
internal fun KeywordAlertGroup(
    settings: KeywordAlertSettings,
    onEvent: (KeywordAlertsEvent) -> Unit,
) {
    val context = LocalContext.current
    val blocked = rememberChannelBlocked(PingogramChannel.KeywordAlerts.id)
    SettingsGroup(
        title = stringResource(R.string.settings_keyword_alerts_alert),
        footer = stringResource(R.string.settings_keyword_alerts_muted_note),
    ) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_keyword_alerts_when_muted),
                summary = stringResource(R.string.settings_keyword_alerts_when_muted_summary),
                checked = settings.alertWhenMuted,
                shapes = shapes,
                onCheckedChange = { onEvent(KeywordAlertsEvent.AlertWhenMuted(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_keyword_alerts_in_chat_list),
                summary = stringResource(R.string.settings_keyword_alerts_in_chat_list_summary),
                checked = settings.showInChatList,
                shapes = shapes,
                onCheckedChange = { onEvent(KeywordAlertsEvent.ShowInChatList(it)) },
            )
        }
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_notifications_sound),
                shapes = shapes,
                supportingContent = {
                    if (blocked) {
                        Text(
                            stringResource(R.string.settings_notifications_sound_blocked),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text(stringResource(R.string.settings_notifications_sound_summary))
                    }
                },
                trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                onClick = {
                    context.openSystemSettings(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, PingogramChannel.KeywordAlerts.id),
                    )
                },
            )
        }
    }
}

/** The folders to look in: a sheet of checkbox rows and "Done", which keeps them; dismissing changes nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeywordFoldersSheet(
    state: KeywordAlertsUiState,
    chosen: Set<Int>,
    onEvent: (KeywordAlertsEvent) -> Unit,
) {
    PingogramBottomSheet(
        onDismissRequest = { onEvent(KeywordAlertsEvent.CloseFolders) },
        sheetState =
            rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        KeywordFoldersContent(state, chosen, onEvent)
    }
}

@Composable
internal fun KeywordFoldersContent(
    state: KeywordAlertsUiState,
    chosen: Set<Int>,
    onEvent: (KeywordAlertsEvent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = TITLE) { PickerTitle(stringResource(R.string.settings_keyword_alerts_folders_title)) }
        items(state.folders, key = { it.id }) { folder ->
            val checked = folder.id in chosen
            val (shape, color) = pickerRowLook(checked)
            ListItem(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.small)
                        .clip(shape)
                        .background(color)
                        .toggleable(
                            checked,
                            role = Role.Checkbox,
                        ) { onEvent(KeywordAlertsEvent.ToggleFolder(folder.id)) },
                leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) {
                Text(folder.title, style = LocalTextStyle.current.userContent)
            }
        }
        item(key = DONE) {
            PickerDone(stringResource(R.string.settings_chat_folder_picker_done)) {
                onEvent(KeywordAlertsEvent.FoldersChosen)
            }
        }
    }
}

private const val TITLE = "title"
private const val DONE = "done"

/** Whether Android blocks notification channel [id], read again whenever the page resumes (the user may change it). */
@Composable
private fun rememberChannelBlocked(id: String): Boolean {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(context, id) {
        blocked =
            NotificationManagerCompat.from(context).getNotificationChannelCompat(id)?.importance ==
            NotificationManagerCompat.IMPORTANCE_NONE
        onPauseOrDispose {}
    }
    return blocked
}
