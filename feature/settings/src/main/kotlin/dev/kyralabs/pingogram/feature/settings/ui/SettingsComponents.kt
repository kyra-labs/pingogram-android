package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.topOnly
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The frame every settings screen shares (docs/DESIGN_SYSTEM.md §6, "Settings rows"): a transparent top bar on the
// page's `surfaceContainerHigh` (settingsPageColor), and below it the page's groups of rows (SettingsGroup), with their
// headings and notes.

/**
 * The color of a settings page, behind its groups and its top bar (`docs/DESIGN_SYSTEM.md` §2.2). Its rows sit on
 * `surface`, the segmented list item's container color: lighter than the page in light themes and darker in dark ones
 * (true black with AMOLED black), as in Google's own Material 3 Expressive menus. One step above the floating
 * navigation bar's `surfaceContainer`, so the bar stands apart from the page it floats over.
 */
@Composable
@ReadOnlyComposable
internal fun settingsPageColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * A settings page pushed above the Settings tab: a small top app bar with back, [title] (`titleLargeEmphasized`) and
 * [actions] at its end, as the archive has, and [content] in [SettingsContent]. The floating navigation bar hides on
 * pushed screens, so the content keeps clear of [contentWindowInsets] only: the system bars, and the keyboard too on
 * pages with text fields.
 */
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsPageFrame(title, onBack, modifier, snackbarHostState, actions, contentWindowInsets) { innerPadding ->
        SettingsContent(innerPadding, content = content)
    }
}

/**
 * [SettingsPageScaffold] around a [body] of its own, for a page that lays out its content itself, e.g. a lazy list in
 * a [SettingsBody]; [body] gets the `Scaffold`'s inner padding.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsPageFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    body: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val titleStyle = MaterialTheme.typography.titleLargeEmphasized
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's color, which the transparent top bar shares.
        containerColor = settingsPageColor(),
        topBar = {
            TopAppBar(
                title = {
                    // The bar is one line high: a long title at a large font scale gets smaller rather than cut off.
                    Text(
                        text = title,
                        modifier = Modifier.semantics { heading() },
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(maxFontSize = titleStyle.fontSize),
                        style = titleStyle,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                actions = actions,
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = contentWindowInsets,
        content = body,
    )
}

/**
 * The body of a settings screen below its top bar, on the page's color: it starts below the bar, so what scrolls in
 * it never shows through the transparent bar, and [content] gets the rest of the `Scaffold`'s [innerPadding] (the sides
 * and the bottom: system bars, the floating navigation bar) to pad what it shows, so it scrolls behind them. Both are
 * read in the layout phase, so a collapsing bar moves the body without recomposing.
 */
@Composable
internal fun SettingsBody(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val belowBar = remember(innerPadding) { innerPadding.topOnly() }
    val contentPadding = remember(innerPadding) { innerPadding.withTop(0.dp) }
    Box(
        modifier
            .consumeWindowInsets(innerPadding)
            .fillMaxSize()
            .padding(belowBar),
    ) { content(contentPadding) }
}

/**
 * The scrolling body of a settings screen ([SettingsBody]): [content]'s groups one below the other, [groupSpacing]
 * apart, as wide as a single column of content gets (`MaterialTheme.sizes.contentMaxWidth`) and centered on wider
 * windows. The end of the content keeps clear of [innerPadding]'s bottom (system bars, the floating navigation bar).
 */
@Composable
internal fun SettingsContent(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    groupSpacing: Dp = MaterialTheme.spacing.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsBody(innerPadding, modifier) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                        .fillMaxWidth()
                        .padding(
                            start = MaterialTheme.spacing.windowMarginCompact,
                            end = MaterialTheme.spacing.windowMarginCompact,
                            top = MaterialTheme.spacing.small,
                            bottom = MaterialTheme.spacing.extraLarge,
                        ),
                verticalArrangement = Arrangement.spacedBy(groupSpacing),
                content = content,
            )
        }
    }
}

/** A group's [title]: a heading in `titleSmallEmphasized` and `primary` (`docs/DESIGN_SYSTEM.md` §3.3). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier =
            modifier
                .padding(start = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.small)
                .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** A note below a group: `bodyMedium` in `onSurfaceVariant`. */
@Composable
internal fun SettingsFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier.padding(
                start = MaterialTheme.spacing.large,
                end = MaterialTheme.spacing.large,
                top = MaterialTheme.spacing.small,
            ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * How the rows of a settings page's lazy list are laid out and animated: at most [maxWidth] wide (the settings pages'
 * column) with [margin]s, on the [motionScheme]'s springs.
 */
internal class SettingsListRowStyle(
    val maxWidth: Dp,
    val margin: Dp,
    val motionScheme: MotionScheme,
)

/** A row of a settings page's lazy list: as wide as the page's column, with its margins, sliding on its springs. */
internal fun Modifier.settingsListRow(
    scope: LazyItemScope,
    style: SettingsListRowStyle,
): Modifier =
    with(scope) {
        this@settingsListRow
            .animateItem(
                fadeInSpec = style.motionScheme.defaultEffectsSpec(),
                placementSpec = style.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = style.motionScheme.fastEffectsSpec(),
            ).widthIn(max = style.maxWidth)
            .fillMaxWidth()
            .padding(horizontal = style.margin)
    }
