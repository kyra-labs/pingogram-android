package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The page of privacy [setting]: hoists its [PrivacySettingViewModel] and shows in snackbars the choices that could not
 * be saved. [onBack] leaves the page.
 */
@Composable
internal fun PrivacySettingRoute(
    setting: PrivacySetting,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacySettingViewModel =
        hiltViewModel<PrivacySettingViewModel, PrivacySettingViewModel.Factory>(key = "privacy-$setting") {
            it.create(setting)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is PrivacySettingEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    PrivacySettingScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * A privacy setting (`REQUIREMENTS.md` FR-S1): who can see or do it, as three [RadioItem]s (Everybody, My contacts,
 * Nobody) under the setting's question, then its exceptions for that choice, counted, as text: editing them comes
 * later, and changing the choice keeps them. The choice being saved shows selected with progress, and the choices wait
 * meanwhile. Without Telegram Premium, voice and video messages offer "Everybody" only, and say why.
 */
@Composable
internal fun PrivacySettingScreen(
    state: PrivacySettingUiState,
    onEvent: (PrivacySettingEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(state.setting.titleRes()),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val rules = state.shownRules
        when (val loaded = state.rules) {
            PrivacyRulesState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_privacy_setting_loading))
            }

            is PrivacyRulesState.Failed -> {
                SettingsError(loaded.error, onRetry = { onEvent(PrivacySettingEvent.Retry) })
            }

            is PrivacyRulesState.Loaded -> {
                if (rules != null) {
                    ChoicesSection(state, rules.choice, onSelect = { onEvent(PrivacySettingEvent.Select(it)) })
                    if (rules.hasExceptions) ExceptionsSection(rules)
                }
            }
        }
    }
}

/** The setting's question over the three main choices, a radio row each. */
@Composable
private fun ChoicesSection(
    state: PrivacySettingUiState,
    selected: PrivacyChoice,
    onSelect: (PrivacyChoice) -> Unit,
) {
    SettingsGroup(
        title = stringResource(state.setting.questionRes()),
        footer = if (state.premiumRequired) stringResource(R.string.settings_privacy_premium_note) else null,
        rowsModifier = Modifier.selectableGroup(),
    ) {
        PrivacyChoices.forEach { choice ->
            item(key = choice) { shapes ->
                val allowed = !state.premiumRequired || choice == PrivacyChoice.Everybody
                RadioItem(
                    label = stringResource(choice.labelRes()),
                    selected = choice == selected,
                    enabled = allowed && state.savingChoice == null,
                    shapes = shapes,
                    onClick = { onSelect(choice) },
                    trailingContent = if (choice == state.savingChoice) ({ SavingIndicator() }) else null,
                )
            }
        }
    }
}

/** Progress on the choice being saved, as "Clear cache" shows it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SavingIndicator() {
    val saving = stringResource(R.string.settings_privacy_setting_saving)
    LoadingIndicator(Modifier.semantics { contentDescription = saving })
}

/** Who is always and never allowed despite the choice, counted, and that changing the choice keeps them. */
@Composable
private fun ExceptionsSection(rules: PrivacyRules) {
    SettingsGroup(
        title = stringResource(R.string.settings_section_privacy_exceptions),
        footer = stringResource(R.string.settings_privacy_exceptions_note),
    ) {
        if (rules.hasAlwaysAllowed) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_always_allowed),
                    shapes = shapes,
                    supportingContent = { Text(alwaysAllowedText(rules.exceptions)) },
                )
            }
        }
        if (rules.hasNeverAllowed) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_never_allowed),
                    shapes = shapes,
                    supportingContent = { Text(neverAllowedText(rules.exceptions)) },
                )
            }
        }
    }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenPreview() {
    PingogramPreview { PrivacySettingScreen(state = SettingsPreviewData.privacySetting, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenSavingPreview() {
    PingogramPreview {
        PrivacySettingScreen(
            state = SettingsPreviewData.privacySetting.copy(savingChoice = PrivacyChoice.Nobody),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenPremiumRequiredPreview() {
    PingogramPreview { PrivacySettingScreen(state = SettingsPreviewData.voiceMessages, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenLoadingPreview() {
    PingogramPreview {
        PrivacySettingScreen(state = PrivacySettingUiState(PrivacySetting.Calls), onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun PrivacySettingScreenErrorPreview() {
    PingogramPreview {
        PrivacySettingScreen(
            state = PrivacySettingUiState(PrivacySetting.Calls, PrivacyRulesState.Failed(DomainError.Network)),
            onEvent = {},
            onBack = {},
        )
    }
}
