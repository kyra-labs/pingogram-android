package dev.kyralabs.pingogram.feature.chat.ui.location

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts the "Location" sheet's steps share ([ShareLocationSheet]).

/**
 * A step's actions as in Google's flows: the primary one at the end, the [secondary] one as a text button at the
 * start (none where it is null). They wrap below each other where both do not fit (large font scales).
 */
@Composable
internal fun StepActions(
    @StringRes secondary: Int?,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
    primary: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TextButton(onClick = onSecondary, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(secondary), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Box {}
        }
        primary()
    }
}

/** A filled button of the default size with the press morph, its label `labelLargeEmphasized`. */
@Composable
internal fun PrimaryButton(
    @StringRes text: Int,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(text), style = MaterialTheme.typography.labelLargeEmphasized)
    }
}

/** The location pin on a [shape] of `primaryContainer`, decorative (`docs/DESIGN_SYSTEM.md` §4). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LocationBadge(
    shape: RoundedPolygon,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val badgeSize = MaterialTheme.sizes.headlineBadge
    Box(
        modifier = Modifier.size(badgeSize).background(containerColor, shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_location_on_filled),
            contentDescription = null,
            modifier = Modifier.size(badgeSize / 2),
            tint = iconColor,
        )
    }
}
