package dev.kyralabs.pingogram.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.navigation.GlobalSearchState
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [PingogramNavigationBar] while [visible] (`docs/DESIGN_SYSTEM.md` §6.1): it slides in from below by its own height on
 * the motion scheme's default spatial spring while it fades in on the default effects spring, and slides away and fades
 * out on the fast ones. The slide and the fade are read in the draw phase, so they never recompose or relayout.
 *
 * Hidden, it stays composed, but is neither placed (drawn, touched) nor in the semantics tree. Screens pushed above a
 * tab hide it, and composing it anew on the way back, its labels measured again ([labelsFit]), would add to the frame
 * that composes the tab's screen again, at the start of the back transition.
 *
 * [selected] is the tab the user is on, also while a screen pushed above it hides the bar, so the bar slides away and
 * back as it was instead of deselecting its tab on the way out and selecting it again on the way in. [searchOpen] and
 * [search] are the search button's ([PingogramNavigationBar]).
 */
@Composable
internal fun FloatingNavigationBar(
    visible: Boolean,
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchOpen: Boolean = false,
    search: GlobalSearchState? = null,
) {
    val motionScheme = MaterialTheme.motionScheme
    // 1 while shown, 0 while hidden: how far the bar is in its place, and its alpha.
    val position = remember { Animatable(if (visible) 1f else 0f) }
    val alpha = remember { Animatable(if (visible) 1f else 0f) }
    val placed = remember { derivedStateOf { alpha.value > 0f } }
    LaunchedEffect(visible) {
        val target = if (visible) 1f else 0f
        launch {
            position.animateTo(
                target,
                if (visible) motionScheme.defaultSpatialSpec() else motionScheme.fastSpatialSpec(),
            )
        }
        alpha.animateTo(target, if (visible) motionScheme.defaultEffectsSpec() else motionScheme.fastEffectsSpec())
    }
    PingogramNavigationBar(
        selected = selected,
        onSelect = onSelect,
        onSearch = onSearch,
        searchOpen = searchOpen,
        search = search,
        modifier =
            modifier
                .placedWhile { visible || placed.value }
                .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                .graphicsLayer {
                    translationY = (1f - position.value) * size.height
                    this.alpha = alpha.value.coerceIn(0f, 1f)
                },
    )
}

/** Places this content only while [placed]; otherwise it is measured but neither drawn nor reached by touches. */
private fun Modifier.placedWhile(placed: () -> Boolean): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (placed()) placeable.place(0, 0) }
    }

/**
 * The navigation bar of compact windows in the floating style of Google's Material 3 Expressive apps (Google Photos,
 * 2026; `docs/DESIGN_SYSTEM.md` §6): a pill that floats over the content and holds the [TopLevelDestination]s as tabs,
 * with the search button detached at its end.
 *
 * Material has no floating navigation bar component. A [HorizontalFloatingToolbar] with standard colors provides the
 * container, its elevation and the adjacent button; the tabs are built from tokens. The selected tab shows its filled
 * icon and label on a `secondaryContainer` pill, the others only their label. Where the labels do not fit (narrow
 * phones, large font scales), the other tabs show their outlined icon instead, named for screen readers by the label.
 *
 * The search button ([onSearch]) grows into search and back ([SearchButtonSlot], through [search]'s container
 * transform), and is away while search is [searchOpen].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PingogramNavigationBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    searchOpen: Boolean = false,
    search: GlobalSearchState? = null,
) {
    BoxWithConstraints(modifier.padding(horizontal = FloatingToolbarDefaults.ScreenOffset)) {
        val showLabels = labelsFit(maxWidth)
        val colors = FloatingToolbarDefaults.standardFloatingToolbarColors()
        val padding = FloatingToolbarDefaults.ContentPadding
        // The toolbar does not fix its height; the tabs fill the inside of its standard container.
        val tabHeight =
            FloatingToolbarDefaults.ContainerSize - padding.calculateTopPadding() - padding.calculateBottomPadding()
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                SearchButtonSlot(shown = !searchOpen, search = search, shape = CircleShape) { transform ->
                    // A circle in the toolbar's own colors, as in Google's floating navigation bars.
                    FloatingToolbarDefaults.StandardFloatingActionButton(
                        onClick = onSearch,
                        modifier = transform,
                        shape = CircleShape,
                        containerColor = colors.toolbarContainerColor,
                        contentColor = colors.toolbarContentColor,
                    ) {
                        Icon(
                            painter = painterResource(UiR.drawable.ic_search),
                            contentDescription = stringResource(R.string.nav_search),
                        )
                    }
                }
            },
            colors = colors,
        ) {
            Row(Modifier.selectableGroup()) {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination == selected
                    NavigationTab(
                        destination = destination,
                        selected = isSelected,
                        showLabel = showLabels || isSelected,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.height(tabHeight),
                    )
                }
            }
        }
    }
}

/**
 * One tab: a pill-shaped `Role.Tab` whose indicator fades in and whose icon slides in when it is selected, on the
 * theme's motion scheme. The indicator's alpha is read in the draw phase and the icon's width in the layout phase
 * ([revealHorizontally]), so neither recomposes.
 */
