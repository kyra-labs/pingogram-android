package dev.kyralabs.pingogram.feature.settings.ui.transcription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsInfoFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsHeroSwitch
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The transcription page: hoists [TranscriptionSettingsViewModel], shows a setting that could not be saved in a
 * snackbar, and opens Android's speech language settings (its voice input settings, else its language settings).
 */
@Composable
internal fun TranscriptionSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TranscriptionSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is TranscriptionSettingsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }

                TranscriptionSettingsEffect.OpenLanguageSettings -> {
                    if (!context.openSpeechSettings()) {
                        launch {
                            snackbarHostState.showSnackbar(resources.getString(R.string.settings_transcription_no_page))
                        }
                    }
                }
            }
        }
    }
    TranscriptionSettingsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Voice message transcription (`docs/DESIGN_SYSTEM.md` §6 "Voice message transcription", the owner's board 28d):
 * "Transcribe automatically" as the page's master switch (others' voice and round video messages; owner decision
 * 2026-11-19), what the recognizer is and keeps (a privacy note, the shield), "Languages on this phone" (ready,
 * downloading, or which Android can download, disabled) and "Android language settings", both of which matter whether
 * or not it is on, and an info footer on Android 13 and Telegram's own transcription. Where the device cannot
 * transcribe, it says why instead.
 */
@Composable
internal fun TranscriptionSettingsScreen(
    state: TranscriptionSettingsUiState,
    onEvent: (TranscriptionSettingsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_transcription),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            TranscriptionSettingsUiState.Loading -> {
                Loading()
            }

            TranscriptionSettingsUiState.Unavailable -> {
                SettingsGroup(footer = stringResource(R.string.settings_transcription_footer)) {
                    item { shapes ->
                        SettingsRow(
                            title = stringResource(R.string.settings_transcription_unavailable),
                            shapes = shapes,
                            icon = R.drawable.ic_info_filled,
                            supportingContent = {
                                Text(
                                    stringResource(R.string.settings_transcription_unavailable_summary),
                                )
                            },
                        )
                    }
                }
            }

            is TranscriptionSettingsUiState.Content -> {
                SettingsHeroSwitch(
                    title = stringResource(R.string.settings_transcription_automatic),
                    summary = stringResource(R.string.settings_transcription_automatic_summary),
                    checked = state.automatic,
                    onCheckedChange = { onEvent(TranscriptionSettingsEvent.SetAutomatic(it)) },
                )
                Privacy()
                Languages(state, onEvent)
                SettingsInfoFooter(stringResource(R.string.settings_transcription_footer))
            }
        }
    }
}

@Composable
private fun Loading() {
    val loading = stringResource(R.string.settings_transcription_loading)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

/** What the recognizer is: Android's own, on this phone, with the shield. */
@Composable
private fun Privacy() {
    SettingsGroup {
        item { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_transcription_privacy_title),
                shapes = shapes,
                icon = R.drawable.ic_shield_filled,
                supportingContent = { Text(stringResource(R.string.settings_transcription_privacy)) },
            )
        }
    }
}

/** "Languages on this phone", then "Android language settings". */
@Composable
private fun Languages(
    state: TranscriptionSettingsUiState.Content,
    onEvent: (TranscriptionSettingsEvent) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    SettingsGroup(title = stringResource(R.string.settings_transcription_section_languages)) {
        val languages = state.languages
        if (languages == null) {
            item(key = LOADING) { shapes ->
                SettingsSegment(shapes) {
                    Box(
                        Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium),
                        contentAlignment = Alignment.Center,
                    ) { DelayedLoadingIndicator() }
                }
            }
        } else if (languages.isEmpty()) {
            item(key = NONE) { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_transcription_no_languages),
                    shapes = shapes,
                    icon = R.drawable.ic_language,
                )
            }
        }
        languages.orEmpty().forEach { language ->
            item(key = language.tag) { shapes ->
                SettingsRow(
                    title = Locale.forLanguageTag(language.tag).getDisplayName(locale),
                    shapes = shapes,
                    icon = language.state.icon(),
                    supportingContent = { Text(stringResource(language.state.label())) },
                    enabled = language.state != LanguageState.NotOnDevice,
                )
            }
        }
        item(key = SETTINGS) { shapes ->
            SettingsRow(
                title = stringResource(R.string.settings_transcription_language_settings),
                shapes = shapes,
                icon = UiR.drawable.ic_settings_filled,
                supportingContent = { Text(stringResource(R.string.settings_transcription_language_settings_summary)) },
                trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                onClick = { onEvent(TranscriptionSettingsEvent.OpenLanguageSettings) },
            )
        }
    }
}

private const val LOADING = "loading"
private const val NONE = "none"
private const val SETTINGS = "settings"

@PingogramPreviews
@Composable
private fun TranscriptionSettingsPreview() {
    PingogramPreview {
        TranscriptionSettingsScreen(TranscriptionSettingsPreviewData.content, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun TranscriptionSettingsUnavailablePreview() {
    PingogramPreview {
        TranscriptionSettingsScreen(TranscriptionSettingsUiState.Unavailable, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun TranscriptionSettingsLoadingPreview() {
    PingogramPreview { TranscriptionSettingsScreen(TranscriptionSettingsUiState.Loading, onEvent = {}, onBack = {}) }
}
