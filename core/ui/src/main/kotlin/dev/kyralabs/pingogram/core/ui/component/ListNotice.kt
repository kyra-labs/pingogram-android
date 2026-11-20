package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * A notice in place of a lazy list's items, as [EmptyState] is for a screen (which fills one, and so cannot sit in a
 * lazy list): the [badge], a `titleMediumEmphasized` [title] (a heading), a `bodyMedium` [body] announced as
 * [announce] says, and an [action]. A profile's tabs say with it that they are empty or failed.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListNotice(
    badge: NoticeBadge,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    announce: LiveRegionMode = LiveRegionMode.Polite,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.extraLarge, vertical = spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Box(
            modifier =
                Modifier
                    .size(
                        MaterialTheme.sizes.headlineBadge,
                    ).background(badge.container, badge.shape.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(badge.icon), contentDescription = null, tint = badge.content)
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            text = body,
            modifier = Modifier.semantics { liveRegion = announce },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        action?.invoke()
    }
}
