package dev.kyralabs.pingogram.feature.settings.ui.keywords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.KeywordAlertSettings
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import kotlinx.coroutines.launch

/**
 * Keyword alerts' page: hoists [KeywordAlertsViewModel], empties the field once a word is added, and shows why a
 * change was not saved in a snackbar.
 */
@Composable
internal fun KeywordAlertsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KeywordAlertsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val word = rememberTextFieldState()
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                KeywordAlertsEffect.Added -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    word.clearText()
                }

                is KeywordAlertsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    KeywordAlertsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState, word)
    state.choosingFolders?.let { KeywordFoldersSheet(state, it, viewModel::onEvent) }
}

/**
 * Keyword alerts (`docs/DESIGN_SYSTEM.md` §13.13, board 30), a settings page: what they do, "Add word" (a filled field
 * and "Add" at its end, disabled until something is typed or once there are 20 words), the words as chips a tap
 * removes, each with its matches this week, then where to look and the switches; or, with none, a short explanation and
 * one-tap suggestions alone. Nothing shows until the
 * settings are read, a matter of milliseconds.
 */
@Suppress("LongParameterList") // The page's state and events, back, and the parts the route keeps for the field.
@Composable
internal fun KeywordAlertsScreen(
    state: KeywordAlertsUiState,
    onEvent: (KeywordAlertsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    word: TextFieldState = rememberTextFieldState(),
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_keyword_alerts),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        val settings = state.settings ?: return@SettingsPageScaffold
        SettingsFooter(
            stringResource(R.string.settings_keyword_alerts_intro),
            Modifier.padding(bottom = MaterialTheme.spacing.small),
        )
        AddWordRow(word, state.canAdd) { onEvent(KeywordAlertsEvent.Add(it)) }
        // The empty page shows only the field and the suggestions (board 30b): the settings come with the first word.
        if (settings.words.isEmpty()) {
            KeywordsEmpty(state, onEvent)
        } else {
            KeywordWords(state, settings, onEvent)
            KeywordWhereGroup(state, settings, onEvent)
            KeywordAlertGroup(settings, onEvent)
        }
    }
}

/** "Add word": the field (one line, user content) with "Add" at its end, which the keyboard's Done also presses. */
@Composable
private fun AddWordRow(
    word: TextFieldState,
    canAdd: Boolean,
    onAdd: (String) -> Unit,
) {
    val typed = word.text.isNotBlank()
    val add = { if (typed && canAdd) onAdd(word.text.toString()) }
    TextField(
        state = word,
        modifier = Modifier.fillMaxWidth(),
        enabled = canAdd,
        label = { Text(stringResource(R.string.settings_keyword_alerts_add_word)) },
        placeholder = { Text(stringResource(R.string.settings_keyword_alerts_add_word_hint)) },
        trailingIcon = {
            TextButton(
                onClick = add,
                enabled = typed && canAdd,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(end = MaterialTheme.spacing.extraSmall),
            ) {
                Text(
                    stringResource(R.string.settings_keyword_alerts_add),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        supportingText =
            if (canAdd) {
                null
            } else {
                {
                    val most = KeywordAlertSettings.MAX_WORDS
                    Text(pluralStringResource(R.plurals.settings_keyword_alerts_full, most, most))
                }
            },
        textStyle = LocalTextStyle.current.userContent,
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = { if (length > KeywordAlertSettings.MAX_WORD_LENGTH) revertAllChanges() },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        onKeyboardAction = { add() },
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** The words as chips that a tap removes, each with its matches this week, then how matching works. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordWords(
    state: KeywordAlertsUiState,
    settings: KeywordAlertSettings,
    onEvent: (KeywordAlertsEvent) -> Unit,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.medium)
                .semantics { collectionInfo = CollectionInfo(rowCount = settings.words.size, columnCount = 1) },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        settings.words.forEach { word ->
            KeywordChip(word, state.matchCounts[word] ?: 0) { onEvent(KeywordAlertsEvent.Remove(word)) }
        }
    }
    SettingsFooter(stringResource(R.string.settings_keyword_alerts_matching))
}
