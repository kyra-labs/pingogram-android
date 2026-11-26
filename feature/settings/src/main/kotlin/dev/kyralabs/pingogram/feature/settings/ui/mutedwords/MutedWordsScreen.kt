package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import dev.kyralabs.pingogram.feature.settings.ui.keywords.AddWordRow
import dev.kyralabs.pingogram.feature.settings.ui.keywords.KeywordChip
import kotlinx.coroutines.launch

/**
 * Muted words' page: hoists [MutedWordsViewModel], empties the field once a word is added, and shows why a change was
 * not saved in a snackbar.
 */
@Composable
internal fun MutedWordsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MutedWordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val holders = remember { MutedWordsHolders() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MutedWordsEffect.Added -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    holders.word.clearText()
                }

                is MutedWordsEffect.ShowError -> {
                    launch { holders.snackbar.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    MutedWordsScreen(state, viewModel::onEvent, onBack, modifier, holders)
}

/** What the route keeps for the page: the field's text ([word]) and the snackbars ([snackbar]). */
internal class MutedWordsHolders(
    val word: TextFieldState = TextFieldState(),
    val snackbar: SnackbarHostState = SnackbarHostState(),
)

/**
 * Muted words (`REQUIREMENTS.md` FR-X21, `docs/DESIGN_SYSTEM.md` §6 "Muted words"), a settings page: the switch, "Add
 * word" (the field of keyword alerts, disabled once there are 100 words), the words as chips a tap removes, and how
 * matching works. Words can be added while the switch is off. Nothing shows until the settings are read.
 */
@Composable
internal fun MutedWordsScreen(
    state: MutedWordsUiState,
    onEvent: (MutedWordsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    holders: MutedWordsHolders = remember { MutedWordsHolders() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_muted_words),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = holders.snackbar,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        val settings = state.settings ?: return@SettingsPageScaffold
        SettingsGroup {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_muted_words_switch),
                    summary = stringResource(R.string.settings_muted_words_switch_summary),
                    checked = settings.enabled,
                    shapes = shapes,
                    onCheckedChange = { onEvent(MutedWordsEvent.SetEnabled(it)) },
                )
            }
        }
        AddWordRow(holders.word, state.canAdd, MutedWordSettings.MAX_WORDS, MutedWordSettings.MAX_WORD_LENGTH) {
            onEvent(MutedWordsEvent.Add(it))
        }
        MutedWordChips(settings) { onEvent(MutedWordsEvent.Remove(it)) }
        SettingsFooter(stringResource(R.string.settings_muted_words_matching))
    }
}

/** The words as the chips of keyword alerts, without counts; a tap removes one. Nothing without words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MutedWordChips(
    settings: MutedWordSettings,
    onRemove: (String) -> Unit,
) {
    if (settings.words.isEmpty()) return
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.medium)
                .semantics { collectionInfo = CollectionInfo(rowCount = settings.words.size, columnCount = 1) },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        settings.words.forEach { word -> KeywordChip(word, matches = 0) { onRemove(word) } }
    }
}
