package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.domain.model.GiveawayParameters
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayUi
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.countriesText
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.giveawayDateText
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.giveawayNumber
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.toPluralCount
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.winnersWonText
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The winners of a giveaway (`docs/DESIGN_SYSTEM.md` §6 "Giveaways"), padded in the bubble: a trophy badge in the
 * bubble's accent, "Giveaway winners" over "3 people won Telegram Premium for 3 months", the winners as tiles (avatar
 * and name, three to a row) that open their profiles, what nobody claimed, and "Learn more".
 */
@Composable
internal fun MessageGiveawayWinners(
    content: MessageContent.GiveawayWinners,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
    ui: GiveawayUi = GiveawayUi(),
    onAction: (GiveawayAction) -> Unit = {},
) {
    Column(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        WinnersHeader(content, colors)
        when {
            content.wasRefunded -> SecondaryNote(R.string.giveaway_refunded, colors)
            ui.winners.isEmpty() -> SecondaryNote(R.string.giveaway_winners_private, colors)
            else -> WinnerTiles(ui, colors, onAction)
        }
        if (content.unclaimedPrizeCount > 0) {
            val count = content.unclaimedPrizeCount
            SecondaryLine(pluralStringResource(R.plurals.giveaway_unclaimed, count, count), colors)
        }
        LearnMoreButton(colors, onClick = { onAction(GiveawayAction.LearnMore) })
    }
}

/** The trophy badge in the bubble's accent, "Giveaway winners" over who won what. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes
@Composable
private fun WinnersHeader(
    content: MessageContent.GiveawayWinners,
    colors: BubbleColors,
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.avatarSmall)
                    .background(colors.accent, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_emoji_events),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.avatarBadge),
                tint = colors.onAccent,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            Text(
                text = stringResource(R.string.giveaway_winners_title),
                color = colors.content,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            Text(
                text =
                    remember(
                        content,
                        locale,
                    ) { resources.winnersWonText(content.winnerCount, content.prize, locale) },
                color = colors.secondaryContent,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The winners as tiles, three to a row. */
@Composable
private fun WinnerTiles(
    ui: GiveawayUi,
    colors: BubbleColors,
    onAction: (GiveawayAction) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        maxItemsInEachRow = WINNERS_PER_ROW,
    ) {
        ui.winners.forEach { winner ->
            WinnerTile(
                winner = winner,
                colors = colors,
                modifier = Modifier.weight(1f),
                onClick = { onAction(GiveawayAction.OpenWinner(winner.id)) },
            )
        }
    }
}

/** A winner: avatar over name on a tile in the bubble's block color, which opens their profile. */
@Composable
private fun WinnerTile(
    winner: SenderAvatar,
    colors: BubbleColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val name = winner.name.resolve()
    val label = stringResource(R.string.giveaway_open_winner, name)
    Column(
        modifier =
            modifier
                .background(colors.block, MaterialTheme.shapes.large)
                .clickable(onClickLabel = label, onClick = onClick)
                .padding(MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Avatar(
            photo = winner.photo,
            name = name,
            id = winner.id,
            size = MaterialTheme.sizes.avatarRow,
            placeholderIcon = winner.placeholderIcon,
        )
        Text(
            text = name,
            color = colors.blockContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.userContent,
        )
    }
}

@Composable
private fun SecondaryNote(
    text: Int,
    colors: BubbleColors,
) {
    Text(text = stringResource(text), color = colors.secondaryContent, style = MaterialTheme.typography.bodyMedium)
}

private const val WINNERS_PER_ROW = 3

@Preview
@Composable
private fun MessageGiveawayWinnersPreview() {
    PingogramTheme(dynamicColor = false) {
        MessageGiveawayWinners(PaidPostSamples.giveawayWinners(), ui = PaidPostSamples.giveawayUi())
    }
}
