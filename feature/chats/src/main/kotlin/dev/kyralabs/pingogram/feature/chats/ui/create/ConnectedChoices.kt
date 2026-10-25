package dev.kyralabs.pingogram.feature.chats.ui.create

import android.content.Context
import android.content.Intent
import android.icu.text.ListFormatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A single choice of a form: Material 3 Expressive's connected [ToggleButton]s, which morph on press and to a full pill
 * when checked, as the contact forms' and Appearance's choices (`docs/DESIGN_SYSTEM.md` §6, "Settings rows"), where
 * every label fits its button on one line; otherwise (a large font scale, long translations) radio rows, as
 * Appearance's choices do, so no label is ever broken or cut. Labels are measured in the checked button's style,
 * `labelLargeEmphasized`.
 */
@Composable
internal fun ConnectedChoices(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLargeEmphasized
    val layoutDirection = LocalLayoutDirection.current
    val buttonPadding = ToggleButtonDefaults.contentPaddingFor(ToggleButtonDefaults.MinHeight)
    // What a button takes besides its label: its padding, and its share of the gaps between the buttons.
    val buttonChrome =
        buttonPadding.calculateStartPadding(layoutDirection) +
            buttonPadding.calculateEndPadding(layoutDirection) +
            ButtonGroupDefaults.ConnectedSpaceBetween
    val density = LocalDensity.current
    val widestLabel = remember(labels, style, density) { labels.maxOf { textMeasurer.measure(it, style).size.width } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val buttonWidth = with(density) { (maxWidth / labels.size - buttonChrome).roundToPx() }
        if (widestLabel <= buttonWidth) {
            ChoiceButtons(labels, selectedIndex, onSelect, enabled)
        } else {
            Column(Modifier.selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    ChoiceRadioRow(label, selected = index == selectedIndex, enabled) { onSelect(index) }
                }
            }
        }
    }
}

/** [ConnectedChoices]' buttons, sharing the row's width, `surfaceContainerHighest` while unchecked. */
@Composable
private fun ChoiceButtons(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
) {
    val colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    val typography = MaterialTheme.typography
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(index) },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                enabled = enabled,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors = colors,
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    style = if (checked) typography.labelLargeEmphasized else typography.labelLarge,
                )
            }
        }
    }
}

/**
 * One option of [ConnectedChoices] where the buttons do not fit: a [ListItem] with a leading [RadioButton], the whole
 * row selecting it, a radio button for screen readers.
 */
@Composable
private fun ChoiceRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label) },
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        leadingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
