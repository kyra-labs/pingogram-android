package dev.kyralabs.pingogram.feature.profile.chatinfo.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
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
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor

/** The statistics page: hoists [StatisticsViewModel]; a top member's row opens their profile. */
@Composable
internal fun StatisticsRoute(
    onBack: () -> Unit,
    onOpenProfile: (userId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnOpenProfile by rememberUpdatedState(onOpenProfile)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StatisticsEffect.OpenProfile -> currentOnOpenProfile(effect.userId)
            }
        }
    }
    StatisticsScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * The statistics (`docs/DESIGN_SYSTEM.md` §6, "Statistics"), a settings-style page: the period in `bodyMedium`
 * `onSurfaceVariant`, "Overview" (segmented rows: the value in `titleMedium` with tabular figures over its label, the
 * change at the end, `primary` up and `error` down), then each chart in a segment of its own under its title
 * ([StatisticsChart]; a `LoadingIndicator` while Telegram makes it, a line when it has no data), and in supergroups
 * "Top members" (avatar, name, messages). A keyed lazy list, at most `contentMaxWidth` wide.
 */
@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    onEvent: (StatisticsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    InfoPageScaffold(
        title = stringResource(R.string.statistics),
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        when (state) {
            StatisticsUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is StatisticsUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(StatisticsEvent.Retry) } }
            }

            is StatisticsUiState.Content -> {
                InfoPageBody(innerPadding) { contentPadding ->
                    StatisticsList(state, onEvent, contentPadding, listState)
                }
            }
        }
    }
}

@Composable
private fun StatisticsList(
    state: StatisticsUiState.Content,
    onEvent: (StatisticsEvent) -> Unit,
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
        item(key = PERIOD, contentType = PERIOD) {
            Text(
                text = stringResource(R.string.statistics_period, state.period),
                modifier = rowModifier,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.overview.isNotEmpty()) {
            item(key = OVERVIEW, contentType = OVERVIEW) { Overview(state, rowModifier) }
        }
        items(state.graphs, key = { it.key }, contentType = { GRAPH }) { graph -> Graph(graph, rowModifier) }
        if (state.topSenders.isNotEmpty()) {
            item(key = TOP, contentType = TOP) { TopSenders(state, onEvent, rowModifier) }
        }
    }
}

@Composable
private fun Overview(
    state: StatisticsUiState.Content,
    modifier: Modifier,
) {
    val rows =
        state.overview.map { item ->
            ProfileRow(item.label.toString()) { shapes ->
                SegmentedListItem(
                    shapes = shapes,
                    colors = profileRowColors(),
                    supportingContent = { Text(stringResource(item.label)) },
                    trailingContent =
                        item.change?.let { change ->
                            {
                                Text(
                                    change,
                                    color =
                                        when (item.trend) {
                                            Trend.Up -> MaterialTheme.colorScheme.primary
                                            Trend.Down -> MaterialTheme.colorScheme.error
                                            Trend.Flat -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    style = MaterialTheme.typography.labelLarge.numericContent,
                                )
                            }
                        },
                ) {
                    Text(item.value, style = MaterialTheme.typography.titleMedium.numericContent)
                }
            }
        }
    ProfileGroup(rows, modifier, title = stringResource(R.string.statistics_overview))
}

@Composable
private fun Graph(
    graph: GraphItem,
    modifier: Modifier,
) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = profileRowContainerColor()) {
        Column(
            Modifier.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(graph.title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
            when (val chart = graph.state) {
                GraphState.Loading -> GraphLoading()
                GraphState.Empty -> GraphEmpty()
                is GraphState.Drawn -> StatisticsChart(chart.chart, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GraphLoading() {
    val description = stringResource(R.string.statistics_graph_loading)
    Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.chartHeight), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
private fun GraphEmpty() {
    Text(
        text = stringResource(R.string.statistics_graph_empty),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun TopSenders(
    state: StatisticsUiState.Content,
    onEvent: (StatisticsEvent) -> Unit,
    modifier: Modifier,
) {
    val rows =
        state.topSenders.map { item ->
            ProfileRow(item.userId.toString()) { shapes ->
                val name = item.name.resolve()
                SegmentedListItem(
                    onClick = { onEvent(StatisticsEvent.Open(item.userId)) },
                    shapes = shapes,
                    colors = profileRowColors(),
                    leadingContent = {
                        Avatar(
                            photo = item.photo,
                            name = name,
                            id = item.userId,
                            size = MaterialTheme.sizes.avatarSmall,
                        )
                    },
                    supportingContent = { Text(item.messages.resolve()) },
                ) {
                    Text(
                        name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge.userContent,
                    )
                }
            }
        }
    ProfileGroup(rows, modifier, title = stringResource(R.string.statistics_top_members))
}

private const val PERIOD = "period"
private const val OVERVIEW = "overview"
private const val GRAPH = "graph"
private const val TOP = "top"
