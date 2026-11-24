package dev.kyralabs.pingogram.feature.contacts.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.contacts.ui.birthdays.BirthdayItem
import dev.kyralabs.pingogram.feature.contacts.ui.birthdays.BirthdaysUiState
import kotlinx.collections.immutable.persistentListOf

/** The Birthdays page's states, named, for its previews. */
internal class BirthdaysPreviewStates : PreviewParameterProvider<BirthdaysUiState> {
    private fun birthday(
        userId: Long,
        name: String,
        date: String,
        turns: Int? = null,
        remind: Boolean = false,
    ) = BirthdayItem(userId, UiText.Plain(name), photo = null, date = date, turns = turns, remind = remind)

    private val states =
        listOf(
            "All groups" to
                BirthdaysUiState.Content(
                    today = persistentListOf(birthday(2, "Ada Lovelace", "November 24", turns = 36, remind = true)),
                    upcoming =
                        persistentListOf(
                            birthday(4, "Grace Hopper", "November 25"),
                            birthday(11, "Alan Turing", "November 26", turns = 41),
                        ),
                    past = persistentListOf(birthday(15, "Barbara Liskov", "November 23")),
                ),
            "Upcoming only" to
                BirthdaysUiState.Content(
                    today = persistentListOf(),
                    upcoming = persistentListOf(birthday(4, "Grace Hopper", "November 25", remind = true)),
                    past = persistentListOf(),
                ),
            "Loading" to BirthdaysUiState.Loading,
            "No birthdays" to BirthdaysUiState.Empty,
        )

    override val values: Sequence<BirthdaysUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
