package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.dates.DateTimeSheet
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslationRepositories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [ChatMessageFeatures] on the harness's fakes: its parts made over the shared [ChatPartContext]. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageFeaturesTest : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    private fun TestScope.features() =
        ChatMessageFeatures(
            parts =
                ChatPartContext(
                    chatId = PRIVATE_CHAT,
                    savedState = savedState,
                    scope = backgroundScope,
                    onEffect = { effects += it },
                    history = MutableSharedFlow<ChatHistory>(replay = 1),
                    messageOf = { null },
                ),
            repositories =
                MessageFeatureRepositories(
                    messages = messages,
                    translation =
                        TranslationRepositories(
                            translations,
                            languageDetector,
                            appLanguage,
                            hiddenTranslateBars,
                        ),
                    transcription = null,
                    polls = polls,
                    checklists = checklists,
                    giveaways = giveaways,
                ),
            users = users,
            chats = chats,
            chat = emptyFlow(),
            describeDate = { DateTimeSheet(unixTime = it, text = "At $it", zoneName = "UTC") },
        )

    @Test
    fun `has no transcripts without transcription on the device`() =
        runTest {
            assertNull(features().transcripts)
        }

    @Test
    fun `a tapped date's sheet is described as the view model describes it`() =
        runTest {
            val dates = features().dateTimes

            dates.onEvent(DateTimeEvent.Open(UNIX_TIME))

            assertEquals(DateTimeSheet(UNIX_TIME, "At $UNIX_TIME", "UTC"), dates.sheet.value)
        }

    @Test
    fun `the parts keep their state in the conversation's saved state`() =
        runTest {
            features().dateTimes.onEvent(DateTimeEvent.Open(UNIX_TIME))

            assertEquals(DateTimeSheet(UNIX_TIME, "At $UNIX_TIME", "UTC"), features().dateTimes.sheet.value)
        }

    @Test
    fun `the parts' effects go where the conversation's go`() =
        runTest {
            val dates = features().dateTimes
            dates.onEvent(DateTimeEvent.Open(UNIX_TIME))

            dates.onEvent(DateTimeEvent.Copy)

            assertEquals(listOf(ChatEffect.CopyText("At $UNIX_TIME")), effects)
        }

    private companion object {
        const val UNIX_TIME = 1_700_000_000L
    }
}
