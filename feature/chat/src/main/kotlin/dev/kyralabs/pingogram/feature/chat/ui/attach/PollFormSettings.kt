package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.scheduledTime
import java.time.Instant

/**
 * The settings (board 5b): anonymous voting (always on in a channel, which says why), multiple answers, changing the
 * vote, hiding the results until the poll closes, shuffling the options, members adding options (public polls outside
 * channels) and quiz mode, then when the poll closes ([closesItem]). A quiz's votes are final and it takes as many
 * answers as it has correct ones, so its rows for those are disabled.
 */
internal fun LazyListScope.pollSettingItems(
    sheet: PollSheet,
    editor: PollEditor,
    actions: PollFormActions,
    now: () -> Instant,
) {
    item(key = SETTINGS) { FormItem { FormSectionHeader(stringResource(R.string.poll_settings)) }}
    item(key = ANONYMOUS) { FormItem {
        FormSwitchRow(
            title = stringResource(R.string.poll_anonymous),
            checked = sheet.isChannel || editor.isAnonymous,
            onCheckedChange = { editor.isAnonymous = it },
            enabled = !sheet.isChannel,
            note = stringResource(R.string.poll_anonymous_channel).takeIf { sheet.isChannel },
        )
    }}
    item(key = MULTIPLE) { FormItem {
        FormSwitchRow(
            title = stringResource(R.string.poll_multiple),
            checked = !editor.isQuiz && editor.allowsMultipleAnswers,
            onCheckedChange = { editor.allowsMultipleAnswers = it },
            enabled = !editor.isQuiz,
        )
    }}
    item(key = REVOTING) { FormItem {
        FormSwitchRow(
            title = stringResource(R.string.poll_revoting),
            checked = !editor.isQuiz && editor.allowsRevoting,
            onCheckedChange = { editor.allowsRevoting = it },
            enabled = !editor.isQuiz,
        )
    }}
    item(key = HIDE_RESULTS) { FormItem {
        FormSwitchRow(
            title = stringResource(R.string.poll_hide_results),
            checked = editor.hidesResults,
            onCheckedChange = { editor.hidesResults = it },
            note = stringResource(R.string.poll_hide_results_note),
        )
    }}
    answerSettingItems(sheet, editor)
    pollVotersItems(sheet, editor)
    closesItem(editor, actions, now)
}

/** Shuffling the options, members adding options and quiz mode, the settings about the answers. */
private fun LazyListScope.answerSettingItems(
    sheet: PollSheet,
    editor: PollEditor,
) {
    item(key = SHUFFLE) { FormItem {
        FormSwitchRow(
            title = stringResource(R.string.poll_shuffle),
            checked = editor.shufflesOptions,
            onCheckedChange = { editor.shufflesOptions = it },
        )
    }}
    item(key = ADDING) { FormItem {
        val allowed = editor.canAllowAddingOptions(sheet.isChannel)
        FormSwitchRow(
            title = stringResource(R.string.poll_adding_options),
            checked = allowed && editor.allowsAddingOptions,
            onCheckedChange = { editor.allowsAddingOptions = it },
            enabled = allowed,
            note = stringResource(R.string.poll_adding_options_note).takeUnless { allowed },
        )
    }}
    item(key = QUIZ) { FormItem {
        FormSwitchRow(
            title = stringResource(R.string.poll_quiz),
            checked = editor.isQuiz,
            onCheckedChange = { editor.isQuiz = it },
            // A bot's keyboard button may ask for a quiz, or for a regular poll.
            enabled = editor.requiredQuiz == null,
            note =
                stringResource(
                    when (editor.requiredQuiz) {
                        true -> R.string.poll_quiz_required
                        false -> R.string.poll_regular_required
                        null -> R.string.poll_quiz_note
                    },
                ),
        )
    }}
}

/**
 * "Closes" with the moment the poll will close at its end, then Never, 1 h, 1 day and Custom as connected
 * [ToggleButton]s (`SegmentTick`, one radio group), those beyond Telegram's `poll_open_period_max` disabled; Custom
 * opens the date and time pickers. Left out where TDLib does not say how long a poll may stay open.
 */
private fun LazyListScope.closesItem(
    editor: PollEditor,
    actions: PollFormActions,
    now: () -> Instant,
) {
    if (editor.limits.maxOpenPeriod <= 0) return
    item(key = CLOSES) { FormItem {
        val context = LocalContext.current
        val closesAt =
            when (editor.closing) {
                PollClosing.Never -> null
                PollClosing.Custom -> editor.customCloseDate
                else -> now().epochSecond + editor.closing.seconds
            }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
            FormSectionHeader(
                title = stringResource(R.string.poll_closes),
                trailing = closesAt?.let { context.scheduledTime(it) },
            )
            ClosingButtons(editor, actions)
        }
    }}
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class) // ToggleButton's connected shapes
@Composable
private fun ClosingButtons(
    editor: PollEditor,
    actions: PollFormActions,
) {
    val haptics = LocalHapticFeedback.current
    val choices = PollClosing.entries
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        choices.forEachIndexed { index, choice ->
            val checked = editor.closing == choice
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    if (choice == PollClosing.Custom) actions.onPickCloseDate() else editor.closing = choice
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                enabled = editor.canClose(choice),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                Text(
                    text = stringResource(choice.label),
                    maxLines = 1,
                    style =
                        with(MaterialTheme.typography) { if (checked) labelLargeEmphasized else labelLarge },
                )
            }
        }
    }
}

private val PollClosing.label: Int
    get() =
        when (this) {
            PollClosing.Never -> R.string.poll_closes_never
            PollClosing.Hour -> R.string.poll_closes_hour
            PollClosing.Day -> R.string.poll_closes_day
            PollClosing.Custom -> R.string.poll_closes_custom
        }

/**
 * A section's title in `titleSmallEmphasized` and `primary`, as in settings, with [trailing] at its end in
 * `labelLarge` `onSurfaceVariant` ("4 of 12") and an optional [note] below.
 */
@Composable
internal fun FormSectionHeader(
    title: String,
    note: String? = null,
    trailing: String? = null,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(top = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            trailing?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                )
            }
        }
        // A note that comes and goes with the form's choices grows in and shrinks away.
        val shownNote = remember { mutableStateOf(note) }
        if (note != null) shownNote.value = note
        GrowingVisibility(visible = note != null) {
            shownNote.value?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * A setting as a list item with a switch ([CheckSwitch]), the whole row toggling it with a toggle haptic, on the
 * sheet's color.
 */
@Composable
internal fun FormSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    note: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        modifier =
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.performToggle(it)
                    onCheckedChange(it)
                },
            ),
        enabled = enabled,
        trailingContent = { CheckSwitch(checked = checked, enabled = enabled) },
        supportingContent = note?.let { { Text(it) } },
        colors =
            ListItemDefaults.colors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
    ) {
        Text(title)
    }
}

private const val SETTINGS = "settings"
private const val ANONYMOUS = "anonymous"
private const val MULTIPLE = "multiple"
private const val REVOTING = "revoting"
private const val HIDE_RESULTS = "hide_results"
private const val SHUFFLE = "shuffle"
private const val ADDING = "adding"
private const val QUIZ = "quiz"
private const val CLOSES = "closes"
