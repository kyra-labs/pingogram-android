package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.plusBottom
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.BottomAndTopOf
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageBody
import dev.kyralabs.pingogram.feature.profile.chatinfo.InfoPageScaffold
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageError
import dev.kyralabs.pingogram.feature.profile.chatinfo.PageLoading
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors

/** The statistics page of a poll (`PollStatisticsRoute` of `core:navigation`): hoists [PollStatisticsViewModel]. */
@Composable
internal fun PollStatisticsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PollStatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PollStatisticsScreen(state, onRetry = viewModel::retry, onBack = onBack, modifier = modifier)
}

/**
 * A poll's statistics (`docs/DESIGN_SYSTEM.md` §6, "Poll statistics"), a settings-style page as the chat's statistics:
 * the question (a heading) over that Telegram makes the graph once a poll has 100 votes, "Votes over time" in a segment
 * of its own ([GraphCard], `core:ui`'s chart), then "Results" with each answer's votes and share. A delayed loading
 * indicator, and a `Cookie4Sided` error with "Try again".
 */
@Composable
internal fun PollStatisticsScreen(
    state: PollStatisticsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    InfoPageScaffold(
        title = stringResource(R.string.poll_statistics),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        when (state) {
            PollStatisticsUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is PollStatisticsUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error, onRetry) }
            }

            is PollStatisticsUiState.Content -> {
                InfoPageBody(innerPadding) { contentPadding -> PollStatisticsList(state, contentPadding, listState) }
            }
        }
    }
}

@Composable
private fun PollStatisticsList(
    state: PollStatisticsUiState.Content,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    val spacing = MaterialTheme.spacing
    val rowModifier =
        Modifier
            .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = windowMargin())
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            remember(contentPadding, spacing) {
                contentPadding.withTop(spacing.small).plusBottom(spacing.extraLarge).let(::BottomAndTopOf)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        item(key = QUESTION, contentType = QUESTION) { Question(state, rowModifier) }
        item(key = GRAPH, contentType = GRAPH) {
            GraphCard(
                title = stringResource(R.string.poll_statistics_graph),
                state = state.graph,
                modifier = rowModifier,
                emptyText = stringResource(R.string.poll_statistics_graph_empty),
            )
        }
        item(key = RESULTS, contentType = RESULTS) { Results(state, rowModifier) }
    }
}

@Composable
private fun Question(
    state: PollStatisticsUiState.Content,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            text = state.question,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium.userContent,
        )
        Text(
            text = stringResource(R.string.poll_statistics_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Each answer's votes and share of them, under "Results · 120 votes". */
@Composable
private fun Results(
    state: PollStatisticsUiState.Content,
    modifier: Modifier,
) {
    val rows =
        state.options.mapIndexed { index, option ->
            ProfileRow(index) { shapes ->
                SegmentedListItem(
                    shapes = shapes,
                    colors = profileRowColors(),
                    supportingContent = { Text(option.votes.resolve(), style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        Text(option.percent, style = MaterialTheme.typography.labelLarge.numericContent)
                    },
                ) {
                    Text(option.text, style = MaterialTheme.typography.bodyLarge.userContent)
                }
            }
        }
    ProfileGroup(
        rows,
        modifier,
        title = stringResource(R.string.poll_statistics_results, state.votes.resolve()),
    )
}

private const val QUESTION = "question"
private const val GRAPH = "graph"
private const val RESULTS = "results"
