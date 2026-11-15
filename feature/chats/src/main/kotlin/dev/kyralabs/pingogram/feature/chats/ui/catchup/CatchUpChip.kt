package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * The chat list header's Catch-up chip (`docs/DESIGN_SYSTEM.md` §6 "Catch-up", owner decision 2026-11-15): an
 * extra-small filled button in `primary`, "Catch up · 12 chats" after a bolt on a `MaterialShapes.Cookie9Sided` badge
 * in `onPrimary`; Compose's hit testing widens its touches to 48 dp. Screen readers hear "Catch up, 12 unread chats".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CatchUpChip(
    chats: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val height = ButtonDefaults.ExtraSmallContainerHeight
    val label = resources.getQuantityString(R.plurals.catch_up_chip_description, chats, chats)
    Button(
        onClick = onClick,
        modifier =
            modifier.heightIn(height).semantics { contentDescription = label },
        shapes = ButtonDefaults.shapesFor(height),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
    ) {
        val badge = MaterialTheme.sizes.inlineIcon
        Box(
            modifier =
                Modifier
                    .size(badge)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.onPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bolt),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(MaterialTheme.spacing.small))
        Text(
            text = resources.getQuantityString(R.plurals.catch_up_chip, chats, chats),
            // The button's description says it in full.
            modifier = Modifier.clearAndSetSemantics {},
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ButtonDefaults.textStyleFor(height),
        )
    }
}
