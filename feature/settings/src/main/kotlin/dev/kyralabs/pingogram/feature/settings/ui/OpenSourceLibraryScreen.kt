package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData

/**
 * The page of library [libraryId]: hoists its [OpenSourceLibraryViewModel] and opens the links it asks for. [onBack]
 * leaves the page.
 */
@Composable
internal fun OpenSourceLibraryRoute(
    libraryId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSourceLibraryViewModel =
        hiltViewModel<OpenSourceLibraryViewModel, OpenSourceLibraryViewModel.Factory>(key = "library-$libraryId") {
            it.create(libraryId)
        },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is OpenSourceLibraryEffect.OpenUrl -> uriHandler.tryOpen(effect.url)
            }
        }
    }
    OpenSourceLibraryScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/**
 * A library, font or icon set Pingogram is built from (`docs/adr/0002-open-source-licenses.md`), titled by its name:
 * what it is, its version, artifact, developers and website (opened in the browser), then each of its licenses under
 * the license's name, its full text selectable for copying, and a link to the license online.
 */
@Composable
internal fun OpenSourceLibraryScreen(
    state: OpenSourceLibraryUiState,
    onEvent: (OpenSourceLibraryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = (state as? OpenSourceLibraryUiState.Content)?.name ?: stringResource(R.string.settings_about_licenses)
    SettingsPageScaffold(title = title, onBack = onBack, modifier = modifier) {
        when (state) {
            OpenSourceLibraryUiState.Loading -> {
                LicensesLoading(stringResource(R.string.settings_license_loading))
            }

            is OpenSourceLibraryUiState.Error -> {
                SettingsError(error = state.error, onRetry = { onEvent(OpenSourceLibraryEvent.Retry) })
            }

            is OpenSourceLibraryUiState.Content -> {
                LibraryDetails(state, onOpenWebsite = { onEvent(OpenSourceLibraryEvent.OpenWebsite) })
                state.licenses.forEach { license ->
                    LicenseGroup(license, onOpen = { onEvent(OpenSourceLibraryEvent.OpenLicense(license.id)) })
                }
            }
        }
    }
}

/** What the library is ([OpenSourceLibraryUiState.Content.description]), then a group of what is known about it. */
@Composable
private fun LibraryDetails(
    library: OpenSourceLibraryUiState.Content,
    onOpenWebsite: () -> Unit,
) {
    Column {
        if (library.description != null) {
            Text(
                text = library.description,
                modifier =
                    Modifier.padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        bottom = MaterialTheme.spacing.large,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SettingsGroup {
            library.version?.let { version ->
                item { shapes -> DetailRow(R.string.settings_license_version, version, shapes) }
            }
            if (library.isMavenArtifact) {
                item { shapes -> DetailRow(R.string.settings_license_artifact, library.id, shapes) }
            }
            library.developers?.let { developers ->
                item { shapes -> DetailRow(R.string.settings_license_developers, developers, shapes) }
            }
            library.website?.let { website ->
                item { shapes ->
                    SettingsRow(
                        title = stringResource(R.string.settings_license_website),
                        shapes = shapes,
                        supportingContent = { Text(website) },
                        trailingContent = { OpensInBrowserIcon() },
                        onClick = onOpenWebsite,
                    )
                }
            }
        }
    }
}

/** A row that only shows: [label] and its [value]. */
@Composable
private fun DetailRow(
    @StringRes label: Int,
    value: String,
    shapes: ListItemShapes,
) {
    SettingsRow(title = stringResource(label), shapes = shapes, supportingContent = { Text(value) })
}

/**
 * A license under its name: its full text in a segment of its own, selectable so it can be copied, or a note that it
 * is published online where the app has no text; then a row that opens it online ([onOpen]) if its address is known.
 */
@Composable
private fun LicenseGroup(
    license: LicenseItem,
    onOpen: () -> Unit,
) {
    SettingsGroup(title = license.name) {
        item(key = "text") { shapes ->
            SettingsSegment(shapes) {
                val text = license.text
                if (text != null) {
                    SelectionContainer {
                        Text(
                            text = text,
                            modifier = Modifier.padding(MaterialTheme.spacing.large),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_license_text_missing),
                        modifier = Modifier.padding(MaterialTheme.spacing.large),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        license.url?.let { url ->
            item(key = "online") { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_license_online),
                    shapes = shapes,
                    supportingContent = { Text(url) },
                    trailingContent = { OpensInBrowserIcon() },
                    onClick = onOpen,
                )
            }
        }
    }
}

/** Says that a row leaves the app; the row's text names it. */
@Composable
private fun OpensInBrowserIcon() {
    Icon(painter = painterResource(R.drawable.ic_open_in_new), contentDescription = null)
}

@PingogramPreviews
@Composable
private fun OpenSourceLibraryScreenPreview() {
    PingogramPreview {
        OpenSourceLibraryScreen(state = SettingsPreviewData.openSourceLibrary, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun OpenSourceLibraryScreenWithoutTextPreview() {
    PingogramPreview {
        OpenSourceLibraryScreen(state = SettingsPreviewData.openSourceLibraryWithoutText, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun OpenSourceLibraryScreenLoadingPreview() {
    PingogramPreview { OpenSourceLibraryScreen(state = OpenSourceLibraryUiState.Loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun OpenSourceLibraryScreenErrorPreview() {
    PingogramPreview {
        OpenSourceLibraryScreen(
            state = OpenSourceLibraryUiState.Error(DomainError.NotFound),
            onEvent = {},
            onBack = {},
        )
    }
}
