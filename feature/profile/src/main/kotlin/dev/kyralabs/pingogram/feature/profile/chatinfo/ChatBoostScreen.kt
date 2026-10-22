package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatBoostStatus
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.ProfileGroup
import dev.kyralabs.pingogram.feature.profile.ui.ProfileRow
import dev.kyralabs.pingogram.feature.profile.ui.ProfileSectionHeader
import dev.kyralabs.pingogram.feature.profile.ui.profileRowColors
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor
import java.text.NumberFormat

/** The boosts page of a channel or supergroup: hoists its [ChatBoostViewModel]. [onBack] leaves it. */
@Composable
internal fun ChatBoostRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatBoostViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatBoostScreen(state, viewModel::onEvent, onBack, modifier)
}

/**
 * The boosts page (`docs/DESIGN_SYSTEM.md` §6 "Boosts"), read only, a settings-style page: "Level" as one segment with
 * the level and the boosts in `titleMedium`, a determinate `LinearWavyProgressIndicator` toward the next level and how
 * many boosts it still takes; then "Overview", segmented rows of the boosts, the members with Telegram Premium and
 * whether the user boosts the chat; then a note on what boosts are, which says that Pingogram cannot give them and
 * how many boosts the user has unused, never an offer or a link to buy anything (`REQUIREMENTS.md` §1.2).
 */
@Composable
internal fun ChatBoostScreen(
    state: ChatBoostUiState,
    onEvent: (ChatBoostEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoPageScaffold(title = stringResource(R.string.boost_title), onBack = onBack, modifier = modifier) { padding ->
        when (state) {
            ChatBoostUiState.Loading -> {
                PageLoading(padding)
            }

            is ChatBoostUiState.Error -> {
                InfoPageBody(padding) { PageError(state.error) { onEvent(ChatBoostEvent.Retry) } }
            }

            is ChatBoostUiState.Content -> {
                InfoPageContent(padding) {
                    LevelSegment(state.status)
                    OverviewGroup(state.status, state.isChannel)
                    BoostNote(state.status, state.isChannel)
                }
            }
        }
    }
}

/** "Level 2 · 14 boosts", the progress toward the next level, and how many boosts it still takes. */
@Composable
private fun LevelSegment(status: ChatBoostStatus) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val level = stringResource(R.string.boost_level, numbers.format(status.level))
    val boosts = pluralStringResource(R.plurals.boost_count, status.boostCount, numbers.format(status.boostCount))
    val next =
        if (status.isMaxLevel) {
            stringResource(R.string.boost_max_level)
        } else {
            pluralStringResource(
                R.plurals.boost_to_next_level,
                status.boostsToNextLevel,
                numbers.format(status.boostsToNextLevel),
                numbers.format(status.level + 1),
            )
        }
    val shapes = ListItemDefaults.segmentedShapes(0, 1)
    Column {
        ProfileSectionHeader(stringResource(R.string.boost_section_level))
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(profileRowContainerColor(), shapes.shape)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$level, $boosts. $next"
                        progressBarRangeInfo = ProgressBarRangeInfo(status.levelProgress, 0f..1f)
                    }.padding(MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
                Text(level, Modifier.weight(1f), style = MaterialTheme.typography.titleMediumEmphasized)
                Text(boosts, style = MaterialTheme.typography.titleMedium)
            }
            LinearWavyProgressIndicator(
                progress = { status.levelProgress },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            Text(
                text = next,
                modifier = Modifier.clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The boosts, the members with Telegram Premium ("30 · 1.5%"), and whether the user boosts the chat. */
@Composable
private fun OverviewGroup(
    status: ChatBoostStatus,
    isChannel: Boolean,
) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = NumberFormat.getIntegerInstance(locale)
    val percent = NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }
    val premium =
        stringResource(
            R.string.boost_premium_value,
            numbers.format(status.premiumMemberCount),
            percent.format(status.premiumMemberPercentage / PERCENT),
        )

    fun row(
        key: String,
        label: Int,
        value: String,
    ) = ProfileRow(key) { shapes ->
        SegmentedListItem(
            shapes = shapes,
            colors = profileRowColors(),
            supportingContent = { Text(stringResource(label)) },
        ) {
            Text(value)
        }
    }
    val rows =
        listOfNotNull(
            row("boosts", R.string.boost_row_boosts, numbers.format(status.boostCount)),
            row(
                "premium",
                if (isChannel) R.string.boost_row_premium_subscribers else R.string.boost_row_premium_members,
                premium,
            ),
            row(
                "you",
                R.string.boost_row_you,
                stringResource(if (isChannel) R.string.boost_you_boost_channel else R.string.boost_you_boost_group),
            ).takeIf { status.boostedByUser },
        )
    ProfileGroup(rows, title = stringResource(R.string.boost_section_overview))
}

/** What boosts are, that Pingogram shows them only, and the user's unused boosts, if any; said, never offered. */
@Composable
private fun BoostNote(
    status: ChatBoostStatus,
    isChannel: Boolean,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    Column(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = stringResource(if (isChannel) R.string.boost_note_channel else R.string.boost_note_group),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (status.unusedBoosts > 0) {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.boost_unused,
                        status.unusedBoosts,
                        numbers.format(status.unusedBoosts),
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** TDLib's percentage is 0–100; `NumberFormat`'s percent instance takes a fraction. */
private const val PERCENT = 100.0

/** Boosts of a channel halfway to its next level, which the user boosts, with one boost unused. */
internal val PreviewBoostStatus =
    ChatBoostStatus(
        level = 2,
        boostCount = 14,
        currentLevelBoostCount = 10,
        nextLevelBoostCount = 20,
        premiumMemberCount = 312,
        premiumMemberPercentage = 2.5,
        boostedByUser = true,
        unusedBoosts = 1,
    )

@PingogramPreviews
@Composable
private fun ChatBoostScreenPreview() {
    PingogramPreview { ChatBoostScreen(ChatBoostUiState.Content(PreviewBoostStatus, isChannel = true), {}, {}) }
}

@PingogramPreviews
@Composable
private fun ChatBoostScreenMaxLevelPreview() {
    PingogramPreview {
        ChatBoostScreen(
            ChatBoostUiState.Content(
                PreviewBoostStatus.copy(level = 10, boostCount = 120, nextLevelBoostCount = 0, boostedByUser = false),
                isChannel = false,
            ),
            {},
            {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ChatBoostScreenLoadingPreview() {
    PingogramPreview { ChatBoostScreen(ChatBoostUiState.Loading, {}, {}) }
}

@PingogramPreviews
@Composable
private fun ChatBoostScreenErrorPreview() {
    PingogramPreview { ChatBoostScreen(ChatBoostUiState.Error(DomainError.Network), {}, {}) }
}
