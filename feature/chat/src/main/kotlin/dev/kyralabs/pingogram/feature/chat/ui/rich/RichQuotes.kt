package dev.kyralabs.pingogram.feature.chat.ui.rich

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R

/**
 * A quote in the bubble's block style, as reply quotes are: a tonal card (`medium` corners, the block color) with a
 * quote mark, its [content] and who it [credit]s.
 */
@Composable
internal fun RichQuote(
    scope: RichScope,
    credit: FormattedText,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(scope.colors.block, MaterialTheme.shapes.medium)
                .padding(spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_format_quote),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = scope.colors.blockContent,
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            content()
            if (credit.text.isNotBlank()) {
                RichText(credit, scope, MaterialTheme.typography.labelMedium, color = scope.colors.secondaryContent)
            }
        }
    }
}

/** A quote set apart: larger, centred, italic, with who it credits below it. */
@Composable
internal fun RichPullQuote(
    block: RichBlock.PullQuote,
    scope: RichScope,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        RichText(
            text = block.text,
            scope = scope,
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            textAlign = TextAlign.Center,
        )
        if (block.credit.text.isNotBlank()) {
            RichText(
                text = block.credit,
                scope = scope,
                style = MaterialTheme.typography.labelMedium,
                color = scope.colors.secondaryContent,
                textAlign = TextAlign.Center,
            )
        }
    }
}
