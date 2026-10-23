package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.topOnly
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The export page's frame: a settings page in look (docs/DESIGN_SYSTEM.md §6 "Settings rows", "Chat export"),
// as the settings pages and the group info pages are, which live in modules this one does not depend on.

/**
 * The page: Material's medium flexible top bar with back and [title], built from [TwoRowsTopAppBar] as the settings
 * pages' (`headlineMediumEmphasized` expanded, `titleLargeEmphasized` collapsed), transparent on the page's color
 * ([exportPageColor]) and collapsing as [body] scrolls, as wide as the page's column; snackbars from
 * [snackbarHostState].
 */
@Composable
internal fun ExportPageScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(),
    body: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    val extraMargin = windowMargin() - MaterialTheme.spacing.windowMarginCompact
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = exportPageColor(),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                TwoRowsTopAppBar(
                    title = { expanded -> PageTitle(title, expanded) },
                    modifier =
                        Modifier
                            .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                            .padding(horizontal = extraMargin),
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.chat_export_back),
                            )
                        }
                    },
                    collapsedHeight = TopAppBarDefaults.MediumAppBarCollapsedHeight,
                    expandedHeight = TopAppBarDefaults.MediumFlexibleAppBarWithoutSubtitleExpandedHeight,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = body,
    )
}

/** [title] on the bar's [expanded] row or its collapsed one; smaller rather than cut off at a large font scale. */
@Composable
private fun PageTitle(
    title: String,
    expanded: Boolean,
) {
    val typography = MaterialTheme.typography
    val style = if (expanded) typography.headlineMediumEmphasized else typography.titleLargeEmphasized
    Text(
        text = title,
        modifier = Modifier.semantics { heading() },
        maxLines = if (expanded) EXPANDED_TITLE_MAX_LINES else 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(maxFontSize = style.fontSize),
        style = style,
    )
}

/**
 * The body below the bar: it starts below the bar, so nothing scrolls behind the transparent bar, and [content] gets
 * the rest of [innerPadding]. The `Scaffold` hands a new [innerPadding] in every frame of a collapsing bar; one padding
 * object reads the latest in the layout phase ([LatestPadding]), so [content] does not compose again for it
 * (`docs/PERFORMANCE.md` §4).
 */
@Composable
internal fun ExportPageBody(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val latest = remember { LatestPadding() }
    SideEffect { latest.current = innerPadding }
    val belowBar = remember(latest) { latest.topOnly() }
    val contentPadding = remember(latest) { latest.withTop(0.dp) }
    Box(
        modifier
            .consumeWindowInsets(innerPadding)
            .fillMaxSize()
            .padding(belowBar),
    ) { content(contentPadding) }
}

/** The padding last handed to [ExportPageBody], read where it is used: in layout, so a change only lays out again. */
@Stable
private class LatestPadding : PaddingValues {
    var current: PaddingValues by mutableStateOf(PaddingValues(0.dp))

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = current.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = current.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        current.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = current.calculateBottomPadding()
}

/** The page's groups, `spacing.extraLarge` apart, as wide as a column of content gets and centered, in the margin. */
@Composable
internal fun ExportColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val margin = windowMargin()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .fillMaxWidth()
                    .padding(
                        start = margin,
                        end = margin,
                        top = MaterialTheme.spacing.small,
                        bottom = MaterialTheme.spacing.extraLarge,
                    ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
            content = content,
        )
    }
}

/** The page's color: `surfaceContainerHigh`, as settings pages; `surface` (true black) with AMOLED black. */
@Composable
@ReadOnlyComposable
internal fun exportPageColor(): Color =
    if (isAmoledBlack()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh

/** The lines a title may take on the bar's expanded row, as Material's flexible bars wrap it. */
private const val EXPANDED_TITLE_MAX_LINES = 2
