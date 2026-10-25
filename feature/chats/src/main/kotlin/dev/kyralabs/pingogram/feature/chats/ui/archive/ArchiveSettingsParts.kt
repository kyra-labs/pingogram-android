package dev.kyralabs.pingogram.feature.chats.ui.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.theme.isAmoledBlack
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The archive settings page's frame and rows, as feature:settings' pages draw theirs (docs/DESIGN_SYSTEM.md §6,
// "Settings rows"): features share no code, so these are that pattern's parts this page needs.

/**
 * The color of the page, behind its groups and its transparent top bar: `surfaceContainerHigh`, true black (`surface`)
 * with AMOLED black, where the rows move to `surfaceContainer` ([pageRowColors], `docs/DESIGN_SYSTEM.md` §2.2).
 */
@Composable
@ReadOnlyComposable
internal fun archivePageColor(): Color =
    if (isAmoledBlack()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHigh

/**
 * The colors of a row: the segmented list item's on `surface`, or `surfaceContainer` with AMOLED black, where the page
 * itself is black.
 */
@Composable
internal fun pageRowColors(): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor =
            if (isAmoledBlack()) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
    )

/**
 * A settings-style page pushed above the screen it was opened from: Material's medium flexible top bar with back and
 * [title] ([ArchivePageTopBar]), collapsing as the page scrolls, and [content]'s groups below it, one column at most
 * `contentMaxWidth` wide inside the window margin, `spacing.extraLarge` apart. The content keeps clear of the system
 * bars and scrolls behind them.
 */
@Composable
internal fun ArchivePageScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The page's color, which the transparent top bar shares.
        containerColor = archivePageColor(),
        topBar = { ArchivePageTopBar(title, onBack, scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val margin = windowMargin()
        Column(
            modifier =
                Modifier
                    .consumeWindowInsets(innerPadding)
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
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
}

/**
 * Material's medium flexible top bar, built from [TwoRowsTopAppBar] as feature:settings' pages build it: back and
 * [title], transparent on the page's color, as wide as the page's column inside the same window margin, collapsing with
 * [scrollBehavior].
 */
@Composable
private fun ArchivePageTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // The bar's own insets match the compact margin; on wider windows it moves in with the content's.
    val extraMargin = windowMargin() - MaterialTheme.spacing.windowMarginCompact
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        TwoRowsTopAppBar(
            title = { expanded -> ArchivePageTitle(title, expanded) },
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .padding(horizontal = extraMargin),
            navigationIcon = {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.chat_list_archive_back),
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
}

/**
 * [title] on the bar's [expanded] row (`headlineMediumEmphasized`, up to two lines) or its collapsed one
 * (`titleLargeEmphasized`, one line), smaller rather than cut off at a large font scale.
 */
@Composable
private fun ArchivePageTitle(
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
 * A group of rows, Material 3 Expressive's segmented list: [title] as a heading in `titleSmallEmphasized` and
 * `primary`, the rows ([rows], each composed with the shapes of its place among [count] rows,
 * `ListItemDefaults.segmentedShapes`), `ListItemDefaults.SegmentedGap` apart, and [footer] below them in `bodyMedium`
 * `onSurfaceVariant`. [rowsModifier] applies to the rows alone, e.g. `Modifier.selectableGroup()`.
 */
@Composable
internal fun ArchivePageGroup(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    footer: String? = null,
    rowsModifier: Modifier = Modifier,
    rows: @Composable (index: Int, shapes: ListItemShapes) -> Unit,
) {
    Column(modifier) {
        Text(
            text = title,
            modifier =
                Modifier
                    .padding(start = MaterialTheme.spacing.large, bottom = MaterialTheme.spacing.small)
                    .semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        Column(rowsModifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            repeat(count) { index -> key(index) { rows(index, ListItemDefaults.segmentedShapes(index, count)) } }
        }
        if (footer != null) {
            Text(
                text = footer,
                modifier =
                    Modifier.padding(
                        start = MaterialTheme.spacing.large,
                        end = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.small,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A row that holds something other than a list item's slots (loading, an error): [content] on the rows' color. */
@Composable
internal fun ArchivePageSegment(
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(pageRowColors().containerColor, shapes.shape),
        content = content,
    )
}

/**
 * A setting that is on or off: the whole row toggles its switch ([CheckSwitch], a check in its thumb while on) with a
 * `ToggleOn` or `ToggleOff` haptic, and screen readers hear it as a switch; disabled ([enabled]) it shows its value
 * faded and does nothing.
 */
@Suppress("LongParameterList") // A switch row's texts, value, state, place and callback.
@Composable
internal fun ArchiveSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    shapes: ListItemShapes,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        shapes = shapes,
        modifier =
            Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        enabled = enabled,
        trailingContent = { CheckSwitch(checked = checked, enabled = enabled) },
        supportingContent = { Text(summary) },
        colors = pageRowColors(),
    ) {
        Text(title)
    }
}

/**
 * One of several options in a `selectableGroup`: the single-selection [SegmentedListItem] with a [RadioButton],
 * [label] and [summary]. The whole row selects it, with a `SegmentTick` haptic, screen readers hear it as a radio
 * button, and the selected one takes the list item's selected container and shape.
 */
@Composable
internal fun ArchiveRadioItem(
    label: String,
    summary: String,
    selected: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        selected = selected,
        onClick = {
            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onClick()
        },
        shapes = shapes,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = { Text(summary) },
        colors = pageRowColors(),
    ) {
        Text(label)
    }
}

/** The lines a title may take on the bar's expanded row, as Material's flexible bars wrap it. */
private const val EXPANDED_TITLE_MAX_LINES = 2
