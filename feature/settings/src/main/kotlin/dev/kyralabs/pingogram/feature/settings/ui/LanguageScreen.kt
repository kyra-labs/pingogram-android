package dev.kyralabs.pingogram.feature.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.core.app.LocaleManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Language page: hoists [LanguageViewModel], shows in snackbars a language that could not be saved, and tells the
 * page the system's language and whether Android has app language settings of its own (Android 13+), which its last
 * row opens. [onBack] leaves the page.
 */
@Composable
internal fun LanguageRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LanguageViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is LanguageEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    // The system's languages change only with the configuration, which creates the activity again.
    val configuration = LocalConfiguration.current
    val systemLanguage =
        remember(configuration) { LocaleManagerCompat.getSystemLocales(context)[0]?.toLanguageTag() }
    LanguageScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        systemLanguage = systemLanguage,
        onOpenAndroidSettings =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                {
                    context.openSystemSettings(
                        Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
            } else {
                null
            },
    )
}

/**
 * Language (`REQUIREMENTS.md` FR-S1 "Language", §6.4): the system's language ([systemLanguage], an IETF BCP 47 tag,
 * named below "System default") and each language Pingogram ships, as radio rows, each named in its own language and,
 * where that differs, in the current one; a note on text without a translation; then, where Android has app language
 * settings of its own ([onOpenAndroidSettings], Android 13+), a row that opens them. Choosing a language creates the
 * screens again in it.
 */
@Composable
internal fun LanguageScreen(
    state: LanguageUiState,
    onEvent: (LanguageEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    systemLanguage: String? = null,
    onOpenAndroidSettings: (() -> Unit)? = null,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_language),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            LanguageUiState.Loading -> LanguageLoading()
            is LanguageUiState.Content -> LanguageContent(state, onEvent, systemLanguage, onOpenAndroidSettings)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LanguageLoading() {
    val loading = stringResource(R.string.settings_language_loading)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = loading })
    }
}

@Composable
private fun LanguageContent(
    state: LanguageUiState.Content,
    onEvent: (LanguageEvent) -> Unit,
    systemLanguage: String?,
    onOpenAndroidSettings: (() -> Unit)?,
) {
    val select = { languageTag: String? ->
        if (languageTag != state.selected) onEvent(LanguageEvent.Select(languageTag))
    }
    // Names in the language the screens are in now.
    val current = LocalConfiguration.current.locales[0] ?: Locale.ROOT
    SettingsGroup(
        title = stringResource(R.string.settings_section_app_language),
        footer = stringResource(R.string.settings_language_footer),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        item(key = SYSTEM_LANGUAGE_KEY) { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_language_system),
                summary = systemLanguage?.let { languageName(it, current).inItself },
                selected = state.selected == null,
                enabled = true,
                shapes = shapes,
                onClick = { select(null) },
            )
        }
        state.languages.forEach { language ->
            item(key = language) { shapes ->
                val name = languageName(language, current)
                RadioItem(
                    label = name.inItself,
                    summary = name.inCurrent.takeIf { it != name.inItself },
                    selected = state.selected == language,
                    enabled = true,
                    shapes = shapes,
                    onClick = { select(language) },
                )
            }
        }
    }
    if (onOpenAndroidSettings != null) {
        SettingsGroup(title = stringResource(R.string.settings_section_android)) {
            item { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_language_android_settings),
                    shapes = shapes,
                    supportingContent = { Text(stringResource(R.string.settings_language_android_settings_summary)) },
                    trailingContent = {
                        Icon(painter = painterResource(R.drawable.ic_open_in_new), contentDescription = null)
                    },
                    onClick = onOpenAndroidSettings,
                )
            }
        }
    }
}

/** A language's name in itself ("Deutsch") and in the language of the screens ("German"). */
private class LanguageName(
    val inItself: String,
    val inCurrent: String,
)

/** The name of the language [languageTag] in itself and in [current], each starting with a capital as a label does. */
private fun languageName(
    languageTag: String,
    current: Locale,
): LanguageName {
    val locale = Locale.forLanguageTag(languageTag)
    return LanguageName(
        inItself = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) },
        inCurrent = locale.getDisplayName(current).replaceFirstChar { it.titlecase(current) },
    )
}

/** The key of the "System default" row, which no language tag can take. */
private const val SYSTEM_LANGUAGE_KEY = "system"

@PingogramPreviews
@Composable
private fun LanguageScreenPreview() {
    PingogramPreview {
        LanguageScreen(
            state = LanguageUiState.Content(persistentListOf("en"), selected = null),
            onEvent = {},
            onBack = {},
            systemLanguage = "en-US",
            onOpenAndroidSettings = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun LanguageScreenTranslatedPreview() {
    PingogramPreview {
        LanguageScreen(
            state = LanguageUiState.Content(persistentListOf("en", "de", "pt-BR"), selected = "de"),
            onEvent = {},
            onBack = {},
            systemLanguage = "fr-FR",
        )
    }
}

@PingogramPreviews
@Composable
private fun LanguageScreenLoadingPreview() {
    PingogramPreview { LanguageScreen(state = LanguageUiState.Loading, onEvent = {}, onBack = {}) }
}
