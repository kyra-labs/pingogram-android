package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.RichMessageEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A collapsible section on the bubble's block color with `medium` corners: its header (`titleSmallEmphasized`, a
 * 48 dp row) with a chevron that turns as it opens, on the fast spatial spring in its graphics layer, and below it its
 * blocks, which grow in and shrink away (`expandVertically` / `shrinkVertically` on the default spatial spring, fades
 * on the effects springs). It starts as Telegram says ([RichBlock.Details.isOpen]); a tap on the header toggles it
 * ([RichMessageEvent.ToggleSection]), kept in the view model's saved state ([RichMessageUi]).
 *
 * The bubble is one item for screen readers (`docs/DESIGN_SYSTEM.md` §9): they hear the section and whether it is
 * expanded in its description and toggle it through its custom actions ([richActions]).
 */
@Composable
internal fun RichDetails(
    block: RichBlock.Details,
    scope: RichScope,
) {
    val open = scope.ui.isOpen(block)
    val motion = MaterialTheme.motionScheme
    val turn by animateFloatAsState(if (open) HALF_TURN else 0f, motion.fastSpatialSpec(), label = "chevron")
    val spacing = MaterialTheme.spacing
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scope.colors.block, shape),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.Button) {
                        scope.onEvent(RichMessageEvent.ToggleSection(scope.messageId, block.id))
                    }.padding(horizontal = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            RichText(
                text = block.header,
                scope = scope,
                style = MaterialTheme.typography.titleSmallEmphasized,
                modifier = Modifier.weight(1f),
                color = scope.colors.blockContent,
            )
            Icon(
                painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon).graphicsLayer { rotationZ = turn },
                tint = scope.colors.blockContent,
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(motion.defaultSpatialSpec<IntSize>()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkVertically(motion.defaultSpatialSpec<IntSize>()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Column(
                modifier = Modifier.padding(start = spacing.small, end = spacing.small, bottom = spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) { RichBlocks(block.blocks, scope) }
        }
    }
}

private const val HALF_TURN = 180f
