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

/** What a tap in a giveaway's bubble asks for; the bubble turns it into the event of its message. */
internal sealed interface GiveawayAction {
    /** "Learn more": the details sheet. */
    data object LearnMore : GiveawayAction

    /** A participating channel's chip. */
    data class OpenChat(
        val chatId: Long,
    ) : GiveawayAction

    /** A winner's tile. */
    data class OpenWinner(
        val userId: Long,
    ) : GiveawayAction
}

/**
 * A giveaway a channel runs (`REQUIREMENTS.md` FR-C4, the owner's board 3; `docs/DESIGN_SYSTEM.md` §6 "Giveaways"),
 * padded in the bubble, `mediaMaxWidth` wide: the prize card (`tertiaryContainer`: "GIVEAWAY PRIZE", "3 ×" in the
 * condensed display style beside "Telegram Premium", "for 3 months"), the participating channels as chips that open
 * them, when the winners are picked and who takes part, and "Learn more", which opens the details sheet. Nothing here
 * enters the giveaway: Telegram enters whoever subscribes to the channels (`REQUIREMENTS.md` §1.2).
 */
@Composable
internal fun MessageGiveaway(
    content: MessageContent.Giveaway,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
    ui: GiveawayUi = GiveawayUi(),
    onAction: (GiveawayAction) -> Unit = {},
) {
    val parameters = content.parameters
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        PrizeCard(content.prize, content.winnerCount)
        if (ui.channels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                SectionLabel(R.string.giveaway_channels, colors)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    ui.channels.forEach { channel ->
                        ChannelChip(channel, colors, onClick = { onAction(GiveawayAction.OpenChat(channel.id)) })
                    }
                }
            }
        }
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            SectionLabel(R.string.giveaway_winners_date, colors)
            Text(
                text =
                    remember(parameters.winnersSelectionDate, locale) {
                        giveawayDateText(parameters.winnersSelectionDate, locale)
                    },
                color = colors.content,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
            )
            val channels = parameters.chatIds.size
            val who = if (parameters.onlyNewMembers) R.plurals.giveaway_who_new else R.plurals.giveaway_who_all
            Text(
                text = pluralStringResource(who, channels),
                color = colors.secondaryContent,
                style = MaterialTheme.typography.bodySmall,
            )
            if (parameters.countryCodes.isNotEmpty()) {
                val countries =
                    remember(parameters.countryCodes, locale) { countriesText(parameters.countryCodes, locale) }
                Text(
                    text = stringResource(R.string.giveaway_countries, countries),
                    color = colors.secondaryContent,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (parameters.prizeDescription.isNotBlank()) {
                Text(
                    text = stringResource(R.string.giveaway_also, parameters.prizeDescription),
                    color = colors.content,
                    style = MaterialTheme.typography.bodyMedium.userContent,
                )
            }
        }
        LearnMoreButton(colors, onClick = { onAction(GiveawayAction.LearnMore) })
    }
}

