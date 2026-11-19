package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.R

/**
 * Back in a page's top bar as a tonal circle (`docs/DESIGN_SYSTEM.md` §13.10, "Top bar"; owner decision 2026-11-19):
 * a [FilledTonalIconButton] in Material's 40 dp icon-button container, centered in its 48 dp touch target, whose shape
 * morphs on press, the back arrow (mirrored right to left) named [label], with [label] as its tooltip. The bar places
 * the touch target on the window margin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RoundBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(IconButtonDefaults.smallContainerSize()),
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = label)
        }
    }
}
