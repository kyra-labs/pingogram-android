package dev.kyralabs.pingogram.feature.chat.ui.giveaway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.model.GiveawayPrize
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.GiveawayEvent
import java.util.Locale

/**
 * The details of a giveaway ("Learn more", `docs/DESIGN_SYSTEM.md` §6 "Giveaways"), a [ModalBottomSheet] as the
 * conversation's other sheets: "About this giveaway" in `titleLargeEmphasized` over the prize, then what TDLib knows
 * for the user: while it runs, when the winners are picked and whether the user takes part (and why not); once over,
 * when they were picked, whether the user won (a Telegram Premium gift code is shown to activate in another Telegram
 * app, never applied here) and how many winners took their prize; a [DelayedLoadingIndicator] while it loads, or why
 * it failed with "Try again". A note says Pingogram enters and buys nothing. Dragging it down, the scrim, back and
 * "Close" close it ([GiveawayEvent.DismissInfo]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiveawayInfoBottomSheet(
    sheet: GiveawayInfoSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(GiveawayEvent.DismissInfo) }) {
        GiveawayInfoContent(sheet, currentOnEvent)
    }
}

@Composable
internal fun GiveawayInfoContent(
    sheet: GiveawayInfoSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = spacing.large, end = spacing.large, bottom = spacing.large)
                .testTag(GIVEAWAY_INFO_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.giveaway_info_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text =
                remember(sheet.prize, sheet.winnerCount, locale) {
                    resources.prizeLine(sheet.prize, sheet.winnerCount, locale)
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        InfoState(sheet.state)
        Text(
            text = stringResource(R.string.giveaway_info_note),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        SheetButtons(failed = sheet.state is GiveawayInfoState.Failed, onEvent = onEvent)
    }
}

/** A [DelayedLoadingIndicator] while it loads, what TDLib knows, or why it failed. */
@Composable
private fun InfoState(state: GiveawayInfoState) {
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    when (state) {
        GiveawayInfoState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large), contentAlignment = Alignment.Center) {
                DelayedLoadingIndicator()
            }
        }

        is GiveawayInfoState.Loaded -> {
            val lines = remember(state, locale) { resources.infoLines(state, locale) }
            lines.forEach { line -> Text(text = line, style = MaterialTheme.typography.bodyLarge.userContent) }
        }

        is GiveawayInfoState.Failed -> {
            Text(
                text = stringResource(R.string.giveaway_info_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = errorText(state.error),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** "Try again" after a failure, and "Close", at the end. */
@Composable
private fun SheetButtons(
    failed: Boolean,
    onEvent: (ChatEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.End),
    ) {
        if (failed) {
            TextButton(onClick = { onEvent(GiveawayEvent.RetryInfo) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.giveaway_info_retry))
            }
        }
        TextButton(onClick = { onEvent(GiveawayEvent.DismissInfo) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.giveaway_info_close), textAlign = TextAlign.Center)
        }
    }
}

/** "3 × Telegram Premium for 3 months", "500 Telegram Stars shared among 3 winners". */
private fun android.content.res.Resources.prizeLine(
    prize: GiveawayPrize,
    winnerCount: Int,
    locale: Locale,
): String =
    when (prize) {
        is GiveawayPrize.Premium -> {
            "${getString(R.string.giveaway_prize_times, winnerCount)} ${prizeText(prize, locale)}"
        }

        is GiveawayPrize.Stars -> {
            "${prizeText(prize, locale)} ${getQuantityString(R.plurals.giveaway_stars_among, winnerCount, winnerCount)}"
        }
    }

/** What TDLib knows, one sentence a line. */
private fun android.content.res.Resources.infoLines(
    state: GiveawayInfoState.Loaded,
    locale: Locale,
): List<String> =
    when (val info = state.info) {
        is GiveawayInfo.Ongoing -> ongoingLines(info, state.chatTitle, locale)
        is GiveawayInfo.Completed -> completedLines(info, locale)
    }

/** Whether the draw is over, and whether the user takes part or why not. */
private fun android.content.res.Resources.ongoingLines(
    info: GiveawayInfo.Ongoing,
    chatTitle: String?,
    locale: Locale,
): List<String> =
    listOfNotNull(
        getString(R.string.giveaway_info_ended).takeIf { info.isEnded },
        when (val status = info.status) {
            GiveawayParticipantStatus.Eligible -> {
                getString(R.string.giveaway_info_eligible)
            }

            GiveawayParticipantStatus.Participating -> {
                getString(R.string.giveaway_info_participating)
            }

            is GiveawayParticipantStatus.AlreadyWasMember -> {
                getString(R.string.giveaway_info_was_member, giveawayDateText(status.joinedChatDate, locale))
            }

            is GiveawayParticipantStatus.Administrator -> {
                getString(R.string.giveaway_info_admin, chatTitle ?: getString(R.string.giveaway_channel_unknown))
            }

            is GiveawayParticipantStatus.DisallowedCountry -> {
                getString(R.string.giveaway_info_country, countriesText(listOf(status.userCountryCode), locale))
            }
        },
    )

/** When the winners were picked, whether the user won, and how many took their prize. */
private fun android.content.res.Resources.completedLines(
    info: GiveawayInfo.Completed,
    locale: Locale,
): List<String> =
    listOfNotNull(
        getString(R.string.giveaway_info_completed_date, giveawayDateText(info.actualWinnersSelectionDate, locale)),
        when {
            info.wasRefunded -> {
                getString(R.string.giveaway_refunded)
            }

            info.isWinner && info.wonStarCount > 0 -> {
                getString(R.string.giveaway_info_won_stars, giveawayNumber(info.wonStarCount, locale))
            }

            info.isWinner -> {
                getString(R.string.giveaway_info_won_premium, info.giftCode)
            }

            else -> {
                getString(R.string.giveaway_info_lost)
            }
        },
        getQuantityString(R.plurals.giveaway_info_activated, info.winnerCount, info.activationCount, info.winnerCount)
            .takeIf { !info.wasRefunded && info.winnerCount > 0 },
    )

internal const val GIVEAWAY_INFO_TEST_TAG = "giveaway_info"
