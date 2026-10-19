package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The building blocks every settings screen shares (docs/DESIGN_SYSTEM.md §6, "Settings rows"): sections of ListItems
// in surfaceContainer cards under a titleSmallEmphasized header in primary, on the rounded surface sheet below a top
// bar on surfaceContainer, as the chat list is laid out.

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
 * [SettingsPageScaffold] around a [body] of its own, for a page that lays out its content itself, e.g. a lazy list on
 * a [ContentSheet]; [body] gets the `Scaffold`'s inner padding.
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
        // The top bar's container; the content sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
 * The scrolling body of a settings screen on a [ContentSheet] below its top bar: [content]'s sections one below the
 * other, as wide as a single column of content gets (`MaterialTheme.sizes.contentMaxWidth`) and centered on wider
 * windows. The end of the content keeps clear of [innerPadding]'s bottom (system bars, the floating navigation bar).
 */
@Composable
internal fun SettingsContent(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ContentSheet(innerPadding, modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
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
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraLarge),
                content = content,
            )
        }
    }
}

/**
 * A section: [title] as a heading ([SettingsSectionHeader]) over its rows in a `surfaceContainer` card, and a [footer]
 * note below the card ([SettingsFooter]).
 */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier) {
        SettingsSectionHeader(title)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            content = content,
        )
        if (footer != null) SettingsFooter(footer)
    }
}

/** A section's [title]: a heading in `titleSmallEmphasized` and `primary` (`docs/DESIGN_SYSTEM.md` §3.3). */
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

/** A note below a section's card: `bodyMedium` in `onSurfaceVariant`. */
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
 * The colors of a row in a [SettingsSection]: on the card's `surfaceContainer`, enabled or not (a disabled list item
 * would otherwise take the list's `surface`, and its row would leave the card).
 */
@Composable
internal fun settingsItemColors(): ListItemColors =
    ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

/** A row's leading icon, a Material Symbols drawable; the row's text names it for screen readers. */
@Composable
internal fun SettingsItemIcon(
    @DrawableRes icon: Int,
) {
    Icon(painter = painterResource(icon), contentDescription = null)
}

/**
 * The shape of row [index] of [count] that a lazy list draws as one card, as a [SettingsSection] would: the card's
 * `medium` corners at the top of the first row and the bottom of the last one, square in between.
 */
@Composable
internal fun cardRowShape(
    index: Int,
    count: Int,
): Shape {
    val card = MaterialTheme.shapes.medium
    return when {
        count == 1 -> card
        index == 0 -> card.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
        index == count - 1 -> card.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
        else -> RectangleShape
    }
}
