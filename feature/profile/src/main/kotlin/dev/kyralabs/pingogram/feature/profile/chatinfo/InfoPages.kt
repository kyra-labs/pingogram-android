package dev.kyralabs.pingogram.feature.profile.chatinfo

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.component.topOnly
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.component.withTop
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.profilePageColor
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.core.ui.R as UiR

// The pages a group's or channel's info page opens (members, rights, permissions, editing, invite links): settings
// pages in look (docs/DESIGN_SYSTEM.md §6 "Settings rows"), a transparent medium flexible top bar on the profile's
// page color, and below it groups of segmented rows (ProfileGroup).

/**
 * A page pushed above a group's or channel's info page: Material's medium flexible top bar with back, [title] and
 * [actions], built from [TwoRowsTopAppBar] as the settings pages' (`headlineMediumEmphasized` expanded,
 * `titleLargeEmphasized` collapsed), collapsing as the page scrolls, and [body] below it with the `Scaffold`'s inner
 * padding. The bar is as wide as the page's column and inside the same window margin.
 */
@Composable
internal fun InfoPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    actions: @Composable RowScope.() -> Unit = {},
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    body: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val extraMargin = windowMargin() - MaterialTheme.spacing.windowMarginCompact
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = profilePageColor(),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                TwoRowsTopAppBar(
                    title = { expanded -> InfoPageTitle(title, expanded) },
                    modifier =
                        Modifier
                            .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                            .padding(horizontal = extraMargin),
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.profile_back),
                            )
                        }
                    },
                    actions = actions,
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
        contentWindowInsets = contentWindowInsets,
        content = body,
    )
}

/** [title] on the bar's [expanded] row or its collapsed one; smaller rather than cut off at a large font scale. */
@Composable
private fun InfoPageTitle(
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
 * The body of a page below its top bar: it starts below the bar, so nothing scrolls behind the transparent bar, and
 * [content] gets the rest of [innerPadding] (the sides and the bottom). The `Scaffold` hands a new [innerPadding] in
 * every frame of a collapsing bar; the body keeps one padding object that reads the latest in the layout phase
 * ([LatestPadding]), so [content] does not compose again for it (`docs/PERFORMANCE.md` §4). Read its parts in layout
 * modifiers and lazy lists' content padding, not in composition.
 */
@Composable
internal fun InfoPageBody(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
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

/** The padding last handed to [InfoPageBody], read where it is used: in layout, so a change only lays out again. */
@Stable
private class LatestPadding : PaddingValues {
    var current: PaddingValues by mutableStateOf(PaddingValues(0.dp))

    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = current.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = current.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        current.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = current.calculateBottomPadding()
}

/** The sides of [padding] alone, for content above a list that takes the bottom itself. */
@Stable
internal class SidesOf(
    private val padding: PaddingValues,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = padding.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
        padding.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = 0.dp
}

/**
 * A page's groups one below the other, `spacing.extraLarge` apart, in a column that scrolls, as wide as a column of
 * content gets and centered, inside the window margin.
 */
@Composable
internal fun InfoPageContent(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val margin = windowMargin()
    InfoPageBody(innerPadding, modifier) { contentPadding ->
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
 * A switch row (`docs/DESIGN_SYSTEM.md` §6 "Settings rows"): the clickable segmented list item (not its toggleable
 * variant, which screen readers would call a checkbox) with `Role.Switch` semantics, [label] and [supporting] text, and
 * a trailing [CheckSwitch] that the row toggles, with a `ToggleOn` or `ToggleOff` haptic. Disabled rows ([enabled]
 * false) show what they are and change nothing.
 */
@Suppress("LongParameterList") // What the row shows and does.
@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supporting: String? = null,
) {
    val haptics = LocalHapticFeedback.current
    SegmentedListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        shapes = shapes,
        modifier =
            modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        enabled = enabled,
        colors = profileRowColors(),
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { CheckSwitch(checked = checked, enabled = enabled) },
    ) {
        Text(label)
    }
}

/** The lines a title may take on the bar's expanded row, as Material's flexible bars wrap it. */
private const val EXPANDED_TITLE_MAX_LINES = 2

/** The top and bottom of [padding] alone: the members list's sides are its column's. */
@Stable
internal class BottomAndTopOf(
    private val padding: PaddingValues,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateTopPadding() = padding.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) = 0.dp

    override fun calculateBottomPadding() = padding.calculateBottomPadding()
}
