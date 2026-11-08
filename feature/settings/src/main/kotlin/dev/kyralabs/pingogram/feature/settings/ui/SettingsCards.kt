package dev.kyralabs.pingogram.feature.settings.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The cards at the top of some settings pages (docs/DESIGN_SYSTEM.md §13.10): a status card on
// `primaryContainer` (what is going on now) and a banner on `errorContainer` (something to fix, with its button).

/**
 * A status card: an [icon] on a `Cookie9Sided` badge, a [kicker] ("Right now") over the [title], the [summary] and a
 * [status] line after a dot, on `primaryContainer` with `extraLarge` corners. One item for screen readers.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsStatusCard(
    kicker: String,
    title: String,
    summary: String,
    status: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(MaterialTheme.sizes.headlineBadge)
                        .background(colors.onPrimaryContainer, MaterialShapes.Cookie9Sided.toShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(icon), contentDescription = null, tint = colors.primaryContainer)
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                Text(kicker, style = MaterialTheme.typography.labelMediumEmphasized)
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(spacing.small)
                            .background(colors.onPrimaryContainer, CircleShape),
                    )
                    Text(status, style = MaterialTheme.typography.labelLargeEmphasized)
                }
            }
        }
    }
}

/**
 * A banner about something to fix: an [icon] on a `Flower` badge, the [title] over the [summary], and a [button] that
 * fixes it ([onClick]), `onErrorContainer` on `errorContainer` with `extraLarge` corners. The button's label stays
 * apart for screen readers, the rest is one item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsBannerCard(
    @DrawableRes icon: Int,
    title: String,
    summary: String,
    button: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.errorContainer,
        contentColor = colors.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(MaterialTheme.sizes.leadingIconContainer)
                            .background(colors.onErrorContainer, MaterialShapes.Flower.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painter = painterResource(icon), contentDescription = null, tint = colors.errorContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                    Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End),
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colors.onErrorContainer,
                        contentColor = colors.errorContainer,
                    ),
            ) {
                Text(button, style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}
