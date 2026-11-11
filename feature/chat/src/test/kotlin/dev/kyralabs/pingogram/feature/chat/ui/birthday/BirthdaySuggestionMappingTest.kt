package dev.kyralabs.pingogram.feature.chat.ui.birthday

import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatViewModelHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A birthday suggestion's message becomes its card's item, naming the other person both ways (board 16b). */
@OptIn(ExperimentalCoroutinesApi::class)
class BirthdaySuggestionMappingTest : ChatViewModelHarness() {
    private val suggested = Birthdate(day = 10, month = 12, year = null)

    @Test
    fun `a suggestion received names its sender, one sent its receiver`() =
        runViewModelTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(ME, firstName = "Me"))
            val suggestion = MessageContent.Service(ServiceAction.BirthdateSuggested(suggested))
            val viewModel =
                showing(
                    chat(),
                    history(
                        message(1, content = suggestion),
                        message(2, sender = MessageSender.User(ME), isOutgoing = true, content = suggestion),
                        message(3, content = MessageContent.Service(ServiceAction.MemberJoined)),
                    ),
                )
            runCurrent()

            val services = viewModel.items.filterIsInstance<ChatItem.Service>().sortedBy { it.id }
            assertEquals(
                ChatItem.ServiceBirthday(suggested, UiText.Plain("Ada"), outgoing = false),
                services[0].birthday,
            )
            assertEquals(
                ChatItem.ServiceBirthday(suggested, UiText.Plain("Ada"), outgoing = true),
                services[1].birthday,
            )
            assertNull(services[2].birthday)
        }
}