/**
 * The winners of a giveaway (`docs/DESIGN_SYSTEM.md` §6 "Giveaways"), padded in the bubble: a trophy badge in the
 * bubble's accent, "Giveaway winners" over "3 people won Telegram Premium for 3 months", the winners as tiles (avatar
 * and name, three to a row) that open their profiles, what nobody claimed, and "Learn more".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes
@Composable
internal fun MessageGiveawayWinners(
    content: MessageContent.GiveawayWinners,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
    ui: GiveawayUi = GiveawayUi(),
    onAction: (GiveawayAction) -> Unit = {},
) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
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
        when {
            content.wasRefunded -> {
                SecondaryNote(R.string.giveaway_refunded, colors)
            }

            ui.winners.isEmpty() -> {
                SecondaryNote(R.string.giveaway_winners_private, colors)
            }

            else -> {
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
        }
        if (content.unclaimedPrizeCount > 0) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.giveaway_unclaimed,
                        content.unclaimedPrizeCount,
                        content.unclaimedPrizeCount,
                    ),
                color = colors.secondaryContent,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        LearnMoreButton(colors, onClick = { onAction(GiveawayAction.LearnMore) })
    }
}

/** The prize on a gold card: the gift badge, "GIVEAWAY PRIZE", the count large beside the prize, and for how long. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes
@Composable
private fun PrizeCard(
    prize: GiveawayPrize,
    winnerCount: Int,
) {
    val locale = LocalConfiguration.current.locales[0]
    val onCard = MaterialTheme.colorScheme.onTertiaryContainer
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium)
                .padding(MaterialTheme.spacing.medium)
                .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.headlineBadge)
                    .background(MaterialTheme.colorScheme.tertiary, MaterialShapes.Sunny.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_redeem),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.avatarExtraSmall),
                tint = MaterialTheme.colorScheme.onTertiary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            Text(
                text = stringResource(R.string.giveaway_prize_kicker).uppercase(locale),
                color = onCard,
                style = MaterialTheme.typography.labelMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text =
                        when (prize) {
                            is GiveawayPrize.Premium -> {
                                stringResource(R.string.giveaway_prize_times, winnerCount)
                            }

                            is GiveawayPrize.Stars -> {
                                remember(
                                    prize,
                                    locale,
                                ) { giveawayNumber(prize.starCount, locale) }
                            }
                        },
                    color = onCard,
                    style =
                        MaterialTheme.experimentalTypography.displaySmallCondensed.copy(
                            fontFeatureSettings = "tnum",
                        ),
                )
                Text(
                    text =
                        stringResource(
                            when (prize) {
                                is GiveawayPrize.Premium -> R.string.giveaway_prize_premium
                                is GiveawayPrize.Stars -> R.string.giveaway_prize_stars
                            },
                        ),
                    modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraSmall),
                    color = onCard,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
            }
            Text(
                text =
                    when (prize) {
                        is GiveawayPrize.Premium -> {
                            pluralStringResource(R.plurals.giveaway_premium_months, prize.monthCount, prize.monthCount)
                        }

                        is GiveawayPrize.Stars -> {
                            pluralStringResource(R.plurals.giveaway_stars_among, winnerCount, winnerCount)
                        }
                    },
                color = onCard,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A participating channel: its avatar and name on a chip that opens it. */
@Composable
private fun ChannelChip(
    channel: SenderAvatar,
    colors: BubbleColors,
    onClick: () -> Unit,
) {
    val name = channel.name.resolve()
    AssistChip(
        onClick = onClick,
        label = {
            Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyleUserContent())
        },
        leadingIcon = {
            Avatar(
                photo = channel.photo,
                name = name,
                id = channel.id,
                size = MaterialTheme.sizes.avatarBadge,
                placeholderIcon = channel.placeholderIcon,
                shape = AvatarShape.Channel,
            )
        },
        colors = AssistChipDefaults.assistChipColors(containerColor = colors.block, labelColor = colors.blockContent),
        border = null,
    )
}

@Composable
private fun LocalTextStyleUserContent() = MaterialTheme.typography.labelLarge.userContent

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
private fun SectionLabel(
    text: Int,
    colors: BubbleColors,
) {
    Text(text = stringResource(text), color = colors.accent, style = MaterialTheme.typography.titleSmallEmphasized)
}

@Composable
private fun SecondaryNote(
    text: Int,
    colors: BubbleColors,
) {
    Text(text = stringResource(text), color = colors.secondaryContent, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun LearnMoreButton(
    colors: BubbleColors,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.block,
                contentColor = colors.blockContent,
            ),
    ) {
        Text(stringResource(R.string.giveaway_learn_more))
    }
}

private const val WINNERS_PER_ROW = 3

@Preview
@Composable
private fun MessageGiveawayPreview() {
    PingogramTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            MessageGiveaway(PaidPostSamples.giveaway(), ui = PaidPostSamples.giveawayUi())
            MessageGiveawayWinners(PaidPostSamples.giveawayWinners(), ui = PaidPostSamples.giveawayUi())
        }
    }
}
