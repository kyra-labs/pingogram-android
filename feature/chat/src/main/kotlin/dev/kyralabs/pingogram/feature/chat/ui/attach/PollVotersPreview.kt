package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

/** "Who can vote" of a channel's poll, as previews and goldens show it: [voters], with [countries] picked. */
@Composable
internal fun PollVotersSample(
    voters: PollVoters,
    countries: List<String> = emptyList(),
) {
    val editor = remember(voters, countries) { PollEditor(PollDraft(voters = voters, countries = countries)) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn { pollVotersItems(PollSheet(isChannel = true), editor) }
    }
}

@PingogramPreviews
@Composable
private fun PollVotersCountriesPreview() {
    PingogramPreview { PollVotersSample(PollVoters.Countries, listOf("DE", "FR", "IN")) }
}

@PingogramPreviews
@Composable
private fun PollVotersNoCountryPreview() {
    PingogramPreview { PollVotersSample(PollVoters.Countries) }
}
