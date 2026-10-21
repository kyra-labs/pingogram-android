package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import java.text.NumberFormat

/**
 * The Open-source licenses page: hoists [OpenSourceLicensesViewModel]. A row calls [onOpenLibrary] with the id of the
 * library it shows; [onBack] leaves the page.
 */
@Composable
internal fun OpenSourceLicensesRoute(
    onOpenLibrary: (libraryId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OpenSourceLicensesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OpenSourceLicensesScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenLibrary = onOpenLibrary,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Open-source licenses (`REQUIREMENTS.md` FR-S1, About; `docs/adr/0002-open-source-licenses.md`): every library, font
 * and icon set the app is built from, listed by its build, in the rows of one segmented group under their count: name,
 * version and licenses. A row opens the library's page with the full texts of its licenses. The list comes from the
 * app itself, at once; a loading indicator shows only if it takes a moment ([DelayedLoadingIndicator]).
 */
@Composable
internal fun OpenSourceLicensesScreen(
    state: OpenSourceLicensesUiState,
    onEvent: (OpenSourceLicensesEvent) -> Unit,
    onOpenLibrary: (libraryId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    SettingsPageFrame(
        title = stringResource(R.string.settings_about_licenses),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        SettingsBody(innerPadding) { contentPadding ->
            when (state) {
                OpenSourceLicensesUiState.Loading -> {
                    LicensesLoading(
                        stringResource(R.string.settings_licenses_loading),
                        Modifier.padding(contentPadding),
                    )
                }

                is OpenSourceLicensesUiState.Error -> {
                    SettingsError(
                        error = state.error,
                        onRetry = { onEvent(OpenSourceLicensesEvent.Retry) },
                        modifier = Modifier.padding(contentPadding),
                    )
                }

                is OpenSourceLicensesUiState.Content -> {
                    LibraryList(state, onOpenLibrary, listState, contentPadding)
                }
            }
        }
    }
}

/**
 * The note on what the list is, the libraries' count, then a row for each library, as wide as the settings pages'
 * column and with their margins.
 */
@Composable
private fun LibraryList(
    state: OpenSourceLicensesUiState.Content,
    onOpenLibrary: (libraryId: String) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style =
        SettingsListRowStyle(
            MaterialTheme.sizes.contentMaxWidth,
            windowMargin(),
            MaterialTheme.motionScheme,
        )
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        // The gap between segmented rows; the note and the heading keep their own padding besides.
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = INTRO, contentType = INTRO) {
            Text(
                text = stringResource(R.string.settings_licenses_intro),
                modifier =
                    Modifier.settingsListRow(this, style).padding(
                        start = spacing.large,
                        end = spacing.large,
                        bottom = spacing.extraLarge,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item(key = HEADER, contentType = HEADER) {
            SettingsSectionHeader(
                title =
                    pluralStringResource(
                        R.plurals.settings_licenses_header,
                        state.libraries.size,
                        numbers.format(state.libraries.size),
                    ),
                modifier = Modifier.settingsListRow(this, style),
            )
        }
        itemsIndexed(state.libraries, key = { _, item -> item.id }, contentType = { _, _ -> ROW }) { index, item ->
            LibraryRow(
                item = item,
                shapes = ListItemDefaults.segmentedShapes(index, state.libraries.size),
                onClick = { onOpenLibrary(item.id) },
                modifier = Modifier.settingsListRow(this, style),
            )
        }
    }
}

/** A library, a row of the list's segmented group ([shapes]): its name, then its version and licenses. */
@Composable
private fun LibraryRow(
    item: OpenSourceLibraryItem,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        colors = settingsRowColors(),
        supportingContent = {
            Text(
                if (item.version != null) {
                    stringResource(R.string.settings_licenses_library_summary, item.version, item.licenses)
                } else {
                    item.licenses
                },
            )
        },
    ) {
        Text(item.name)
    }
}

/**
 * The list or a library loading: [DelayedLoadingIndicator], which shows only if it takes a moment, announced by screen
 * readers as [description].
 */
@Composable
internal fun LicensesLoading(
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.extraLarge)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        DelayedLoadingIndicator()
    }
}

private const val INTRO = "intro"
private const val HEADER = "header"
private const val ROW = "row"

@PingogramPreviews
@Composable
private fun OpenSourceLicensesScreenPreview() {
    PingogramPreview {
        OpenSourceLicensesScreen(
            state = SettingsPreviewData.openSourceLicenses,
            onEvent = {},
            onOpenLibrary = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun OpenSourceLicensesScreenLoadingPreview() {
    PingogramPreview {
        OpenSourceLicensesScreen(
            state = OpenSourceLicensesUiState.Loading,
            onEvent = {},
            onOpenLibrary = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun OpenSourceLicensesScreenErrorPreview() {
    PingogramPreview {
        OpenSourceLicensesScreen(
            state = OpenSourceLicensesUiState.Error(DomainError.Unknown(0, "unreadable")),
            onEvent = {},
            onOpenLibrary = {},
            onBack = {},
        )
    }
}
