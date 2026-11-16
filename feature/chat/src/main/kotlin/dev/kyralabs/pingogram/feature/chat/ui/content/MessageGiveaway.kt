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
        DrawDetails(content.parameters, colors)
        LearnMoreButton(colors, onClick = { onAction(GiveawayAction.LearnMore) })
    }
}

/** When the winners are picked, who takes part, from which countries, and what else the channel gives away. */
@Composable
private fun DrawDetails(
    parameters: GiveawayParameters,
    colors: BubbleColors,
) {
    val locale = LocalConfiguration.current.locales[0]
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
        val who = if (parameters.onlyNewMembers) R.plurals.giveaway_who_new else R.plurals.giveaway_who_all
        SecondaryLine(pluralStringResource(who, parameters.chatIds.size), colors)
        if (parameters.countryCodes.isNotEmpty()) {
            val countries = remember(parameters.countryCodes, locale) { countriesText(parameters.countryCodes, locale) }
            SecondaryLine(stringResource(R.string.giveaway_countries, countries), colors)
        }
        if (parameters.prizeDescription.isNotBlank()) {
            Text(
                text = stringResource(R.string.giveaway_also, parameters.prizeDescription),
                color = colors.content,
                style = MaterialTheme.typography.bodyMedium.userContent,
            )
        }
    }
}

@Composable
internal fun SecondaryLine(
    text: String,
    colors: BubbleColors,
) {
    Text(text = text, color = colors.secondaryContent, style = MaterialTheme.typography.bodySmall)
}

/** The prize on a gold card: the gift badge, "GIVEAWAY PRIZE", the count large beside the prize, and for how long. */
@Composable
private fun PrizeCard(
    prize: GiveawayPrize,
    winnerCount: Int,
) {
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
        PrizeBadge()
        PrizeTexts(prize, winnerCount)
    }
}

/** The gift symbol on a `Sunny` badge in `tertiary`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // MaterialShapes
@Composable
private fun PrizeBadge() {
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
}

/** "GIVEAWAY PRIZE", "3 ×" beside "Telegram Premium" (or the Telegram Stars), and "for 3 months". */
@Composable
private fun PrizeTexts(
    prize: GiveawayPrize,
    winnerCount: Int,
) {
    val locale = LocalConfiguration.current.locales[0]
    val onCard = MaterialTheme.colorScheme.onTertiaryContainer
    val amount =
        when (prize) {
            is GiveawayPrize.Premium -> stringResource(R.string.giveaway_prize_times, winnerCount)
            is GiveawayPrize.Stars -> remember(prize, locale) { giveawayNumber(prize.starCount, locale) }
        }
    val name =
        when (prize) {
            is GiveawayPrize.Premium -> R.string.giveaway_prize_premium
            is GiveawayPrize.Stars -> R.string.giveaway_prize_stars
        }
    val detail =
        when (prize) {
            is GiveawayPrize.Premium -> {
                pluralStringResource(R.plurals.giveaway_premium_months, prize.monthCount, prize.monthCount)
            }

            is GiveawayPrize.Stars -> {
                pluralStringResource(R.plurals.giveaway_stars_among, winnerCount, winnerCount)
            }
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
                text = amount,
                color = onCard,
                style = MaterialTheme.experimentalTypography.displaySmallCondensed.copy(fontFeatureSettings = "tnum"),
            )
            Text(
                text = stringResource(name),
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.extraSmall),
                color = onCard,
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
        }
        Text(text = detail, color = onCard, style = MaterialTheme.typography.bodyMedium)
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
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
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
private fun SectionLabel(
    text: Int,
    colors: BubbleColors,
) {
    Text(text = stringResource(text), color = colors.accent, style = MaterialTheme.typography.titleSmallEmphasized)
}

@Composable
internal fun LearnMoreButton(
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

@Preview
@Composable
private fun MessageGiveawayPreview() {
    PingogramTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
            MessageGiveaway(PaidPostSamples.giveaway(), ui = PaidPostSamples.giveawayUi())
        }
    }
}