@Composable
private fun NavigationTab(
    destination: TopLevelDestination,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = MaterialTheme.motionScheme
    val colorScheme = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val label = stringResource(destination.label)
    val indicatorColor = colorScheme.secondaryContainer
    val indicatorAlpha = animateFloatAsState(if (selected) 1f else 0f, motionScheme.defaultEffectsSpec())
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onSecondaryContainer else LocalContentColor.current,
        animationSpec = motionScheme.defaultEffectsSpec(),
    )
    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .drawBehind {
                    drawRoundRect(
                        indicatorColor,
                        cornerRadius = CornerRadius(size.height / 2),
                        alpha = indicatorAlpha.value,
                    )
                }.selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.Tab,
                    interactionSource = null,
                    indication = ripple(),
                ).padding(horizontal = if (selected) spacing.large else spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconShown = selected || !showLabel
        val iconWidth = animateFloatAsState(if (iconShown) 1f else 0f, motionScheme.fastSpatialSpec())
        val iconAlpha = animateFloatAsState(if (iconShown) 1f else 0f, motionScheme.fastEffectsSpec())
        Row(
            modifier =
                Modifier
                    .revealHorizontally { iconWidth.value }
                    .graphicsLayer { alpha = iconAlpha.value },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
                // Without a label, the icon names the tab.
                contentDescription = if (showLabel) null else label,
                tint = contentColor,
            )
            if (showLabel) Spacer(Modifier.width(spacing.small))
        }
        if (showLabel) {
            val typography = MaterialTheme.typography
            Text(
                text = label,
                color = contentColor,
                style = if (selected) typography.labelLargeEmphasized else typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

/**
 * Whether every tab can show its label in [maxWidth], the bar's width with the search button. Measured for the widest
 * selection (the tab that grows most when emphasized and given its icon and wider padding), so selecting another tab
 * never switches between labels and icons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun labelsFit(maxWidth: Dp): Boolean {
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val labels = TopLevelDestination.entries.map { stringResource(it.label) }
    return remember(textMeasurer, typography, spacing, density, layoutDirection, labels, maxWidth) {
        with(density) {
            fun width(
                label: String,
                emphasized: Boolean,
            ): Int {
                val style = if (emphasized) typography.labelLargeEmphasized else typography.labelLarge
                return textMeasurer.measure(label, style, maxLines = 1).size.width
            }
            val labelsWidth = labels.sumOf { width(it, emphasized = false) }.toDp()
            val emphasisGrowth = labels.maxOf { width(it, emphasized = true) - width(it, emphasized = false) }.toDp()
            val tabPadding = spacing.medium * 2 * (labels.size - 1) + spacing.large * 2
            val icon = ICON_SIZE + spacing.small
            val toolbarPadding =
                FloatingToolbarDefaults.ContentPadding.calculateLeftPadding(layoutDirection) +
                    FloatingToolbarDefaults.ContentPadding.calculateRightPadding(layoutDirection)
            // The search button is about as large as the toolbar is high, a small gap away.
            val searchButton = FloatingToolbarDefaults.ContainerSize + spacing.small
            labelsWidth + emphasisGrowth + tabPadding + icon + toolbarPadding + searchButton <= maxWidth
        }
    }
}

/**
 * Shows this content at [fraction] of its width (0 hides it, values past 1 leave room after it), clipped, the fraction
 * read in the layout phase. Unlike `AnimatedVisibility`'s horizontal expand and shrink, whose intrinsic width stays
 * the content's full width, its intrinsic width is the width it takes: [HorizontalFloatingToolbar] sizes itself from
 * its content's intrinsic width and aligns the content to its end, so while one tab's icon shrank and another's grew
 * the toolbar was wider than its content and pushed it towards the search button, moving the whole bar until the
 * shrinking icon was gone.
 */
private fun Modifier.revealHorizontally(fraction: () -> Float): Modifier =
    clipToBounds().layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        val width = (placeable.width * fraction()).roundToInt().coerceAtLeast(0)
        layout(width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** Material Symbols are 24 dp (`docs/DESIGN_SYSTEM.md` §8). */
private val ICON_SIZE = 24.dp

@PingogramPreviews
@Composable
private fun PingogramNavigationBarPreview() {
    PingogramPreview {
        PingogramNavigationBar(selected = TopLevelDestination.Chats, onSelect = {}, onSearch = {})
    }
}
