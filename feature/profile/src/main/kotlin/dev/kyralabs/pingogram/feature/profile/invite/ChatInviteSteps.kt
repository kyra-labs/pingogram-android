package dev.kyralabs.pingogram.feature.profile.invite

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The request was sent: a `Cookie9Sided` badge with a check, the title, what happens next, and "Done". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RequestSent(
    kind: GroupKind,
    onDone: () -> Unit,
) {
    InviteMessage(
        shape = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_check,
        title = stringResource(R.string.invite_request_sent_title),
        body =
            stringResource(
                if (kind ==
                    GroupKind.Channel
                ) {
                    R.string.invite_request_sent_channel
                } else {
                    R.string.invite_request_sent_group
                },
            ),
        liveRegion = true,
    ) {
        InviteActions(primary = R.string.invite_done, enabled = true, onPrimary = onDone)
    }
}

/** Why the link does not open: a `Cookie4Sided` badge on `errorContainer`, the error, "Close" and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InviteFailed(
    state: ChatInviteUiState.Failed,
    onEvent: (ChatInviteEvent) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    InviteMessage(
        shape = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_link,
        title = stringResource(R.string.invite_failed_title),
        body = errorMessage(LocalResources.current, state.error, isDebuggable()),
        containerColor = colors.errorContainer,
        iconColor = colors.onErrorContainer,
    ) {
        if (state.canRetry) {
            InviteActions(
                primary = R.string.invite_try_again,
                enabled = true,
                onPrimary = { onEvent(ChatInviteEvent.Retry) },
                secondary = R.string.invite_close,
                onSecondary = onClose,
            )
        } else {
            InviteActions(primary = R.string.invite_close, enabled = true, onPrimary = onClose)
        }
    }
}

/** A step that says something: a [shape] badge with [icon], [title] in `headlineSmallEmphasized`, [body], [actions]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InviteMessage(
    shape: RoundedPolygon,
    icon: Int,
    title: String,
    body: String,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    liveRegion: Boolean = false,
    actions: @Composable () -> Unit,
) {
    val badgeSize = MaterialTheme.sizes.headlineBadge
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Box(Modifier.size(badgeSize).background(containerColor, shape.toShape()), Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, Modifier.size(badgeSize / 2), tint = iconColor)
        }
        Column(
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    if (liveRegion) this.liveRegion = LiveRegionMode.Polite
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
            Text(
                text = body,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        actions()
    }
}

/**
 * The step's actions, as the app's flows place them: [primary] a default filled `Button` at the end (none where it is
 * null), [secondary] a `TextButton` at the start; they wrap below each other where both do not fit.
 */
@Composable
internal fun InviteActions(
    @StringRes primary: Int?,
    enabled: Boolean,
    onPrimary: () -> Unit,
    @StringRes secondary: Int? = null,
    onSecondary: () -> Unit = {},
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
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
        if (primary != null) {
            Button(onClick = onPrimary, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(primary), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}
